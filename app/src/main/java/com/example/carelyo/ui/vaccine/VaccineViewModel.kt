package com.example.carelyo.ui.vaccine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.carelyo.agent.infra.CarelyoAgent
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.agent.infra.CarelyoMessageBroker
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.ChildVaccine
import com.example.carelyo.data.entity.ChildVaccineInsert
import com.example.carelyo.data.entity.ChildVaccineUpdate
import com.example.carelyo.data.entity.Clinic
import com.example.carelyo.data.entity.Reminder
import com.example.carelyo.data.entity.Vaccination
import com.example.carelyo.data.entity.VaccineStatusEnum
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.session.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import android.widget.Toast

class VaccineViewModel(application: Application) : AndroidViewModel(application), CarelyoAgent {

    override val agentName: String = "VaccineViewModelAgent"

    private val _vaccineState = MutableLiveData<VaccineState>()
    val vaccineState: LiveData<VaccineState> = _vaccineState

    private val _children = MutableLiveData<List<Child>>()
    val children: LiveData<List<Child>> = _children

    private val _availableVaccines = MutableLiveData<List<Vaccination>>()
    val availableVaccines: LiveData<List<Vaccination>> = _availableVaccines

    private val _clinics = MutableLiveData<List<Clinic>>()
    val clinics: LiveData<List<Clinic>> = _clinics

    private val _isFormReady = MutableLiveData<Boolean>()
    val isFormReady: LiveData<Boolean> = _isFormReady

    private var allVaccinations: List<Vaccination> = emptyList()
    private var allChildVaccines: List<ChildVaccine> = emptyList()
    private var allChildren: List<Child> = emptyList()

    private var filterChild: Child? = null
    private var isLoadingData = false
    private var parentId: Int = 0

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dbDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dbDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    init {
        CarelyoMessageBroker.registerAgent(this)
        requestFormData()
    }

    fun requestFormData() {
        CarelyoMessageBroker.passMessage(
            CarelyoMessage(
                sender = agentName,
                receiver = "VaccinationMonitoringAgent",
                messageType = "REQUEST_INITIAL_VACCINE_FORM_DATA",
                content = emptyMap()
            )
        )
    }

