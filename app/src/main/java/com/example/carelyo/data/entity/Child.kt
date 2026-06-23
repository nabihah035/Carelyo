package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Child(
    @SerialName("childid")
    val ChildID: Int = 0,
    @SerialName("parent_id")
    val Parent_ID: Int,
    @SerialName("full_name")
    val full_name: String? = null,
    @SerialName("date_of_birth")
    val date_of_birth: String? = null,
    @SerialName("gender")
    val gender: String? = null,
    @SerialName("blood_type")
    val blood_type: String? = null,
    @SerialName("weight")
    val weight: String? = null,
    @SerialName("height")
    val height: String? = null,
    @SerialName("created_at")
    val created_at: String? = null,
    @SerialName("updated_at")
    val updated_at: String? = null,
    @SerialName("status")
    val status: String? = null
)

@Serializable
data class ChildInsert(
    @SerialName("parent_id")
    val Parent_ID: Int,
    @SerialName("full_name")
    val full_name: String? = null,
    @SerialName("date_of_birth")
    val date_of_birth: String? = null,
    @SerialName("gender")
    val gender: String? = null,
    @SerialName("blood_type")
    val blood_type: String? = null,
    @SerialName("weight")
    val weight: String? = null,
    @SerialName("height")
    val height: String? = null,
    @SerialName("status")
    val status: String? = null
)