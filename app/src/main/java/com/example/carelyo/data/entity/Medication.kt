package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Medication(
    @SerialName("medid")
    val MedID: Int = 0,
    @SerialName("childid")
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