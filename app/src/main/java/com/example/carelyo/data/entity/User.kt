// 📁 File: com/example/carelyo/data/entity/User.kt
package com.example.carelyo.data.entity

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class User @OptIn(ExperimentalSerializationApi::class) constructor(
    // 🔹 Default to 0, and tell Kotlin NEVER to send this key over JSON if it is 0
    @EncodeDefault(EncodeDefault.Mode.NEVER) val UserID: Int = 0,
    val firebase_fcm_token: String? = null,
    val email: String,
    val full_name: String?,
    val phone_number: String? = null,
    val role: String?,
    val created_at: String? = null,
    val updated_at: String? = null
)