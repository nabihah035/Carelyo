package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Child(
    @SerialName("childid")
    val ChildID: Int = 0, // Default to 0 for incoming new records
    @SerialName("parent_id")
    val Parent_ID: Int,
    val full_name: String? = null,
    val date_of_birth: String? = null,
    val gender: String? = null,
    val blood_type: String? = null,
    @SerialName("weight")
    val weight: Double? = null,
    @SerialName("height")
    val height: Double? = null,
    val profile_photo_url: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)