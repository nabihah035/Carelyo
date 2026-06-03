package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class Vaccination(
    val VaccineID: Int,
    val vaccine_name: String? = null,
    val recommended_age_weeks: Int? = null,
    val description: String? = null
)