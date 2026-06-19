package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Appointment(
    @SerialName("appid")
    val AppID: Int= 0,
    @SerialName("parentid")
    val ParentID: Int,
    @SerialName("childid")
    val ChildID: Int,
    val appointment_date: String? = null,
    val appointment_time: String? = null,
    val clinic_name: String? = null,
    val doctor_name: String? = null,
    val purpose: String? = null,
    val notes: String? = null,
    val status: String? = null,
    val created_at: String? = null
)