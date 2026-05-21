package com.example.dairyflow2.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dairyflow2.data.model.Profile
import com.example.dairyflow2.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionDataStore by preferencesDataStore("session")

class SessionStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val roleKey = stringPreferencesKey("role")
    private val nameKey = stringPreferencesKey("name")
    private val emailKey = stringPreferencesKey("email")
    private val phoneKey = stringPreferencesKey("phone")
    private val localAccountsKey = stringPreferencesKey("local_accounts")

    val session: Flow<StoredSession?> = context.sessionDataStore.data.map { preferences ->
        val token = preferences[accessTokenKey].orEmpty()
        val userId = preferences[userIdKey].orEmpty()
        val role = preferences[roleKey].orEmpty()
        if (token.isBlank() || userId.isBlank() || role.isBlank()) {
            null
        } else {
            StoredSession(
                accessToken = token,
                refreshToken = preferences[refreshTokenKey].orEmpty(),
                profile = Profile(
                    id = userId,
                    role = UserRole.fromWire(role),
                    fullName = preferences[nameKey].orEmpty(),
                    email = preferences[emailKey].orEmpty(),
                    phone = preferences[phoneKey].orEmpty(),
                ),
            )
        }
    }

    suspend fun save(accessToken: String, refreshToken: String, profile: Profile) {
        context.sessionDataStore.edit { preferences ->
            preferences[accessTokenKey] = accessToken
            preferences[refreshTokenKey] = refreshToken
            preferences[userIdKey] = profile.id
            preferences[roleKey] = profile.role.wireName
            preferences[nameKey] = profile.fullName
            preferences[emailKey] = profile.email
            preferences[phoneKey] = profile.phone
        }
    }

    suspend fun saveLocalAccount(profile: Profile, password: String) {
        context.sessionDataStore.edit { preferences ->
            val accounts = preferences[localAccountsKey].decodeLocalAccounts()
            val account = LocalAccount(
                id = profile.id,
                role = profile.role.wireName,
                fullName = profile.fullName,
                email = profile.email.trim(),
                phone = profile.phone,
                password = password,
            )
            val updated = accounts
                .filterNot { it.email.equals(account.email, ignoreCase = true) }
                .plus(account)
            preferences[localAccountsKey] = json.encodeToString(updated)
        }
    }

    suspend fun findLocalAccount(email: String, password: String): Profile? {
        val accounts = context.sessionDataStore.data.first()[localAccountsKey].decodeLocalAccounts()
        return accounts.firstOrNull {
            it.email.equals(email.trim(), ignoreCase = true) && it.password == password
        }?.toProfile()
    }

    suspend fun clear() {
        context.sessionDataStore.edit { preferences ->
            preferences.remove(accessTokenKey)
            preferences.remove(refreshTokenKey)
            preferences.remove(userIdKey)
            preferences.remove(roleKey)
            preferences.remove(nameKey)
            preferences.remove(emailKey)
            preferences.remove(phoneKey)
        }
    }

    private fun String?.decodeLocalAccounts(): List<LocalAccount> =
        if (isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<LocalAccount>>(this) }.getOrDefault(emptyList())
        }
}

data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val profile: Profile,
)

@Serializable
private data class LocalAccount(
    val id: String,
    val role: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val password: String,
) {
    fun toProfile(): Profile = Profile(
        id = id,
        role = UserRole.fromWire(role),
        fullName = fullName,
        email = email,
        phone = phone,
    )
}
