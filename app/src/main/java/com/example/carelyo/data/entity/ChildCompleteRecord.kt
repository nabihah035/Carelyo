package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChildCompleteRecord(
    val child: Child,
    val allergies: List<Allergie> = emptyList(),
    val medicalHistory: List<MedicalHistory> = emptyList(),
    val doctorVisits: List<DoctorVisit> = emptyList(),
    val childVaccines: List<ChildVaccine> = emptyList()
)