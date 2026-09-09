package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClinicPatient(
    @SerialName("clinic_patient_id")
    val ClinicPatientID: Int = 0,
    val clinicid: Int,
    val userid: Int? = null,
    val childid: Int? = null,
    val registered_at: String? = null
)