    fun requestVaccinationData(parentId: Int, child: Child? = null) {
        if (isLoadingData) return
        isLoadingData = true
        this.parentId = parentId
        filterChild = child
        _vaccineState.postValue(VaccineState.Loading)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (allChildren.isEmpty() && child == null) {
                    val childrenResult = try {
                        SupabaseClient.client.postgrest["CHILD"]
                            .select { filter { eq("parent_id", parentId) } }.decodeList<Child>()
                    } catch (e: Exception) {
                        SupabaseClient.client.postgrest["CHILD"]
                            .select { filter { eq("Parent_ID", parentId) } }.decodeList<Child>()
                    }

                    allChildren = childrenResult
                    _children.postValue(childrenResult)

                    CarelyoMessageBroker.passMessage(
                        CarelyoMessage(
                            sender = agentName,
                            receiver = "BROADCAST",
                            messageType = "INFORM_CHILD_PROFILES_READY",
                            content = mapOf("children" to childrenResult)
                        )
                    )
                }

                if (allVaccinations.isEmpty()) {
                    allVaccinations = SupabaseClient.client.postgrest["VACCINATION"]
                        .select()
                        .decodeList<Vaccination>()
                    _availableVaccines.postValue(allVaccinations)
                }

                if (allChildVaccines.isEmpty()) {
                    allChildVaccines = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                        .select()
                        .decodeList<ChildVaccine>()
                }

                if (_clinics.value.isNullOrEmpty()) {
                    try {
                        val clinicList = SupabaseClient.client.postgrest["CLINIC"]
                            .select()
                            .decodeList<Clinic>()
                        _clinics.postValue(clinicList)
                    } catch (ce: Exception) {
                        ce.printStackTrace()
                    }
                }

                isLoadingData = false
                recalculate()
            } catch (e: Exception) {
                _vaccineState.postValue(VaccineState.Error(e.message ?: "Failed to load data"))
                isLoadingData = false
            }
        }
    }

    fun getTakenVaccineIdsForChild(childId: Int): List<Int>? {
        return allChildVaccines
            .filter { it.ChildID == childId }
            .mapNotNull { it.VaccineID }
            .distinct()
    }

    fun addChildVaccine(childVaccine: ChildVaccine) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val insertPayload = ChildVaccineInsert(
                    ChildID = childVaccine.ChildID ?: 0,
                    VaccineID = childVaccine.VaccineID ?: 0,
                    status = childVaccine.status,
                    administered_date = childVaccine.administered_date,
                    administered_at = childVaccine.administered_at,
                    notes = childVaccine.notes
                )
                val result = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                    .insert(insertPayload) { select() }
                    .decodeList<ChildVaccine>()
                    .firstOrNull()

                if (result != null) {
                    allChildVaccines = allChildVaccines + result
                    _availableVaccines.postValue(allVaccinations)
                    recalculate()

                    // Create reminder for this vaccine
                    createVaccineReminder(result)

                    Toast.makeText(getApplication(), "Vaccine added successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _vaccineState.postValue(VaccineState.Error(e.message ?: "Failed to add vaccine"))
            }
        }
    }

    private fun createVaccineReminder(childVaccine: ChildVaccine) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get child info
                val child = allChildren.find { it.ChildID == childVaccine.ChildID }
                val vaccine = allVaccinations.find { it.VaccineID == childVaccine.VaccineID }

                if (child == null || vaccine == null) return@launch

                // Check if reminder already exists for this vaccine
                val existingReminders = SupabaseClient.client.postgrest["REMINDER"]
                    .select {
                        filter { eq("childid", childVaccine.ChildID ?: 0) }
                        filter { eq("reminder_type", "vaccine") }
                    }
                    .decodeList<Reminder>()

                // Create reminder only if it doesn't exist
                if (existingReminders.isEmpty()) {
                    val scheduledAt = if (childVaccine.administered_at != null) {
                        childVaccine.administered_at
                    } else {
                        // If not administered yet, schedule for future
                        LocalDateTime.now().plusDays(7).format(dbDateTimeFormatter)
                    }

                    val reminder = Reminder(
                        ChildID = childVaccine.ChildID ?: 0,
                        ParentID = parentId,
                        reminder_type = "vaccine",
                        scheduled_at = scheduledAt,
                        is_sent = (childVaccine.status == com.example.carelyo.data.entity.VaccineStatusEnum.COMPLETED.value)
                    )

                    SupabaseClient.client.postgrest["REMINDER"]
                        .insert(reminder)
                }
            } catch (e: Exception) {
                // Log error but don't fail the vaccine addition
                e.printStackTrace()
            }
        }
    }

    fun markVaccineAsTaken(
        childId: Int,
        vaccineId: Int,
        administeredDate: String? = null,
        administeredAt: String? = null,
        clinicName: String? = null,
        notes: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingRecord = allChildVaccines.find {
                    it.ChildID == childId && it.VaccineID == vaccineId
                }

                val date = administeredDate ?: LocalDate.now().format(dbDateFormatter)
                val at = administeredAt ?: LocalDateTime.now().atZone(ZoneId.systemDefault()).format(dbDateTimeFormatter)

                val clinicLine = if (!clinicName.isNullOrBlank()) "Clinic: $clinicName" else null
                val userNotesLine = if (!notes.isNullOrBlank()) notes.trim() else null
                val formattedNewNotes = listOfNotNull(clinicLine, userNotesLine).joinToString("\n")

                if (existingRecord != null) {
                    val fullNotes = if (!existingRecord.notes.isNullOrBlank() && formattedNewNotes.isNotBlank()) {
                        "${existingRecord.notes}\n$formattedNewNotes"
                    } else if (formattedNewNotes.isNotBlank()) {
                        formattedNewNotes
                    } else {
                        existingRecord.notes ?: "Completed"
                    }

                    val updatePayload = ChildVaccineUpdate(
                        status = VaccineStatusEnum.COMPLETED.value,
                        administered_date = date,
                        administered_at = at,
                        notes = fullNotes
                    )

                    SupabaseClient.client.postgrest["CHILD_VACCINE"]
                        .update(updatePayload) {
                            filter {
                                eq("childvaccineid", existingRecord.ChildVaccineID ?: 0)
                            }
                        }

                    val updatedRecord = existingRecord.copy(
                        status = VaccineStatusEnum.COMPLETED.value,
                        administered_date = date,
                        administered_at = at,
                        notes = fullNotes
                    )

                    allChildVaccines = allChildVaccines.map {
                        if (it.ChildVaccineID == existingRecord.ChildVaccineID) updatedRecord else it
                    }
                } else {
                    val insertPayload = ChildVaccineInsert(
                        ChildID = childId,
                        VaccineID = vaccineId,
                        status = VaccineStatusEnum.COMPLETED.value,
                        administered_date = date,
                        administered_at = at,
                        notes = if (formattedNewNotes.isNotBlank()) formattedNewNotes else "Completed"
                    )

                    val result = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                        .insert(insertPayload) { select() }
                        .decodeList<ChildVaccine>()
                        .firstOrNull()

                    if (result != null) {
                        allChildVaccines = allChildVaccines + result
                        createVaccineReminder(result)
                    }
                }

                // Mark associated reminders as sent/read
                markRemindersAsSent(childId, vaccineId)

                recalculate()
            } catch (e: Exception) {
                _vaccineState.postValue(VaccineState.Error(e.message ?: "Failed to update vaccine"))
            }
        }
    }

    private fun markRemindersAsSent(childId: Int, vaccineId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reminders = SupabaseClient.client.postgrest["REMINDER"]
                    .select {
                        filter { eq("childid", childId) }
                        filter { eq("reminder_type", "vaccine") }
                    }
                    .decodeList<Reminder>()

                for (reminder in reminders) {
                    val updatedReminder = reminder.copy(is_sent = true)
                    SupabaseClient.client.postgrest["REMINDER"]
                        .update(updatedReminder) {
                            filter { eq("remindid", reminder.RemindID) }
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        when (message.messageType) {
            "INFORM_INITIAL_VACCINE_FORM_READY" -> {
                val vaccines = message.content["availableVaccines"] as? List<*>
                val children = message.content["availableChildren"] as? List<*>

                allVaccinations = vaccines?.filterIsInstance<Vaccination>() ?: emptyList()
                allChildren = children?.filterIsInstance<Child>() ?: emptyList()

                _availableVaccines.postValue(allVaccinations)
                _children.postValue(allChildren)
                _isFormReady.postValue(true)
            }
            "INFORM_CHILD_VACCINE_ADD_SUCCESS" -> {
                val sessionManager = SessionManager(getApplication())
                val user = sessionManager.getUserSession()
                user?.let {
                    requestVaccinationData(it.UserID)
                }
            }
        }
    }

    private fun recalculate() {
        viewModelScope.launch {
            try {
                val targetChildren = if (filterChild != null) {
                    listOf(filterChild!!)
                } else {
                    allChildren
                }

                if (targetChildren.isEmpty()) {
                    _vaccineState.postValue(VaccineState.Error("No children found"))
                    return@launch
                }

                val currentDate = LocalDate.now()
                val allItems = mutableListOf<VaccineScheduleItem>()
                val childGroups = mutableListOf<ChildVaccineGroup>()
                var totalDone = 0
                var totalUp = 0
                var totalOver = 0

                for (child in targetChildren) {
                    val birthDate = parseDate(child.date_of_birth) ?: continue
                    val ageInMonths = ChronoUnit.MONTHS.between(birthDate, currentDate).toInt()
                    val childItems = mutableListOf<VaccineScheduleItem>()
                    var childDoneCount = 0

                    val allVaccinesForChild = allVaccinations.sortedBy { it.recommended_age_weeks }

                    for (vaccine in allVaccinesForChild) {
                        val cv = allChildVaccines.find { it.ChildID == child.ChildID && it.VaccineID == vaccine.VaccineID }

                        val status = if (cv != null) {
                            when (cv.status?.lowercase()) {
                                "done", "completed", "skipped" -> VaccineStatus.DONE
                                "upcoming", "scheduled", "due" -> VaccineStatus.UPCOMING
                                "overdue" -> VaccineStatus.OVERDUE
                                else -> VaccineStatus.UPCOMING
                            }
                        } else {
                            val isOverdue = vaccine.recommended_age_weeks?.let { weeks ->
                                birthDate.plusWeeks(weeks.toLong()).isBefore(currentDate)
                            } ?: false
                            if (isOverdue) VaccineStatus.OVERDUE else VaccineStatus.UPCOMING
                        }

                        if (status == VaccineStatus.DONE) {
                            totalDone++
                            childDoneCount++
                        } else if (status == VaccineStatus.UPCOMING) {
                            totalUp++
                        } else {
                            totalOver++
                        }

                        val givenDate = if (status == VaccineStatus.DONE) {
                            cv?.administered_date?.let {
                                try { LocalDate.parse(it, dbDateFormatter) } catch (e: Exception) { null }
                            }
                        } else null

                        val dueDate = if (cv != null && status != VaccineStatus.DONE) {
                            cv.administered_date?.let {
                                try { LocalDate.parse(it, dbDateFormatter) } catch (e: Exception) { null }
                            }
                        } else {
                            vaccine.recommended_age_weeks?.let { weeks ->
                                birthDate.plusWeeks(weeks.toLong())
                            }
                        }

                        val ageText = vaccine.recommended_age_weeks?.let { weeks ->
                            when {
                                weeks <= 4 -> "At birth"
                                weeks <= 12 -> "$weeks weeks (${weeks / 4} months)"
                                else -> "${weeks / 4} months"
                            }
                        } ?: "Unknown"

                        childItems.add(
                            VaccineScheduleItem(
                                vaccineId = vaccine.VaccineID,
                                vaccineName = vaccine.vaccine_name ?: "Unknown",
                                ageRequirement = ageText,
                                status = status,
                                givenDate = givenDate,
                                dueDate = dueDate,
                                description = cv?.notes ?: vaccine.description ?: "",
                                recommendedAgeWeeks = vaccine.recommended_age_weeks ?: 0,
                                childId = child.ChildID,
                                childName = child.full_name ?: "Unknown"
                            )
                        )
                    }

                    allItems.addAll(childItems)

                    val childAgeString = child.date_of_birth?.let { calculateAge(it) } ?: ""
                    childGroups.add(
                        ChildVaccineGroup(
                            child = child,
                            ageText = childAgeString,
                            completedCount = childDoneCount,
                            totalCount = childItems.size,
                            items = childItems
                        )
                    )
                }

                val sorted = allItems.sortedWith(
                    compareBy(
                        {
                            when (it.status) {
                                VaccineStatus.OVERDUE -> 0
                                VaccineStatus.UPCOMING -> 1
                                VaccineStatus.DONE -> 2
                            }
                        },
                        { it.recommendedAgeWeeks }
                    )
                )

                val total = totalDone + totalUp + totalOver
                val progress = if (total > 0) ((totalDone.toFloat() / total) * 100).toInt() else 0

                _vaccineState.postValue(
                    VaccineState.Success(
                        completedCount = totalDone,
                        upcomingCount = totalUp,
                        overdueCount = totalOver,
                        percentage = progress,
                        scheduleItems = sorted,
                        childGroups = childGroups,
                        progressLabel = if (filterChild != null) "${filterChild!!.full_name} – Progress" else "All Children – Progress",
                        isAllChildren = (filterChild == null)
                    )
                )
            } catch (e: Exception) {
                _vaccineState.postValue(VaccineState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private fun calculateAge(dateOfBirth: String): String {
        return try {
            val birthDate = LocalDate.parse(dateOfBirth)
            val currentDate = LocalDate.now()
            val years = java.time.Period.between(birthDate, currentDate).years
            val months = java.time.Period.between(birthDate, currentDate).months
            when {
                years > 0 -> "$years year${if (years > 1) "s" else ""} ${months} month${if (months > 1) "s" else ""}"
                months > 0 -> "$months month${if (months > 1) "s" else ""} old"
                else -> "Newborn"
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseDate(dateString: String?): LocalDate? {
        if (dateString.isNullOrBlank()) return null
        return try {
            LocalDate.parse(dateString)
        } catch (e: Exception) {
            try {
                LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            } catch (e2: Exception) {
                null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        CarelyoMessageBroker.unregisterAgent(agentName)
    }
}

// ── State Classes ──────────────────────────────────────────────────────

data class ChildVaccineGroup(
    val child: Child,
    val ageText: String,
    val completedCount: Int,
    val totalCount: Int,
    val items: List<VaccineScheduleItem>
)

sealed class VaccineState {
    object Loading : VaccineState()
    data class Success(
        val completedCount: Int,
        val upcomingCount: Int,
        val overdueCount: Int,
        val percentage: Int,
        val scheduleItems: List<VaccineScheduleItem>,
        val childGroups: List<ChildVaccineGroup> = emptyList(),
        val progressLabel: String,
        val isAllChildren: Boolean
    ) : VaccineState()
    data class Error(val message: String) : VaccineState()
}

enum class VaccineStatus { DONE, UPCOMING, OVERDUE }

data class VaccineScheduleItem(
    val vaccineId: Int,
    val vaccineName: String,
    val ageRequirement: String,
    val status: VaccineStatus,
    val givenDate: LocalDate?,
    val dueDate: LocalDate?,
    val description: String,
    val recommendedAgeWeeks: Int,
    val childId: Int,
    val childName: String
)