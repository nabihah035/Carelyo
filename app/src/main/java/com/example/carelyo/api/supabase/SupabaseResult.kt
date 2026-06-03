package com.example.carelyo.api.supabase

sealed class SupabaseResult<out T> {
    data class Success<T>(val data: T) : SupabaseResult<T>()
    data class Error(val message: String) : SupabaseResult<Nothing>()
}