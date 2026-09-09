package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Clinic(
    @SerialName("clinicid")
    val ClinicID: Int = 0,
    val clinic_name: String,
    val address: String? = null,
    val phone_number: String? = null,
    val email: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)
