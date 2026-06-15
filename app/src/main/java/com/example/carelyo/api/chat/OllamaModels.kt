package com.example.carelyo.api.chat

data class ChatRequest(
    val model: String = "qwen2.5:3b",
    val messages: List<Message>,
    val stream: Boolean = false,
    val options: Map<String, Any> = mapOf("num_predict" to 250) // Increased to allow ~180 words since Qwen is faster!
)

data class Message(
    val role: String, // "system", "user", or "assistant"
    val content: String
)

data class ChatResponse(
    val message: Message
)