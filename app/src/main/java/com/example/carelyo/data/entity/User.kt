package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class User(
    @SerialName("userid")
    val UserID: Int = 0,
    val email: String,
    val password: String = "",
    val full_name: String?,
    val phone_number: String? = null,
    val role: String?,
    val created_at: String? = null,
    val updated_at: String? = null
)