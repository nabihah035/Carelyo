package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class Medication(
    val MedID: Int,
    val ChildID: Int,
    val medication_name: String? = null,
    val dosage: String? = null,
    val frequency: String? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val notes: String? = null,
    val is_active: Boolean = true,
    val created_at: String? = null
)