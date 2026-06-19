package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MedicalHistory(
    @SerialName("medicalhisid")
    val MedicalHisID: Int = 0,
    @SerialName("childid")
    val ChildID: Int,
    val condition_name: String? = null,
    val diagnosis_date: String? = null,
    val treatment: String? = null,
    val notes: String? = null,
    val created_at: String? = null
)