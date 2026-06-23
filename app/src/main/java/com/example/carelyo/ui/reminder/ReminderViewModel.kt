package com.example.carelyo.ui.reminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.carelyo.data.entity.Reminder
import com.example.carelyo.service.ReminderService
import com.example.carelyo.api.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

sealed class ReminderOperationResult {
    data class Success(val message: String) : ReminderOperationResult()
    data class Error(val message: String) : ReminderOperationResult()
    object Loading : ReminderOperationResult()
}

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val reminderService = ReminderService.getInstance(application)

    private val _reminders = MutableLiveData<List<Reminder>>(emptyList())
    val reminders: LiveData<List<Reminder>> = _reminders

    private val _unreadCount = MutableLiveData<Int>(0)
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _operationResult = MutableLiveData<ReminderOperationResult>()
    val operationResult: LiveData<ReminderOperationResult> = _operationResult

    private var allReminders: List<Reminder> = emptyList()
    private var currentParentId: Int = -1

    fun loadReminders(parentId: Int) {
        currentParentId = parentId
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get all reminders, not just unread ones
                val result = reminderService.getReminders(parentId)
                allReminders = result
                _reminders.postValue(result)
                updateUnreadCount(result) // This will count unread ones
                _operationResult.postValue(ReminderOperationResult.Success("Reminders loaded"))
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error(e.message ?: "Failed to load reminders"))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun markAsRead(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update both is_sent and noti_status in the database
                SupabaseClient.client.postgrest["REMINDER"]
                    .update(
                        {
                            set("is_sent", true)
                            set("noti_status", "Read")
                        }
                    ) {
                        filter { eq("remindid", reminder.RemindID) }
                    }

                // Update local list
                val updatedReminder = reminder.copy(noti_status = "Read", is_sent = true)
                allReminders = allReminders.map {
                    if (it.RemindID == reminder.RemindID) updatedReminder else it
                }
                _reminders.postValue(allReminders)
                updateUnreadCount(allReminders)
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
                // Update all unread reminders for this parent
                SupabaseClient.client.postgrest["REMINDER"]
                    .update(
                        {
                            set("is_sent", true)
                            set("noti_status", "Read")
                        }
                    ) {
                        filter {
                            eq("parentid", currentParentId)
                            eq("noti_status", "Unread")
                        }
                    }

                // Update all reminders to Read status in local list
                allReminders = allReminders.map {
                    if (it.noti_status == "Unread") {
                        it.copy(noti_status = "Read", is_sent = true)
                    } else {
                        it
                    }
                }
                _reminders.postValue(allReminders)
                updateUnreadCount(allReminders)
                _operationResult.postValue(ReminderOperationResult.Success("All reminders marked as read"))
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error("Error: ${e.message}"))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = reminderService.deleteReminder(reminder.RemindID)
            if (success) {
                allReminders = allReminders.filter { it.RemindID != reminder.RemindID }
                _reminders.postValue(allReminders)
                updateUnreadCount(allReminders)
                _operationResult.postValue(ReminderOperationResult.Success("Reminder deleted"))
            } else {
                _operationResult.postValue(ReminderOperationResult.Error("Failed to delete reminder from server"))
            }
        }
    }

    private fun updateUnreadCount(reminders: List<Reminder>) {
        val count = reminders.count { it.noti_status == "Unread" }
        _unreadCount.postValue(count)
        // Also update the notification badge via SharedPreferences
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
                loadMedicationsAndChildren(currentParentId)
            } catch (e: Exception) {
                _operationResult.postValue(ReminderOperationResult.Error("Failed to toggle medication"))
            }
        }
    }

    fun deleteMedication(medication: com.example.carelyo.data.entity.Medication) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
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