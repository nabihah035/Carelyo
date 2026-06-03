package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class MedicalHistory(
    val MedicalHisID: Int,
    val ChildID: Int,
    val condition_name: String? = null,
    val diagnosis_date: String? = null,
    val treatment: String? = null,
    val notes: String? = null,
    val created_at: String? = null
)