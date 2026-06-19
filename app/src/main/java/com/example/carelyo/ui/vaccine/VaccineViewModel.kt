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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * VaccineViewModel
 *
 * Operates in two modes:
 *  • ALL-CHILDREN mode  (filterChild == null)
 *    Stats and list show aggregated data for every child of this parent.
 *    Each list item carries a visible child-name badge.
 *
 *  • SINGLE-CHILD mode  (filterChild != null)
 *    Stats and list are scoped to that child only.
 *    Child-name badge is hidden in the adapter.
 *
 * The ViewModel registers itself as a CarelyoAgent named "VaccineViewModelAgent".
 * VaccinationMonitoringAgent sends INFORM_VACCINATION_DATA back to this name.
 */
class VaccineViewModel(application: Application) : AndroidViewModel(application), CarelyoAgent {

    override val agentName: String = "VaccineViewModelAgent"

    // ── State ────────────────────────────────────────────────────────────
    private val _vaccineState = MutableLiveData<VaccineState>()
    val vaccineState: LiveData<VaccineState> = _vaccineState

    // Expose children list so the Fragment can build filter chips
    private val _children = MutableLiveData<List<Child>>()
    val children: LiveData<List<Child>> = _children

    // ── Local cache from last INFORM_VACCINATION_DATA ────────────────────
    private var allVaccinations: List<Vaccination>  = emptyList()
    private var allChildVaccines: List<ChildVaccine> = emptyList()
    private var allChildren: List<Child>             = emptyList()

    /** Currently active filter – null means "show all children". */
    private var filterChild: Child? = null

