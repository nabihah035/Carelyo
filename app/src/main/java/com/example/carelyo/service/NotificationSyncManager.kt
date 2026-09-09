package com.example.carelyo.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.carelyo.R
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.*
import com.example.carelyo.ui.reminder.ReminderActivity
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object NotificationSyncManager {

    private const val TAG = "NotificationSyncManager"

    fun ensureChannel(context: Context) {
        CarelyoFirebaseMessagingService.ensureNotificationChannel(context)
    }

    fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val prefs = context.getSharedPreferences("carelyo_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications disabled by user. Notification $notificationId suppressed.")
            return
        }

        ensureChannel(context)

        val intent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CarelyoFirebaseMessagingService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    suspend fun syncNotifications(context: Context, userId: Int): Int = withContext(Dispatchers.IO) {
        try {
            ensureChannel(context)

            // 1. Load Children for this Parent
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

            // 2. Load existing NOTIFICATION rows for this user
            val existingNotifications = try {
                SupabaseClient.client.postgrest["NOTIFICATION"]
                    .select { filter { eq("userid", userId) } }
                    .decodeList<Notification>()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load existing NOTIFICATION rows", e)
                emptyList()
            }

            val existingAppIds = existingNotifications.mapNotNull { it.appid }.toSet()
            val existingCvIds = existingNotifications.mapNotNull { it.childvaccineid }.toSet()
            val existingMedSchedIds = existingNotifications.mapNotNull { it.medscheduleid }.toSet()
            val existingReminderIds = existingNotifications.mapNotNull { it.reminderid }.toSet()

            val prefs = context.getSharedPreferences("carelyo_prefs", Context.MODE_PRIVATE)
            val alertedIds = prefs.getStringSet("alerted_notification_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

            // 3. Sync Appointments (status strictly "Scheduled", 1 hour before / today)
            try {
                val appointments = SupabaseClient.client.postgrest["APPOINTMENT"]
                    .select {
                        filter {
                            eq("status", AppointmentStatus.SCHEDULED.value)
                        }
                    }
                    .decodeList<Appointment>()
                    .filter { app ->
                        app.ParentID == userId || (childIds.isNotEmpty() && app.ChildID in childIds)
                    }

                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val todayStr = dateFormat.format(Date(now))

                for (app in appointments) {
                    if (app.AppID in existingAppIds) continue

                    var shouldAlert = false
                    val appDateStr = app.appointment_date
                    val appTimeStr = app.appointment_time

                    if (!appDateStr.isNullOrEmpty()) {
                        if (appDateStr.startsWith(todayStr)) {
                            shouldAlert = true
                        } else if (!appTimeStr.isNullOrEmpty()) {
                            val parsedTime = tryParseDateTime("$appDateStr $appTimeStr")
                            if (parsedTime != null) {
                                val diffMillis = parsedTime - now
                                if (diffMillis in -1800000..86400000) {
                                    shouldAlert = true
                                }
                            }
                        }
                    }

                    if (shouldAlert) {
                        val childName = childMap[app.ChildID]?.full_name ?: "your child"
                        val clinicName = app.clinic_name ?: "Clinic"
                        val timeDesc = if (!appTimeStr.isNullOrEmpty()) "at $appTimeStr" else ""
                        val dateDesc = if (!appDateStr.isNullOrEmpty()) "on $appDateStr" else "soon"

                        val notifInsert = NotificationInsert(
                            userid = userId,
                            childid = app.ChildID,
                            clinicid = app.clinicid,
                            appid = app.AppID,
                            title = "Appointment Reminder: $clinicName",
                            message = "Appointment scheduled $timeDesc $dateDesc for $childName.",
                            type = "appointment",
                            is_read = false
                        )

                        try {
                            SupabaseClient.client.postgrest["NOTIFICATION"].insert(notifInsert)
                            showNotification(
                                context,
                                10000 + app.AppID,
                                notifInsert.title ?: "Appointment Reminder",
                                notifInsert.message ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error inserting appointment notification", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing appointments", e)
            }

            // 4. Sync Child Vaccines (status Scheduled, Due, Overdue)
            if (childIds.isNotEmpty()) {
                try {
                    val childVaccines = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                        .select {
                            filter {
                                isIn("childid", childIds)
                                isIn(
                                    "status",
                                    listOf(
                                        VaccineStatusEnum.SCHEDULED.value,
                                        VaccineStatusEnum.DUE.value,
                                        VaccineStatusEnum.OVERDUE.value
                                    )
                                )
                            }
                        }
                        .decodeList<ChildVaccine>()

                    val allVaccines = try {
                        SupabaseClient.client.postgrest["VACCINATION"]
                            .select()
                            .decodeList<Vaccination>()
                            .associateBy { it.VaccineID }
                    } catch (_: Exception) {
                        emptyMap()
                    }

                    for (cv in childVaccines) {
                        val cvId = cv.ChildVaccineID ?: continue
                        if (cvId in existingCvIds) continue

                        val vaccineName = allVaccines[cv.VaccineID]?.vaccine_name ?: "Vaccine"
                        val childName = childMap[cv.ChildID]?.full_name ?: "your child"
                        val statusText = cv.status ?: "Due"

                        val notifInsert = NotificationInsert(
                            userid = userId,
                            childid = cv.ChildID,
                            childvaccineid = cvId,
                            title = "Vaccine Alert: $vaccineName",
                            message = "Vaccine for $childName is $statusText.",
                            type = "vaccine",
                            is_read = false
                        )

                        try {
                            SupabaseClient.client.postgrest["NOTIFICATION"].insert(notifInsert)
                            showNotification(
                                context,
                                20000 + cvId,
                                notifInsert.title ?: "Vaccine Alert",
                                notifInsert.message ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error inserting vaccine notification", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing child vaccines", e)
                }
            }

            // 5. Sync Medication Schedule (time-based: 1 time/day -> 08:00; 2 times/day -> 08:00, 20:00; 3 times/day -> 08:00, 14:00, 20:00)
            if (childIds.isNotEmpty()) {
                try {
                    val activeMeds = SupabaseClient.client.postgrest["MEDICATION"]
                        .select {
                            filter {
                                isIn("childid", childIds)
                                eq("is_active", true)
                            }
                        }
                        .decodeList<Medication>()

                    val medIds = activeMeds.map { it.MedID }
                    if (medIds.isNotEmpty()) {
                        val schedules = try {
                            SupabaseClient.client.postgrest["MEDICATION_SCHEDULE"]
                                .select {
                                    filter {
                                        isIn("medid", medIds)
                                    }
                                }
                                .decodeList<MedicationSchedule>()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load medication schedules", e)
                            emptyList()
                        }

                        val schedulesByMedId = schedules.groupBy { it.MedID }
                        val medMap = activeMeds.associateBy { it.MedID }
                        val nowCal = Calendar.getInstance()
                        val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val todayStr = dateFormat.format(Date())
                        val todayDate = dateFormat.parse(todayStr) ?: Date()

                        for (med in activeMeds) {
                            // Check end date
                            if (!med.end_date.isNullOrEmpty()) {
                                val endDate = try { dateFormat.parse(med.end_date) } catch (_: Exception) { null }
                                if (endDate != null && endDate.before(todayDate)) {
                                    continue
                                }
                            }

                            val childName = childMap[med.ChildID]?.full_name ?: "your child"
                            val medName = med.medication_name ?: "Medication"
                            val dosage = if (!med.dosage.isNullOrEmpty()) " (${med.dosage})" else ""
                            val medSchedules = schedulesByMedId[med.MedID] ?: emptyList()

                            // Get target hours from MEDICATION_SCHEDULE or fallback to frequency mapping
                            val targetHoursWithSchedule = if (medSchedules.isNotEmpty()) {
                                medSchedules.mapNotNull { sched ->
                                    val hour = com.example.carelyo.utils.MedicationSchedulerHelper.extractHourFromSchedule(sched.scheduled_time)
                                    if (hour != null) Pair(hour, sched) else null
                                }
                            } else {
                                com.example.carelyo.utils.MedicationSchedulerHelper.getScheduledHours(med.frequency)
                                    .map { Pair(it, null) }
                            }

                            for ((hour, sched) in targetHoursWithSchedule) {
                                val dailyDoseKey = "med_${med.MedID}_${hour}_${todayStr}"
                                val timeStr = com.example.carelyo.utils.MedicationSchedulerHelper.formatHourToString(hour)

                                // Trigger if scheduled hour has arrived today and hasn't been alerted today
                                if (currentHour >= hour && !alertedIds.contains(dailyDoseKey)) {
                                    val notifInsert = NotificationInsert(
                                        userid = userId,
                                        childid = med.ChildID,
                                        medscheduleid = sched?.MedScheduleID,
                                        title = "Medication Reminder: $medName",
                                        message = "Time to give $medName$dosage to $childName ($timeStr).",
                                        type = "medication",
                                        is_read = false
                                    )

                                    try {
                                        SupabaseClient.client.postgrest["NOTIFICATION"].insert(notifInsert)
                                        val notifId = 30000 + (med.MedID * 10) + (hour % 10)
                                        showNotification(
                                            context,
                                            notifId,
                                            notifInsert.title ?: "Medication Reminder",
                                            notifInsert.message ?: ""
                                        )
                                        alertedIds.add(dailyDoseKey)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error inserting medication notification", e)
                                    }
                                }
                            }
                        }

                        // Also ensure alarms are scheduled with AlarmManager
                        try {
                            MedicationReminderScheduler.scheduleMedicationAlarms(context, userId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error scheduling alarms", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing medications", e)
                }
            }

            // 6. Sync Reminders from REMINDER table
            try {
                val reminders = SupabaseClient.client.postgrest["REMINDER"]
                    .select {
                        filter {
                            eq("parentid", userId)
                        }
                    }
                    .decodeList<Reminder>()
                    .filter { it.noti_status == ReminderNotiStatus.UNREAD.value || it.is_sent != true }

                for (rem in reminders) {
                    if (rem.RemindID in existingReminderIds) continue

                    val childName = childMap[rem.ChildID]?.full_name ?: "your child"
                    val remType = rem.reminder_type ?: "reminder"
                    val scheduledAt = rem.scheduled_at ?: ""
                    val typeCap = remType.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }

                    val notifInsert = NotificationInsert(
                        userid = userId,
                        childid = rem.ChildID,
                        reminderid = rem.RemindID,
                        title = "$typeCap Reminder",
                        message = "Scheduled for $childName at $scheduledAt.",
                        type = remType,
                        is_read = false
                    )

                    try {
                        SupabaseClient.client.postgrest["NOTIFICATION"].insert(notifInsert)
                        SupabaseClient.client.postgrest["REMINDER"]
                            .update({ set("is_sent", true) }) {
                                filter { eq("remindid", rem.RemindID) }
                            }

                        showNotification(
                            context,
                            40000 + rem.RemindID,
                            notifInsert.title ?: "Reminder Alert",
                            notifInsert.message ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error inserting reminder notification", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing reminders", e)
            }

            // 7. Load all unread notifications from NOTIFICATION table
            val unreadNotifications = try {
                SupabaseClient.client.postgrest["NOTIFICATION"]
                    .select {
                        filter {
                            eq("userid", userId)
                            eq("is_read", false)
                        }
                    }
                    .decodeList<Notification>()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching unread notifications", e)
                emptyList()
            }

            // Fire notifications for any unread row not yet alerted on this device
            for (notif in unreadNotifications) {
                val notifKey = notif.NotificationID.toString()
                if (!alertedIds.contains(notifKey)) {
                    showNotification(
                        context,
                        notif.NotificationID,
                        notif.title ?: "Carelyo Notification",
                        notif.message ?: "You have an unread health alert."
                    )
                    alertedIds.add(notifKey)
                }
            }

            prefs.edit()
                .putStringSet("alerted_notification_ids", alertedIds)
                .putInt("unread_count", unreadNotifications.size)
                .apply()

            unreadNotifications.size
        } catch (e: Exception) {
            Log.e(TAG, "General error in syncNotifications", e)
            0
        }
    }

    private fun tryParseDateTime(dateTimeStr: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd hh:mma",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val date = sdf.parse(dateTimeStr)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
