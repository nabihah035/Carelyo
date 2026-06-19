package com.example.carelyo.ui.dashboard.models

data class UpcomingEvent(
    val type: Type,
    val title: String,
    val description: String,
    val date: String,
    val childId: Int
) {
    enum class Type {
        VACCINATION,
        MEDICATION,
        APPOINTMENT
    }
}