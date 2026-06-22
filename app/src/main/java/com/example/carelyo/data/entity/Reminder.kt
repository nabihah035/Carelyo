package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Reminder(
    @SerialName("remindid")
    val RemindID: Int = 0,
    @SerialName("childid")
    val ChildID: Int,
    @SerialName("parentid")
    val ParentID: Int,
    val reminder_type: String? = null,
    val noti_status: String? = "Unread",
    val scheduled_at: String? = null,
    val is_sent: Boolean = false,
    val created_at: String? = null
)