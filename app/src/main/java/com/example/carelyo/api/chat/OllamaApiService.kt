package com.example.carelyo.api.chat

import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApiService {
    @POST("api/chat")
    suspend fun sendChatMessage(@Body request: ChatRequest): ChatResponse
}