package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    @SerialName("notificationid")
    val NotificationID: Int = 0,
    val userid: Int? = null,
    val childid: Int? = null,
    val title: String? = null,
    val message: String? = null,
    val type: String? = null,
    val is_read: Boolean? = false,
    val created_at: String? = null,
    val clinicid: Int? = null,
    val appid: Int? = null,
    val childvaccineid: Int? = null,
    val medscheduleid: Int? = null,
    val reminderid: Int? = null
)

@Serializable
data class NotificationInsert(
    val userid: Int? = null,
    val childid: Int? = null,
    val title: String? = null,
    val message: String? = null,
    val type: String? = null,
    val is_read: Boolean = false,
    val clinicid: Int? = null,
    val appid: Int? = null,
    val childvaccineid: Int? = null,
    val medscheduleid: Int? = null,
    val reminderid: Int? = null
)

