package com.example.carelyo.data.entity

data class UpcomingMedication(
    val medicationName: String,
    val dosage: String?,
    val scheduledTime: String?,
    val childId: Int
)