package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class Allergie(
    val AllergieID: Int,
    val ChildID: Int,
    val allergy_type: String? = null,
    val allergy_name: String? = null,
    val severity: String? = null,
    val notes: String? = null,
    val created_at: String? = null
)