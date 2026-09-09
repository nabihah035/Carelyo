package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatSession(
    @SerialName("sessionid")
    val SessionID: Int = 0,
    val user_id: Int,
    val title: String? = null,
    val created_at: String? = null,
    val update_at: String? = null
)

@Serializable
data class ChatSessionInsert(
    val user_id: Int,
    val title: String? = null,
    val created_at: String? = null,
    val update_at: String? = null
)
