package com.example.carelyo.data.session

import android.content.Context
import android.content.SharedPreferences
import com.example.carelyo.data.entity.User
import kotlinx.serialization.json.Json

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CarelyoPrefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_USER_DATA = "userData"
    }

    fun saveUserSession(user: User) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_DATA, json.encodeToString(user))
            apply()
        }
    }

    fun getUserSession(): User? {
        val userJson = prefs.getString(KEY_USER_DATA, null) ?: return null
        return try {
            json.decodeFromString<User>(userJson)
        } catch (_: Exception) {
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}