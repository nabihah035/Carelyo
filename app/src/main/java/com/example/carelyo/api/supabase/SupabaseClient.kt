package com.example.carelyo.api.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android

object SupabaseClient {

    val client = createSupabaseClient(
        supabaseUrl = "https://hrwppmgrlitutjbqzekt.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhyd3BwbWdybGl0dXRqYnF6ZWt0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc5NzM4NjYsImV4cCI6MjA5MzU0OTg2Nn0.jYZ2VvhOYyHpCh6p_zaofe6XlRXoQxEh9MwcecOUB74"
    ) {
        // 🔹 Explicitly tell Ktor to use the Android engine pipeline
        httpEngine = Android.create()
        install(Postgrest)
        install(Storage)
    }
}