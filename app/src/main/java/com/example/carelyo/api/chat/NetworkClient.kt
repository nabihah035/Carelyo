package com.example.carelyo.api.chat

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private const val BASE_URL = "http://10.157.25.131:11434/"

    // 1. Create a custom OkHttpClient with expanded timeout limits to allow LLM ample time to generate summaries
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS) // Time permitted to establish connection
            .readTimeout(300, TimeUnit.SECONDS)    // 5 minutes permitted for LLM to think and return summary
            .writeTimeout(120, TimeUnit.SECONDS)   // Time permitted to upload text prompt
            .callTimeout(360, TimeUnit.SECONDS)    // Overall call timeout
            .retryOnConnectionFailure(true)
            .build()
    }

    // 2. Attach the client instance directly to your Retrofit Builder instance
    val ollamaApi: OllamaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OllamaApiService::class.java)
    }
}