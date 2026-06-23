package com.example.carelyo.service

import android.app.Application
import android.util.Log
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.Reminder
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

class ReminderService(private val application: Application) {

    private val dbDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    /**
     * Creates reminders for scheduled tasks like medications, appointments, etc.
     */
    suspend fun createReminder(
        childId: Int,
        parentId: Int,
        reminderType: String,
        scheduledAt: String
    ): Boolean {
        return try {
            val reminder = Reminder(
                ChildID = childId,
                ParentID = parentId,
                reminder_type = reminderType,
                scheduled_at = scheduledAt,
                is_sent = false,
                noti_status = "Unread" // Ensure new reminders start as Unread
            )

            val result = withContext(Dispatchers.IO) {
                SupabaseClient.client.postgrest["REMINDER"]
                    .insert(reminder) { select() }
                    .decodeList<Reminder>()
                    .firstOrNull()
            }
            result != null
        } catch (e: Exception) {
            Log.e("ReminderService", "Failed to create reminder", e)
            false
        }
    }

    /**
     * Updates reminder status to sent/read
     */
    suspend fun markReminderAsSent(remindId: Int): Boolean {
        return try {
            // Don't check if already sent - we want to update status anyway
            val reminder = withContext(Dispatchers.IO) {
                SupabaseClient.client.postgrest["REMINDER"]
                    .select {
                        filter { eq("remindid", remindId) }
                    }
                    .decodeList<Reminder>()
                    .firstOrNull()
            }
            reminder?.let {
                // Update both noti_status and is_sent
                val updatedReminder = it.copy(noti_status = "Read", is_sent = true)
                withContext(Dispatchers.IO) {
                    SupabaseClient.client.postgrest["REMINDER"]
                        .update(updatedReminder) {
                            filter { eq("remindid", remindId) }
                        }
                }
                Log.d("ReminderService", "Marked reminder $remindId as Read")
                true
            } ?: false
        } catch (e: Exception) {
            Log.e("ReminderService", "Failed to mark reminder as sent", e)
            false
        }
    }

    /**
     * Gets all unread reminders for a parent
     */
    suspend fun getUnreadReminders(parentId: Int): List<Reminder> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClient.client.postgrest["REMINDER"]
                    .select {
                        filter { eq("parentid", parentId) }
                        filter { eq("noti_status", "Unread") }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Reminder>()
            }
        } catch (e: Exception) {
            Log.e("ReminderService", "Failed to get unread reminders", e)
            emptyList()
        }
    }

    /**
     * Gets all reminders for a parent
     */
    suspend fun getReminders(parentId: Int): List<Reminder> {
        return try {
            SupabaseClient.client.postgrest["REMINDER"]
                .select {
                    filter {
                        eq("parentid", parentId)
                    }
                }
                .decodeList<Reminder>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Deletes a specific reminder by ID
     */
    suspend fun deleteReminder(remindId: Int): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val reminder = SupabaseClient.client.postgrest["REMINDER"]
                    .select { filter { eq("remindid", remindId) } }
                    .decodeList<Reminder>()
                    .firstOrNull()
                reminder?.let {
                    val updatedReminder = it.copy(noti_status = "Delete")
                    SupabaseClient.client.postgrest["REMINDER"]
                        .update(updatedReminder) {
                            filter { eq("remindid", remindId) }
                        }
                }
                true
            }
        } catch (e: Exception) {
            Log.e("ReminderService", "Failed to delete reminder", e)
            false
        }
    }

    /**
     * Marks all reminders as read for a parent
     */
    suspend fun markAllAsRead(parentId: Int): Boolean {
        return try {
            val reminders = getUnreadReminders(parentId)
            Log.d("ReminderService", "Marking ${reminders.size} reminders as read")

            // Update all unread reminders directly in the database
            for (reminder in reminders) {
                val success = markReminderAsSent(reminder.RemindID)
                if (!success) {
                    Log.e("ReminderService", "Failed to mark reminder ${reminder.RemindID} as read")
                }
            }
            true
        } catch (e: Exception) {
            Log.e("ReminderService", "Failed to mark all as read", e)
            false
        }
    }

    companion object {
        private var instance: ReminderService? = null

        fun getInstance(application: Application): ReminderService {
            return instance ?: ReminderService(application).also { instance = it }
        }
    }
}