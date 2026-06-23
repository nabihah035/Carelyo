package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The MEDICATION_SCHEDULE.scheduled_time column is TIMESTAMPTZ in the database.
// Keep scheduled_time as String? (Supabase returns it as text) but update
// the parser in HomeFragment/DashboardViewModel to handle the ISO-8601 format.

@Serializable
data class MedicationSchedule(
    @SerialName("medscheduleid")
    val MedScheduleID: Int = 0,
    @SerialName("medid")
    val MedID: Int,
    val scheduled_time: String? = null,   // ISO-8601 TIMESTAMPTZ e.g. "2026-06-05T08:00:00+08:00"
    val created_at: String? = null
)

@Serializable
data class MedicationScheduleInsert(
    @SerialName("medid")
    val MedID: Int,
    val scheduled_time: String? = null
)