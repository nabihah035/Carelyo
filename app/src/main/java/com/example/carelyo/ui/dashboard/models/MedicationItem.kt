package com.example.carelyo.ui.dashboard.models

data class MedicationItem(
    val name: String,
    val dosage: String,
    val time: String,
    val isCompleted: Boolean,
    val childId: Int
)