    init {
        CarelyoMessageBroker.registerAgent(this)
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Called by the fragment on start (and on chip selection = "All Children").
     * [parentId] is the logged-in user's UserID from SessionManager.
     */
    fun requestVaccinationData(parentId: Int, child: Child? = null) {
        filterChild = child
        _vaccineState.postValue(VaccineState.Loading)

        CarelyoMessageBroker.passMessage(
            CarelyoMessage(
                sender      = agentName,
                receiver    = "VaccinationMonitoringAgent",
                messageType = "REQUEST_VACCINATION_DATA",
                content     = mapOf(
                    "parentId" to parentId,
                    "child"    to child          // null = fetch all children
                )
            )
        )
    }

    /**
     * Called when the user taps a child chip.
     * Filters already-loaded data without a new network round-trip.
     */
    fun filterByChild(child: Child?) {
        filterChild = child
        recalculate()
    }

    fun requestVaccinationAudit(child: Child) {
        CarelyoMessageBroker.passMessage(
            CarelyoMessage(
                sender      = agentName,
                receiver    = "VaccinationMonitoringAgent",
                messageType = "REQUEST_VACCINATION_AUDIT",
                content     = mapOf("child" to child)
            )
        )
    }

    // ── CarelyoAgent ─────────────────────────────────────────────────────

    override fun processIncomingMessage(message: CarelyoMessage) {
        when (message.messageType) {
            "INFORM_VACCINATION_DATA" -> {
                val vaccinations  = message.content["vaccinations"]  as? List<*>
                val childVaccines = message.content["childVaccines"] as? List<*>
                val children      = message.content["children"]      as? List<*>

                allVaccinations  = vaccinations?.filterIsInstance<Vaccination>()  ?: emptyList()
                allChildVaccines = childVaccines?.filterIsInstance<ChildVaccine>() ?: emptyList()
                allChildren      = children?.filterIsInstance<Child>()             ?: emptyList()

                // Expose children list to fragment for chip generation
                _children.postValue(allChildren)

                recalculate()
            }

            "INFORM_VACCINATION_ERROR" -> {
                val error = message.content["error"] as? String
                _vaccineState.postValue(VaccineState.Error(error ?: "Unknown error occurred"))
            }

            "INFORM_VACCINATION_AUDIT_REPORT" -> {
                val reportLog = message.content["reportLog"] as? String
                println("[VaccineViewModel] Audit: $reportLog")
            }
        }
    }

    // ── Calculation ──────────────────────────────────────────────────────

    private fun recalculate() {
        viewModelScope.launch {
            try {
                val targetChildren = if (filterChild != null) {
                    listOf(filterChild!!)
                } else {
                    allChildren
                }

                if (targetChildren.isEmpty()) {
                    _vaccineState.postValue(VaccineState.Error("No children found for this account"))
                    return@launch
                }

                val currentDate = LocalDate.now()
                val allItems    = mutableListOf<VaccineScheduleItem>()
                var totalDone   = 0
                var totalUp     = 0
                var totalOver   = 0

                for (child in targetChildren) {
                    val birthDate = parseDate(child.date_of_birth) ?: continue
                    val ageInMonths = ChronoUnit.MONTHS.between(birthDate, currentDate).toInt()

                    // Vaccines the child is expected to have by this age
                    val expected = getExpectedVaccinesForAge(ageInMonths)

                    // Administered records for this child
                    val administered = allChildVaccines.filter {
                        it.ChildID == child.ChildID &&
                                it.status?.equals("Administered", ignoreCase = true) == true
                    }
                    val administeredMap  = administered.associateBy { it.VaccineID }
                    val completedIds     = administered.map { it.VaccineID }.toSet()

                    val overdueIds = expected
                        .filter { it.VaccineID !in completedIds }
                        .filter { vaccine ->
                            vaccine.recommended_age_weeks?.let { weeks ->
                                birthDate.plusWeeks(weeks.toLong()).isBefore(currentDate)
                            } ?: false
                        }
                        .map { it.VaccineID }
                        .toSet()

                    totalDone += expected.count { it.VaccineID in completedIds }
                    totalOver += overdueIds.size
                    totalUp   += expected.count {
                        it.VaccineID !in completedIds && it.VaccineID !in overdueIds
                    }

                    // Build schedule items for this child
                    for (vaccine in expected) {
                        val cv     = administeredMap[vaccine.VaccineID]
                        val status = when {
                            cv != null                          -> VaccineStatus.DONE
                            vaccine.VaccineID in overdueIds     -> VaccineStatus.OVERDUE
                            else                                -> VaccineStatus.UPCOMING
                        }

                        val givenDate = cv?.administered_date?.let { parseDate(it) }

                        val ageText = vaccine.recommended_age_weeks?.let { weeks ->
                            when {
                                weeks <= 4  -> "At birth"
                                weeks <= 12 -> "$weeks weeks (${weeks / 4} months)"
                                else        -> "${weeks / 4} months"
                            }
                        } ?: "Unknown"

                        allItems.add(
                            VaccineScheduleItem(
                                vaccineId          = vaccine.VaccineID,
                                vaccineName        = vaccine.vaccine_name ?: "Unknown Vaccine",
                                ageRequirement     = ageText,
                                status             = status,
                                givenDate          = givenDate,
                                description        = vaccine.description ?: "No description available",
                                recommendedAgeWeeks = vaccine.recommended_age_weeks ?: 0,
                                childId            = child.ChildID,
                                childName          = child.full_name ?: "Unknown Child"
                            )
                        )
                    }
                }

                // Sort: Overdue → Upcoming → Done, then by age within each group
                val sorted = allItems.sortedWith(
                    compareBy(
                        {
                            when (it.status) {
                                VaccineStatus.OVERDUE  -> 0
                                VaccineStatus.UPCOMING -> 1
                                VaccineStatus.DONE     -> 2
                            }
                        },
                        { it.recommendedAgeWeeks },
                        { it.childName }
                    )
                )

                val total    = totalDone + totalUp + totalOver
                val progress = if (total > 0) ((totalDone.toFloat() / total) * 100).toInt() else 0

                val label = if (filterChild != null)
                    "${filterChild!!.full_name} – Progress"
                else
                    "All Children – Progress"

                _vaccineState.postValue(
                    VaccineState.Success(
                        completedCount  = totalDone,
                        upcomingCount   = totalUp,
                        overdueCount    = totalOver,
                        percentage      = progress,
                        scheduleItems   = sorted,
                        progressLabel   = label,
                        isAllChildren   = (filterChild == null)
                    )
                )

            } catch (e: Exception) {
                _vaccineState.postValue(VaccineState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    // ── Utilities ────────────────────────────────────────────────────────

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

    /**
     * Returns all master vaccines recommended up to [ageInMonths] + 3 months grace.
     * Sorted by recommended_age_weeks ascending.
     */
    private fun getExpectedVaccinesForAge(ageInMonths: Int): List<Vaccination> =
        allVaccinations.filter { vaccine ->
            val weeks = vaccine.recommended_age_weeks ?: return@filter false
            (weeks / 4) <= ageInMonths + 3
        }.sortedBy { it.recommended_age_weeks }

    override fun onCleared() {
        super.onCleared()
        CarelyoMessageBroker.unregisterAgent(agentName)
    }
}

// ── State & data classes ──────────────────────────────────────────────────

sealed class VaccineState {
    object Loading : VaccineState()

    data class Success(
        val completedCount : Int,
        val upcomingCount  : Int,
        val overdueCount   : Int,
        val percentage     : Int,
        val scheduleItems  : List<VaccineScheduleItem>,
        val progressLabel  : String,
        val isAllChildren  : Boolean
    ) : VaccineState()

    data class Error(val message: String) : VaccineState()
}

enum class VaccineStatus { DONE, UPCOMING, OVERDUE }

data class VaccineScheduleItem(
    val vaccineId           : Int,
    val vaccineName         : String,
    val ageRequirement      : String,
    val status              : VaccineStatus,
    val givenDate           : LocalDate?,
    val description         : String,
    val recommendedAgeWeeks : Int,
    // Multi-child fields
    val childId             : Int,
    val childName           : String
)