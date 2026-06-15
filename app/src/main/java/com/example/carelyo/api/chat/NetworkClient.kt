package com.example.carelyo.api.chat

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private const val BASE_URL = "http://10.192.158.131:11434/"

    // 1. Create a custom OkHttpClient with expanded timeout limits
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // Time permitted to establish connection
            .readTimeout(120, TimeUnit.SECONDS)   // Time permitted for Meditron to think and reply
            .writeTimeout(60, TimeUnit.SECONDS)  // Time permitted to upload your text prompt
            .build()
    }

    // 2. Attach the client instance directly to your Retrofit Builder instance
    val ollamaApi: OllamaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // <--- CRITICAL: Injecting the new timeout rules here
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OllamaApiService::class.java)
    }
}