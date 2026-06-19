package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Allergie(
    @SerialName("allergieid")
    val AllergieID: Int = 0,
    @SerialName("childid")
    val ChildID: Int,
    val allergy_type: String? = null,
    val allergy_name: String? = null,
    val severity: String? = null,
    val notes: String? = null,
    val created_at: String? = null
)