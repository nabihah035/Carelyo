package com.example.carelyo.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    @SerialName("messageid")
    val MessageID: Int = 0,
    val sender_type: String,
    val content: String,
    val token_count: Int? = null,
    val created_at: String? = null,
    val sessionid: Int? = null
)

@Serializable
data class ChatMessageInsert(
    val sender_type: String,
    val content: String,
    val sessionid: Int?,
    val token_count: Int? = null
)
