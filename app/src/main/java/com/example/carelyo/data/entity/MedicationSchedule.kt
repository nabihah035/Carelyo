package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class MedicationSchedule(
    val MedScheduleID: Int,
    val MedID: Int,
    val scheduled_time: String? = null,
    val created_at: String? = null
)