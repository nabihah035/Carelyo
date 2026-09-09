package com.example.carelyo.ui.reminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.carelyo.data.entity.Notification
import com.example.carelyo.data.entity.Reminder
import com.example.carelyo.service.NotificationSyncManager
import com.example.carelyo.service.ReminderService
import com.example.carelyo.api.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

sealed class ReminderOperationResult {
    data class Success(val message: String) : ReminderOperationResult()
    data class Error(val message: String) : ReminderOperationResult()
    object Loading : ReminderOperationResult()
}

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val reminderService = ReminderService.getInstance(application)

    private val _notifications = MutableLiveData<List<Notification>>(emptyList())
    val notifications: LiveData<List<Notification>> = _notifications

    private val _reminders = MutableLiveData<List<Reminder>>(emptyList())
    val reminders: LiveData<List<Reminder>> = _reminders

    private val _unreadCount = MutableLiveData<Int>(0)
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _operationResult = MutableLiveData<ReminderOperationResult>()
    val operationResult: LiveData<ReminderOperationResult> = _operationResult

    private var allNotifications: List<Notification> = emptyList()
    private var allReminders: List<Reminder> = emptyList()
    private var currentParentId: Int = -1

    fun loadReminders(parentId: Int) {
        currentParentId = parentId
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Sync any upcoming appointments, vaccines, medications, and reminders into NOTIFICATION table
                NotificationSyncManager.syncNotifications(getApplication(), parentId)

                // Load all notifications for this user from Supabase NOTIFICATION table
                val notifs = SupabaseClient.client.postgrest["NOTIFICATION"]
                    .select {
                        filter { eq("userid", parentId) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Notification>()

                allNotifications = notifs
                _notifications.postValue(notifs)
                updateNotificationUnreadCount(notifs)

                _operationResult.postValue(ReminderOperationResult.Success("Notifications loaded"))
            } catch (e: Exception) {
                // Fallback to legacy REMINDER table if NOTIFICATION fails
                try {
                    val result = reminderService.getReminders(parentId)
                    allReminders = result
                    _reminders.postValue(result)
                    val count = result.count { it.noti_status == "Unread" }
                    _unreadCount.postValue(count)
                } catch (_: Exception) {
                }
                _operationResult.postValue(ReminderOperationResult.Error(e.message ?: "Failed to load notifications"))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun markAsRead(notification: Notification) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update is_read in NOTIFICATION table
                SupabaseClient.client.postgrest["NOTIFICATION"]
                    .update({ set("is_read", true) }) {
                        filter { eq("notificationid", notification.NotificationID) }
                    }

                // If linked to a REMINDER row, also update REMINDER
                if (notification.reminderid != null) {
                    try {
                        SupabaseClient.client.postgrest["REMINDER"]
                            .update({
                                set("is_sent", true)
                                set("noti_status", com.example.carelyo.data.entity.ReminderNotiStatus.READ.value)
                            }) {
                                filter { eq("remindid", notification.reminderid) }
                            }
                    } catch (_: Exception) {
                    }
                }

                // Update local list
                val updatedNotif = notification.copy(is_read = true)
                allNotifications = allNotifications.map {
                    if (it.NotificationID == notification.NotificationID) updatedNotif else it
                }
                _notifications.postValue(allNotifications)
                updateNotificationUnreadCount(allNotifications)
                _operationResult.postValue(ReminderOperationResult.Success("Marked as read"))
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error("Failed to update status: ${e.message}"))
            }
        }
    }

    fun markAllAsRead() {
        if (currentParentId == -1) {
            _operationResult.value = ReminderOperationResult.Error("No parent selected")
            return
        }

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update all unread in NOTIFICATION table
                SupabaseClient.client.postgrest["NOTIFICATION"]
                    .update({ set("is_read", true) }) {
                        filter {
                            eq("userid", currentParentId)
                            eq("is_read", false)
                        }
                    }

                // Also update legacy REMINDER table if present
                try {
                    SupabaseClient.client.postgrest["REMINDER"]
                        .update({
                            set("is_sent", true)
                            set("noti_status", com.example.carelyo.data.entity.ReminderNotiStatus.READ.value)
                        }) {
                            filter {
                                eq("parentid", currentParentId)
                                eq("noti_status", com.example.carelyo.data.entity.ReminderNotiStatus.UNREAD.value)
                            }
                        }
                } catch (_: Exception) {
                }

                // Update local list
                allNotifications = allNotifications.map { it.copy(is_read = true) }
                _notifications.postValue(allNotifications)
                updateNotificationUnreadCount(allNotifications)
                _operationResult.postValue(ReminderOperationResult.Success("All notifications marked as read"))
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error("Error: ${e.message}"))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun deleteNotification(notification: Notification) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.postgrest["NOTIFICATION"]
                    .delete {
                        filter { eq("notificationid", notification.NotificationID) }
                    }

                allNotifications = allNotifications.filter { it.NotificationID != notification.NotificationID }
                _notifications.postValue(allNotifications)
                updateNotificationUnreadCount(allNotifications)
                _operationResult.postValue(ReminderOperationResult.Success("Notification removed"))
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error("Failed to delete notification: ${e.message}"))
            }
        }
    }

    private fun updateNotificationUnreadCount(notifications: List<Notification>) {
        val count = notifications.count { it.is_read != true }
        _unreadCount.postValue(count)
        getApplication<Application>().getSharedPreferences("carelyo_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("unread_count", count)
            .apply()
    }


    // --- Medication Logic ---

    private val _medications = MutableLiveData<List<com.example.carelyo.data.entity.Medication>>(emptyList())
    val medications: LiveData<List<com.example.carelyo.data.entity.Medication>> = _medications

    private val _children = MutableLiveData<List<com.example.carelyo.data.entity.Child>>(emptyList())
    val children: LiveData<List<com.example.carelyo.data.entity.Child>> = _children

    fun loadMedicationsAndChildren(parentId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch children
                val childrenList = com.example.carelyo.api.supabase.SupabaseClient.client
                    .postgrest["CHILD"]
                    .select {
                        filter { eq("parent_id", parentId) }
                    }.decodeList<com.example.carelyo.data.entity.Child>()

                _children.postValue(childrenList)

                if (childrenList.isNotEmpty()) {
                    val childIds = childrenList.map { it.ChildID }
                    // Fetch medications
                    val meds = com.example.carelyo.api.supabase.SupabaseClient.client
                        .postgrest["MEDICATION"]
                        .select {
                            filter { isIn("childid", childIds) }
                        }.decodeList<com.example.carelyo.data.entity.Medication>()

                    _medications.postValue(meds)
                } else {
                    _medications.postValue(emptyList())
                }
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error("Failed to load medications: ${e.message}"))
            }
        }
    }

    fun addMedication(medInsert: com.example.carelyo.data.entity.MedicationInsert, schedules: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Insert Medication
                val insertedMed = com.example.carelyo.api.supabase.SupabaseClient.client
                    .postgrest["MEDICATION"]
                    .insert(medInsert) { select() }
                    .decodeSingle<com.example.carelyo.data.entity.Medication>()

                // Insert Schedules
                val scheduleInserts = schedules.map { time ->
                    com.example.carelyo.data.entity.MedicationScheduleInsert(
                        MedID = insertedMed.MedID,
                        scheduled_time = time
                    )
                }

                if (scheduleInserts.isNotEmpty()) {
                    com.example.carelyo.api.supabase.SupabaseClient.client
                        .postgrest["MEDICATION_SCHEDULE"]
                        .insert(scheduleInserts)
                }

                // Schedule alarms immediately
                try {
                    com.example.carelyo.service.MedicationReminderScheduler.scheduleMedicationAlarms(getApplication(), currentParentId)
                } catch (_: Exception) {}

                _operationResult.postValue(ReminderOperationResult.Success("Medication added successfully"))
                loadMedicationsAndChildren(currentParentId) // reload
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error("Failed to add medication: ${e.message}"))
            }
        }
    }

    fun toggleMedicationActive(medication: com.example.carelyo.data.entity.Medication, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.carelyo.api.supabase.SupabaseClient.client
                    .postgrest["MEDICATION"]
                    .update({ set("is_active", isActive) }) {
                        filter { eq("medid", medication.MedID) }
                    }

                if (!isActive) {
                    com.example.carelyo.service.MedicationReminderScheduler.cancelMedicationAlarms(getApplication(), medication.MedID)
                } else {
                    com.example.carelyo.service.MedicationReminderScheduler.scheduleMedicationAlarms(getApplication(), currentParentId)
                }

                loadMedicationsAndChildren(currentParentId)
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error("Failed to toggle medication"))
            }
        }
    }

    fun deleteMedication(medication: com.example.carelyo.data.entity.Medication) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Cancel scheduled alarms first
                com.example.carelyo.service.MedicationReminderScheduler.cancelMedicationAlarms(getApplication(), medication.MedID)

                // Delete schedules first
                com.example.carelyo.api.supabase.SupabaseClient.client
                    .postgrest["MEDICATION_SCHEDULE"]
                    .delete { filter { eq("medid", medication.MedID) } }

                // Then delete medication
                com.example.carelyo.api.supabase.SupabaseClient.client
                    .postgrest["MEDICATION"]
                    .delete { filter { eq("medid", medication.MedID) } }

                _operationResult.postValue(ReminderOperationResult.Success("Medication deleted"))
                loadMedicationsAndChildren(currentParentId)
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error("Failed to delete medication"))
            }
        }
    }
}