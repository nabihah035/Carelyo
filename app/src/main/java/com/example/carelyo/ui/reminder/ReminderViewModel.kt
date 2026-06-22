package com.example.carelyo.ui.reminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.carelyo.data.entity.Reminder
import com.example.carelyo.service.ReminderService
import kotlinx.coroutines.launch

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
        viewModelScope.launch {
            try {
                // Routing call properly through Service layer
                val result = reminderService.getReminders(parentId)
                allReminders = result
                _reminders.value = result
                updateUnreadCount(result)
                _operationResult.value = ReminderOperationResult.Success("Reminders loaded")
            } catch (e: Exception) {
                _operationResult.value = ReminderOperationResult.Error(e.message ?: "Failed to load reminders")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAsRead(reminder: Reminder) {
        if (reminder.is_sent) return

        viewModelScope.launch {
            val success = reminderService.markReminderAsSent(reminder.RemindID)
            if (success) {
                val updatedReminder = reminder.copy(noti_status = "Read", is_sent = true)
                allReminders = allReminders.map {
                    if (it.RemindID == reminder.RemindID) updatedReminder else it
                }
                _reminders.value = allReminders
                updateUnreadCount(allReminders)
                _operationResult.value = ReminderOperationResult.Success("Marked as read")
            } else {
                _operationResult.value = ReminderOperationResult.Error("Failed to update status on server")
            }
        }
    }

    fun markAllAsRead() {
        if (currentParentId == -1) return
        viewModelScope.launch {
            val success = reminderService.markAllAsRead(currentParentId)
            if (success) {
                allReminders = allReminders.map { it.copy(noti_status = "Read", is_sent = true) }
                _reminders.value = allReminders
                updateUnreadCount(allReminders)
                _operationResult.value = ReminderOperationResult.Success("All marked as read")
            } else {
                _operationResult.value = ReminderOperationResult.Error("Failed to update all items")
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            val success = reminderService.deleteReminder(reminder.RemindID)
            if (success) {
                allReminders = allReminders.filter { it.RemindID != reminder.RemindID }
                _reminders.value = allReminders
                updateUnreadCount(allReminders)
                _operationResult.value = ReminderOperationResult.Success("Reminder deleted")
            } else {
                _operationResult.value = ReminderOperationResult.Error("Failed to delete reminder from server")
            }
        }
    }

    private fun updateUnreadCount(reminders: List<Reminder>) {
        _unreadCount.value = reminders.count { it.noti_status == "Unread" }
    }
}