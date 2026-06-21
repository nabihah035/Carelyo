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
import com.example.carelyo.data.entity.Vaccination
import com.example.carelyo.api.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
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

                // Fetch vaccines directly
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
                val result = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                    .insert(childVaccine) { select() }
                    .decodeList<ChildVaccine>()
                    .firstOrNull()

                if (result != null) {
                    allChildVaccines = allChildVaccines + result
                    _availableVaccines.postValue(allVaccinations)
                    recalculate()
                    Toast.makeText(getApplication(), "Vaccine added successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _vaccineState.postValue(VaccineState.Error(e.message ?: "Failed to add vaccine"))
            }
        }
    }

    fun markVaccineAsTaken(childId: Int, vaccineId: Int, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Find existing record
                val existingRecord = allChildVaccines.find {
                    it.ChildID == childId && it.VaccineID == vaccineId
                }

                if (existingRecord != null) {
                    // Update existing record
                    val today = LocalDate.now().format(dateFormatter)
                    val updatedRecord = existingRecord.copy(
                        status = "Done",
                        administered_date = today,
                        notes = if (existingRecord.notes?.isNotEmpty() == true)
                            "${existingRecord.notes}\n$notes"
                        else notes
                    )

                    SupabaseClient.client.postgrest["CHILD_VACCINE"]
                        .update(updatedRecord) {
                            filter {
                                eq("childvaccineid", existingRecord.ChildVaccineID ?: 0)
                            }
                        }

                    // Update local cache
                    allChildVaccines = allChildVaccines.map {
                        if (it.ChildVaccineID == existingRecord.ChildVaccineID) updatedRecord else it
                    }
                } else {
                    // Create new record
                    val today = LocalDate.now().format(dateFormatter)
                    val newRecord = ChildVaccine(
                        ChildID = childId,
                        VaccineID = vaccineId,
                        status = "Done",
                        administered_date = today,
                        notes = notes
                    )

                    val result = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                        .insert(newRecord) { select() }
                        .decodeList<ChildVaccine>()
                        .firstOrNull()

                    if (result != null) {
                        allChildVaccines = allChildVaccines + result
                    }
                }

                recalculate()
                Toast.makeText(getApplication(), "Vaccine marked as taken!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _vaccineState.postValue(VaccineState.Error(e.message ?: "Failed to update vaccine"))
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
                // Refresh data
                val sessionManager = com.example.carelyo.data.session.SessionManager(getApplication())
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
                var totalDone = 0
                var totalUp = 0
                var totalOver = 0

                for (child in targetChildren) {
                    val birthDate = parseDate(child.date_of_birth) ?: continue
                    val ageInMonths = ChronoUnit.MONTHS.between(birthDate, currentDate).toInt()

                    // Get all vaccines
                    val allVaccinesForChild = allVaccinations.sortedBy { it.recommended_age_weeks }

                    // Build schedule items
                    for (vaccine in allVaccinesForChild) {
                        val cv = allChildVaccines.find { it.ChildID == child.ChildID && it.VaccineID == vaccine.VaccineID }

                        val status = if (cv != null) {
                            when (cv.status?.lowercase()) {
                                "done", "completed" -> VaccineStatus.DONE
                                "upcoming" -> VaccineStatus.UPCOMING
                                "overdue" -> VaccineStatus.OVERDUE
                                else -> VaccineStatus.DONE
                            }
                        } else {
                            val isOverdue = vaccine.recommended_age_weeks?.let { weeks ->
                                birthDate.plusWeeks(weeks.toLong()).isBefore(currentDate)
                            } ?: false
                            if (isOverdue) VaccineStatus.OVERDUE else VaccineStatus.UPCOMING
                        }

                        if (status == VaccineStatus.DONE) totalDone++
                        else if (status == VaccineStatus.UPCOMING) totalUp++
                        else totalOver++

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

                        allItems.add(
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
                        progressLabel = if (filterChild != null) "${filterChild!!.full_name} – Progress" else "All Children – Progress",
                        isAllChildren = (filterChild == null)
                    )
                )
            } catch (e: Exception) {
                _vaccineState.postValue(VaccineState.Error(e.message ?: "Unknown error"))
            }
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

sealed class VaccineState {
    object Loading : VaccineState()
    data class Success(
        val completedCount: Int,
        val upcomingCount: Int,
        val overdueCount: Int,
        val percentage: Int,
        val scheduleItems: List<VaccineScheduleItem>,
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