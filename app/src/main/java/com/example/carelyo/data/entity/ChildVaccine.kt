package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChildVaccine(
    @SerialName("childvaccineid")
    val ChildVaccineID: Int? = null,
    @SerialName("childid")
    val ChildID: Int? = null,
    @SerialName("vaccineid")
    val VaccineID: Int? = null,
    val status: String? = null,
    @SerialName("administered_date")
    val administered_date: String? = null,
    @SerialName("administered_at")
    val administered_at: String? = null,
    val notes: String? = null,
    @SerialName("created_at")
    val created_at: String? = null
)