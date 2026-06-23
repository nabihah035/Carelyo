package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DoctorVisit(
    @SerialName("docvisitid")
    val DocVisitID: Int = 0,
    @SerialName("childid")
    val ChildID: Int,
    val visit_date: String? = null,
    val clinic_name: String? = null,
    val doctor_name: String? = null,
    val raw_notes: String? = null,
    val ai_summary: String? = null,
    val summary_language: String? = null,
    val created_at: String? = null
)

@Serializable
data class DoctorVisitInsert(
    @SerialName("childid") val ChildID: Int,
    val visit_date: String,
    val clinic_name: String,
    val doctor_name: String,
    val raw_notes: String,
    val ai_summary: String,
    val summary_language: String
)