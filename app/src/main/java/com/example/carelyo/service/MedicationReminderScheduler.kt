package com.example.carelyo.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.ChildStatus
import com.example.carelyo.data.entity.Medication
import com.example.carelyo.data.entity.MedicationSchedule
import com.example.carelyo.utils.MedicationSchedulerHelper
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object MedicationReminderScheduler {

    private const val TAG = "MedReminderScheduler"

    suspend fun scheduleMedicationAlarms(context: Context, userId: Int) = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch children
            val children = try {
                SupabaseClient.client.postgrest["CHILD"]
                    .select { filter { eq("parent_id", userId) } }
                    .decodeList<Child>()
            } catch (_: Exception) {
                try {
                    SupabaseClient.client.postgrest["CHILD"]
                        .select { filter { eq("Parent_ID", userId) } }
                        .decodeList<Child>()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load children", e)
                    emptyList()
                }
            }

            val activeChildren = children.filter { it.status != ChildStatus.INACTIVE.value }
            val childMap = activeChildren.associateBy { it.ChildID }
            val childIds = activeChildren.map { it.ChildID }

            if (childIds.isEmpty()) return@withContext

            // 2. Fetch active medications
            val activeMeds = try {
                SupabaseClient.client.postgrest["MEDICATION"]
                    .select {
                        filter {
                            isIn("childid", childIds)
                            eq("is_active", true)
                        }
                    }
                    .decodeList<Medication>()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load medications", e)
                emptyList()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())
            val todayDate = dateFormat.parse(todayStr) ?: Date()

            // 3. For each active medication, determine scheduled hours and set alarms
            for (med in activeMeds) {
                // Check date bounds if provided
                if (!med.end_date.isNullOrEmpty()) {
                    val endDate = try { dateFormat.parse(med.end_date) } catch (_: Exception) { null }
                    if (endDate != null && endDate.before(todayDate)) {
                        continue // Medication course completed
                    }
                }

                val childName = childMap[med.ChildID]?.full_name ?: "your child"

                // Determine scheduled hours from frequency:
                // 1 time/day -> [8] (08:00)
                // 2 times/day -> [8, 20] (08:00, 20:00)
                // 3 times/day -> [8, 14, 20] (08:00, 14:00, 20:00)
                val scheduledHours = MedicationSchedulerHelper.getScheduledHours(med.frequency)

                for (hour in scheduledHours) {
                    scheduleSingleMedicationAlarm(
                        context = context,
                        userId = userId,
                        childId = med.ChildID,
                        childName = childName,
                        medId = med.MedID,
                        medName = med.medication_name ?: "Medication",
                        dosage = med.dosage ?: "",
                        scheduledHour = hour
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in scheduleMedicationAlarms", e)
        }
    }

    fun scheduleSingleMedicationAlarm(
        context: Context,
        userId: Int,
        childId: Int,
        childName: String,
        medId: Int,
        medName: String,
        dosage: String,
        scheduledHour: Int
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val now = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, scheduledHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If scheduled time for today has already passed, schedule for tomorrow
            if (targetTime.timeInMillis <= now.timeInMillis) {
                targetTime.add(Calendar.DAY_OF_YEAR, 1)
            }

            val scheduledTimeStr = MedicationSchedulerHelper.formatHourToString(scheduledHour)

            val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                action = MedicationAlarmReceiver.ACTION_MEDICATION_ALARM
                putExtra(MedicationAlarmReceiver.EXTRA_MED_ID, medId)
                putExtra(MedicationAlarmReceiver.EXTRA_MED_NAME, medName)
                putExtra(MedicationAlarmReceiver.EXTRA_DOSAGE, dosage)
                putExtra(MedicationAlarmReceiver.EXTRA_CHILD_NAME, childName)
                putExtra(MedicationAlarmReceiver.EXTRA_CHILD_ID, childId)
                putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULED_HOUR, scheduledHour)
                putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULED_TIME, scheduledTimeStr)
                putExtra(MedicationAlarmReceiver.EXTRA_USER_ID, userId)
            }

            val requestCode = (medId * 100) + scheduledHour
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetTime.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetTime.timeInMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTime.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    targetTime.timeInMillis,
                    pendingIntent
                )
            }

            Log.d(TAG, "Scheduled alarm for $medName at hour $scheduledHour ($scheduledTimeStr) on ${targetTime.time}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for $medName at hour $scheduledHour", e)
        }
    }

    fun cancelMedicationAlarms(context: Context, medId: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            // Common hours: 8, 14, 20
            val possibleHours = listOf(8, 14, 20)
            for (hour in possibleHours) {
                val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                    action = MedicationAlarmReceiver.ACTION_MEDICATION_ALARM
                }
                val requestCode = (medId * 100) + hour
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.d(TAG, "Cancelled alarm for medId: $medId at hour: $hour")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarms for medId: $medId", e)
        }
    }
}
