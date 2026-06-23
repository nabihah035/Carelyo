package com.example.carelyo.ui.dashboard.models

data class MedicationItem(
    val name: String,
    val dosage: String,
    val time: String,
    val isCompleted: Boolean,
    val childId: Int,
    val medicationId: Int? = null,  // Add unique identifier
    val scheduledTime: String? = null // Add for date-based tracking
)