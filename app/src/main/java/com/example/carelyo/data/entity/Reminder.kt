package com.example.carelyo.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class Reminder(
    val RemindID: Int,
    val ChildID: Int,
    val ParentID: Int,
    val reminder_type: String? = null,
    val reference_id: Int? = null,
    val scheduled_at: String? = null,
    val is_sent: Boolean = false,
    val created_at: String? = null
)