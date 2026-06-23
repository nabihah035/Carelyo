package com.example.carelyo.ui.dashboard.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

class MedicationPersistenceHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("medication_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun setMedicationCompletedToday(
        medicationId: Int,
        childId: Int,
        scheduledTime: String?,
        isCompleted: Boolean
    ) {
        val today = dateFormat.format(Date())
        // Create a unique key using all three identifiers
        val baseKey = "med_${childId}_${medicationId}_${scheduledTime ?: "notime"}"
        val fullKey = "${baseKey}_${today}"  // Fix: Properly concatenate the key
        prefs.edit().putBoolean(fullKey, isCompleted).apply()
    }

    fun isMedicationCompletedToday(
        medicationId: Int,
        childId: Int,
        scheduledTime: String?
    ): Boolean {
        val today = dateFormat.format(Date())
        val baseKey = "med_${childId}_${medicationId}_${scheduledTime ?: "notime"}"
        val fullKey = "${baseKey}_${today}"  // Fix: Properly concatenate the key
        return prefs.getBoolean(fullKey, false)
    }

    fun clearOldEntries() {
        val today = dateFormat.format(Date())
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("med_") && !key.contains(today)) {
                editor.remove(key)
            }
        }
        editor.apply()
    }
}