package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class User(
    // Remove `@EncodeDefault` so the integer key is never stripped out of SharedPreferences JSON
    val UserID: Int = 0,
    val firebase_fcm_token: String? = null,
    val email: String,
    val password: String = "",
    val full_name: String?,
    val phone_number: String? = null,
    val role: String?,
    val created_at: String? = null,
    val updated_at: String? = null
)