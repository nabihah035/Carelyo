package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class RegisterUserRequest(
    val email: String,
    val password: String,
    val full_name: String,
    val phone_number: String,
    val role: String
)