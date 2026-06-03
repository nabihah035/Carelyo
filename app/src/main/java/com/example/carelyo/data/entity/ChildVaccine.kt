package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChildVaccine(
    val ChildVaccineID: Int,
    val ChildID: Int,
    val VaccineID: Int,
    val status: String? = null,
    val administered_date: String? = null,
    val administered_at: String? = null,
    val notes: String? = null,
    val created_at: String? = null
)