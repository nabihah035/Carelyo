package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class Child(
    val ChildID: Int,
    val Parent_ID: Int,
    val full_name: String? = null,
    val date_of_birth: String? = null,
    val gender: String? = null,
    val blood_type: String? = null,
    val profile_photo_url: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)