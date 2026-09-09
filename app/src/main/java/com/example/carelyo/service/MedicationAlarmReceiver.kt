package com.example.carelyo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.NotificationInsert
import com.example.carelyo.data.session.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "MedicationAlarmReceiver received action: $action")

        if (Intent.ACTION_BOOT_COMPLETED == action || "android.intent.action.QUICKBOOT_POWERON" == action) {
            val sessionManager = SessionManager(context)
            val user = sessionManager.getUserSession()
            if (user != null && user.UserID > 0) {
                CoroutineScope(Dispatchers.IO).launch {
                    MedicationReminderScheduler.scheduleMedicationAlarms(context, user.UserID)
                }
            }
            return
        }

        val medId = intent.getIntExtra(EXTRA_MED_ID, 0)
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "Medication"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val childName = intent.getStringExtra(EXTRA_CHILD_NAME) ?: "your child"
        val childId = intent.getIntExtra(EXTRA_CHILD_ID, 0)
        val scheduledHour = intent.getIntExtra(EXTRA_SCHEDULED_HOUR, 8)
        val scheduledTimeStr = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: String.format(Locale.getDefault(), "%02d:00", scheduledHour)
        val userId = intent.getIntExtra(EXTRA_USER_ID, 0)

        val dosageText = if (dosage.isNotEmpty()) " ($dosage)" else ""
        val title = "Medication Reminder: $medName"
        val message = "Time to give $medName$dosageText to $childName ($scheduledTimeStr)."
        val notificationId = 30000 + (medId * 10) + (scheduledHour % 10)

        // Show local system notification
        NotificationSyncManager.showNotification(context, notificationId, title, message)
        Log.d(TAG, "Fired medication alarm notification: $title - $message (ID: $notificationId)")

        // Record in Supabase NOTIFICATION table
        if (userId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val notifInsert = NotificationInsert(
                        userid = userId,
                        childid = if (childId > 0) childId else null,
                        title = title,
                        message = message,
                        type = "medication",
                        is_read = false
                    )
                    SupabaseClient.client.postgrest["NOTIFICATION"].insert(notifInsert)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert notification into database", e)
                }
            }
        }

        // Schedule next day's alarm for this dose
        MedicationReminderScheduler.scheduleSingleMedicationAlarm(
            context = context,
            userId = userId,
            childId = childId,
            childName = childName,
            medId = medId,
            medName = medName,
            dosage = dosage,
            scheduledHour = scheduledHour
        )
    }

    companion object {
        private const val TAG = "MedAlarmReceiver"
        const val ACTION_MEDICATION_ALARM = "com.example.carelyo.ACTION_MEDICATION_ALARM"

        const val EXTRA_MED_ID = "extra_med_id"
        const val EXTRA_MED_NAME = "extra_med_name"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_CHILD_NAME = "extra_child_name"
        const val EXTRA_CHILD_ID = "extra_child_id"
        const val EXTRA_SCHEDULED_HOUR = "extra_scheduled_hour"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_USER_ID = "extra_user_id"
    }
}
