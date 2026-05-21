package com.example.dairyflow2.data.remote

import com.example.dairyflow2.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseConfig {
    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
    val isConfigured: Boolean = url.startsWith("https://") && anonKey.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!isConfigured) {
            null
        } else {
            createSupabaseClient(
                supabaseUrl = url,
                supabaseKey = anonKey,
            ) {
                install(Auth)
                install(Postgrest)
                install(Realtime)
            }
        }
    }
}

