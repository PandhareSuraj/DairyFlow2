package com.example.dairyflow2.data.remote

import com.example.dairyflow2.data.model.AuthSession
import com.example.dairyflow2.data.model.Profile
import com.example.dairyflow2.data.model.SupabaseProfileDto
import com.example.dairyflow2.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant

class SupabaseRestClient(
    private val config: SupabaseConfig = SupabaseConfig,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun signIn(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext demoSession(email)
        }

        val body = """{"email":"${email.escapeJson()}","password":"${password.escapeJson()}"}"""
        val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            accessToken = null,
            body = body,
            preferRepresentation = false,
        ).jsonObject

        val accessToken = response["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val refreshToken = response["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val user = response["user"]?.jsonObject
        val userId = user?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
        val userEmail = user?.get("email")?.jsonPrimitive?.contentOrNull ?: email
        val profile = fetchProfile(userId, accessToken).copy(email = userEmail)

        AuthSession(accessToken, refreshToken, profile)
    }

    suspend fun fetchProfile(userId: String, accessToken: String): Profile {
        if (!config.isConfigured || accessToken.isBlank()) return demoSession("admin@dairyflow.local").profile
        val encoded = URLEncoder.encode(userId, StandardCharsets.UTF_8.name())
        val result = request(
            method = "GET",
            path = "/rest/v1/profiles?id=eq.$encoded&select=*",
            accessToken = accessToken,
        )
        val profile = result.jsonArray.firstOrNull()?.jsonObject
            ?: error("No profile found for authenticated user.")
        return profile.toProfileDto().toProfile()
    }

    suspend fun updateDeliveryStatus(accessToken: String, deliveryId: String, status: String, skippedReason: String?) {
        if (!config.isConfigured || accessToken.isBlank()) return
        val completedAt = if (status == "done") ",\"completed_at\":\"${Instant.now()}\"" else ",\"completed_at\":null"
        val reason = skippedReason?.let { ",\"skipped_reason\":\"${it.escapeJson()}\"" } ?: ",\"skipped_reason\":null"
        val body = """{"status":"$status"$completedAt$reason}"""
        request(
            method = "PATCH",
            path = "/rest/v1/deliveries?id=eq.${deliveryId.urlEncode()}",
            accessToken = accessToken,
            body = body,
        )
    }

    suspend fun markInvoicePaid(accessToken: String, invoiceId: String, customerId: String, amountPaise: Long) {
        if (!config.isConfigured || accessToken.isBlank()) return
        val payment = """
            {
              "invoice_id":"${invoiceId.escapeJson()}",
              "customer_id":"${customerId.escapeJson()}",
              "amount_paise":$amountPaise
            }
        """.trimIndent()
        request("POST", "/rest/v1/payments", accessToken, payment)
        request("PATCH", "/rest/v1/invoices?id=eq.${invoiceId.urlEncode()}", accessToken, """{"status":"paid"}""")
    }

    suspend fun createUser(accessToken: String, email: String, password: String, role: UserRole, fullName: String, phone: String) {
        if (!config.isConfigured || accessToken.isBlank()) return
        val body = """
            {
              "email":"${email.escapeJson()}",
              "password":"${password.escapeJson()}",
              "role":"${role.wireName}",
              "full_name":"${fullName.escapeJson()}",
              "phone":"${phone.escapeJson()}"
            }
        """.trimIndent()
        request("POST", "/functions/v1/create-user", accessToken, body, preferRepresentation = false)
    }

    private fun demoSession(email: String): AuthSession {
        val isStaff = email.contains("staff", ignoreCase = true) || email.contains("delivery", ignoreCase = true)
        val role = if (isStaff) UserRole.DeliveryBoy else UserRole.Admin
        val profile = Profile(
            id = if (isStaff) "staff-1" else "admin-1",
            role = role,
            fullName = if (isStaff) "Ravi Delivery" else "Dairy Owner",
            email = email.ifBlank { if (isStaff) "staff@dairyflow.local" else "admin@dairyflow.local" },
            phone = if (isStaff) "+91 98765 43210" else "+91 90000 00000",
            rating = if (isStaff) 4.8 else 0.0,
            totalDeliveries = if (isStaff) 214 else 0,
            onTimeRate = if (isStaff) 96.0 else 0.0,
            streak = if (isStaff) 18 else 0,
        )
        return AuthSession("demo-access-token", "demo-refresh-token", profile)
    }

    private fun request(
        method: String,
        path: String,
        accessToken: String?,
        body: String? = null,
        preferRepresentation: Boolean = true,
    ): kotlinx.serialization.json.JsonElement {
        val connection = (URL("${config.url}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("apikey", config.anonKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (preferRepresentation) setRequestProperty("Prefer", "return=representation")
            accessToken?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
            if (body != null) {
                doOutput = true
                OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            }
        }

        val code = connection.responseCode
        val text = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("Supabase request failed ($code): ${error.ifBlank { connection.responseMessage }}")
        }

        return if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
    }

    private fun JsonObject.toProfileDto(): SupabaseProfileDto = SupabaseProfileDto(
        id = this["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        role = this["role"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        fullName = this["full_name"]?.jsonPrimitive?.contentOrNull,
        email = this["email"]?.jsonPrimitive?.contentOrNull,
        phone = this["phone"]?.jsonPrimitive?.contentOrNull,
        rating = this["rating"]?.jsonPrimitive?.doubleOrNull,
        totalDeliveries = this["total_deliveries"]?.jsonPrimitive?.intOrNull,
        onTimeRate = this["on_time_rate"]?.jsonPrimitive?.doubleOrNull,
        streak = this["streak"]?.jsonPrimitive?.intOrNull,
        ratePerDeliveryPaise = this["rate_per_delivery_paise"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        isActive = this["is_active"]?.jsonPrimitive?.booleanOrNull,
    )

    private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun JsonArray.firstOrNull() = if (isEmpty()) null else this[0]
}
