package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClinicStaff(
    @SerialName("staff_clinic_id")
    val StaffClinicID: Int = 0,
    val clinicid: Int,
    val userid: Int? = null,
    val role_at_clinic: String? = null,
    val created_at: String? = null
)
