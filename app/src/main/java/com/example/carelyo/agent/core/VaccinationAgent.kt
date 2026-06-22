package com.example.carelyo.agent.core

import com.example.carelyo.agent.infra.CarelyoAgent
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.agent.infra.CarelyoMessageBroker
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.ChildVaccine
import com.example.carelyo.data.entity.Vaccination
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class VaccinationAgent(private val scope: CoroutineScope) : CarelyoAgent {
    override val agentName: String = "VaccinationMonitoringAgent"

    private var cachedVaccinations: List<Vaccination> = emptyList()
    private var cachedChildVaccines: List<ChildVaccine> = emptyList()
    private var cachedChildren: List<Child> = emptyList()

    init {
        CarelyoMessageBroker.registerAgent(this)
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        when (message.messageType) {
            "INFORM_CHILD_PROFILES_READY" -> {
                val children = message.content["children"] as? List<*>
                children?.filterIsInstance<Child>()?.let {
                    cachedChildren = it
                    println("[$agentName]: Cached ${it.size} child profiles from message broker context.")
                    it.forEach { child ->
                        evaluateScheduleMetrics(child)
                    }
                }
            }

            // ── NEW HOOK: FETCH FORM SELECTION POPULATION BASELINES ──
            "REQUEST_INITIAL_VACCINE_FORM_DATA" -> {
                val sender = message.sender
                scope.launch {
                    fetchFormDropdownBaselines(sender)
                }
            }

            // ── NEW HOOK: INSERT ASSIGNED VACCINATION FOR SELECTED CHILD ──
            "REQUEST_ADD_CHILD_VACCINE" -> {
                val childVaccinePayload = message.content["childVaccine"] as? ChildVaccine
                if (childVaccinePayload != null) {
                    insertNewChildVaccineSchedule(childVaccinePayload)
                }
            }

            "REQUEST_VACCINATION_DATA" -> {
                val sender = message.sender
                val child = message.content["child"] as? Child
                scope.launch {
                    fetchAllVaccinationData(sender, child)
                }
            }

            "REQUEST_VACCINATION_AUDIT" -> {
                val child = message.content["child"] as? Child
                child?.let {
                    scope.launch {
                        performDetailedAudit(it)
                    }
                }
            }
        }
    }

    private suspend fun fetchFormDropdownBaselines(requestingAgent: String) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Ensure master list of vaccines is loaded
                if (cachedVaccinations.isEmpty()) {
                    cachedVaccinations = SupabaseClient.client.postgrest["VACCINATION"]
                        .select()
                        .decodeList<Vaccination>()
                }

                // 2. Transmit available children and vaccines back to VaccineViewModel
                val responseMsg = CarelyoMessage(
                    sender = agentName,
                    receiver = requestingAgent,
                    messageType = "INFORM_INITIAL_VACCINE_FORM_READY",
                    content = mapOf(
                        "availableVaccines" to cachedVaccinations,
                        "availableChildren" to cachedChildren
                    )
                )
                CarelyoMessageBroker.passMessage(responseMsg)
                println("[$agentName]: Sent Form Data Setup. Children: ${cachedChildren.size}, Vaccines: ${cachedVaccinations.size}")

            } catch (e: Exception) {
                println("[$agentName]: Dropdown baseline retrieval failed: ${e.localizedMessage}")
                sendFormErrorNotification(requestingAgent, e.localizedMessage ?: "Failed to read lookup data.")
            }
        }
    }

    private fun insertNewChildVaccineSchedule(childVaccine: ChildVaccine) {
        scope.launch(Dispatchers.IO) {
            try {
                println("[$agentName]: Attempting database insert for Vaccine ID ${childVaccine.VaccineID} onto Child ID ${childVaccine.ChildID}")

                // Write directly to Supabase CHILD_VACCINE table
                SupabaseClient.client.postgrest["CHILD_VACCINE"].insert(childVaccine)

                println("[$agentName]: Successfully saved child vaccine mapping to DB storage tier.")

                // Reset local child-vaccine memory cache block so the next query includes the new line items
                cachedChildVaccines = emptyList()

                // Notify UI of transaction completion
                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",
                        messageType = "INFORM_CHILD_VACCINE_ADD_SUCCESS",
                        content = mapOf("childId" to childVaccine.ChildID)
                    )
                )
            } catch (e: Exception) {
                println("[$agentName]: Database insertion aborted: ${e.localizedMessage}")
                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",
                        messageType = "INFORM_VACCINATION_ERROR",
                        content = mapOf("error" to (e.localizedMessage ?: "Failed to write vaccine record."))
                    )
                )
            }
        }
    }

    private suspend fun performDetailedAudit(child: Child) {
        try {
            val birthDate = LocalDate.parse(child.date_of_birth)
            val currentDate = LocalDate.now()
            val ageInMonths = ChronoUnit.MONTHS.between(birthDate, currentDate).toInt()

            val childCompletedVaccines = cachedChildVaccines.filter {
                it.ChildID == child.ChildID && it.status?.equals("Administered", ignoreCase = true) == true
            }

            val expectedVaccines = getExpectedVaccinesForAge(ageInMonths)
            val completedVaccineIds = childCompletedVaccines.map { it.VaccineID }.toSet()
            val pendingVaccines = expectedVaccines.filter { it.VaccineID !in completedVaccineIds }
            val overdueVaccines = pendingVaccines.filter { vaccine ->
                vaccine.recommended_age_weeks?.let { weeks ->
                    val recommendedDate = birthDate.plusWeeks(weeks.toLong())
                    recommendedDate.isBefore(currentDate)
                } ?: false
            }

            val auditReport = mapOf(
                "childId" to child.ChildID,
                "childName" to (child.full_name ?: "Unknown"),
                "ageInMonths" to ageInMonths,
                "totalExpectedVaccines" to expectedVaccines.size,
                "completedVaccines" to completedVaccineIds.size,
                "pendingVaccines" to pendingVaccines.map { it.vaccine_name ?: "Unknown" },
                "overdueVaccines" to overdueVaccines.map { it.vaccine_name ?: "Unknown" },
                "auditTimestamp" to currentDate.toString()
            )

            val auditMessage = CarelyoMessage(
                sender = agentName,
                receiver = "DashboardViewModelAgent",
                messageType = "INFORM_VACCINATION_AUDIT_REPORT",
                content = mapOf("auditReport" to auditReport)
            )
            CarelyoMessageBroker.passMessage(auditMessage)
        } catch (e: Exception) {
            println("[$agentName]: Audit failed for ${child.full_name}: ${e.message}")
        }
    }

    private suspend fun fetchAllVaccinationData(requestingAgent: String, specificChild: Child?) {
        withContext(Dispatchers.IO) {
            try {
                if (cachedVaccinations.isEmpty()) {
                    cachedVaccinations = SupabaseClient.client.postgrest["VACCINATION"].select().decodeList<Vaccination>()
                }

                if (cachedChildVaccines.isEmpty()) {
                    cachedChildVaccines = SupabaseClient.client.postgrest["CHILD_VACCINE"].select().decodeList<ChildVaccine>()
                }

                val filteredChildVaccines = if (specificChild != null) {
                    cachedChildVaccines.filter { it.ChildID == specificChild.ChildID }
                } else {
                    cachedChildVaccines
                }

                val responseMsg = CarelyoMessage(
                    sender = agentName,
                    receiver = requestingAgent,
                    messageType = "INFORM_VACCINATION_DATA",
                    content = mapOf(
                        "vaccinations" to cachedVaccinations,
                        "childVaccines" to filteredChildVaccines,
                        "child" to specificChild
                    )
                )
                CarelyoMessageBroker.passMessage(responseMsg)
            } catch (e: Exception) {
                sendFormErrorNotification(requestingAgent, e.localizedMessage ?: "Unknown query exception.")
            }
        }
    }

    private fun evaluateScheduleMetrics(child: Child) {
        val birthDateStr = child.date_of_birth ?: return
        try {
            val birthDate = LocalDate.parse(birthDateStr)
            val currentDate = LocalDate.now()
            val ageInMonths = ChronoUnit.MONTHS.between(birthDate, currentDate).toInt()

            scope.launch {
                executeImmunisationAudit(child, ageInMonths)
            }
        } catch (e: Exception) {
            println("[$agentName]: Failure computing dates for ${child.full_name}: ${e.localizedMessage}")
        }
    }

    private fun getTargetedVaccinesByAge(ageInMonths: Int): List<String> {
        return when {
            ageInMonths <= 0 -> listOf("BCG Dose 1", "Hepatitis B Dose 1")
            ageInMonths in 1..1 -> listOf("Hepatitis B Dose 2")
            ageInMonths in 2..2 -> listOf("Hexavalent DTaP-IPV-HepB-Hib Dose 1", "Pneumococcal PCV Dose 1")
            ageInMonths in 3..3 -> listOf("Hexavalent DTaP-IPV-HepB-Hib Dose 2", "Pneumococcal PCV Dose 2")
            ageInMonths in 4..5 -> listOf("Hexavalent DTaP-IPV-HepB-Hib Dose 3")
            ageInMonths in 6..8 -> listOf("Pneumococcal PCV Booster")
            ageInMonths in 9..11 -> listOf("MMR Dose 1", "JE Dose 1")
            ageInMonths in 12..17 -> listOf("MMR Dose 2", "JE Dose 2")
            ageInMonths in 18..23 -> listOf("DTaP Booster", "IPV Booster")
            else -> listOf("School entry boosters: dTap-IPV, MMR, HPV")
        }
    }

    private suspend fun executeImmunisationAudit(child: Child, ageInMonths: Int) {
        val targetedVaccines = getTargetedVaccinesByAge(ageInMonths)

        val childCompletedVaccines = cachedChildVaccines.filter {
            it.ChildID == child.ChildID && it.status?.equals("Administered", ignoreCase = true) == true
        }

        val completedVaccineNames = childCompletedVaccines.mapNotNull { childVaccine ->
            cachedVaccinations.find { it.VaccineID == childVaccine.VaccineID }?.vaccine_name
        }

        val missingVaccines = targetedVaccines.filter { target ->
            !completedVaccineNames.any { completed ->
                completed.contains(target.split(" ")[0], ignoreCase = true)
            }
        }

        val detailedLogReport = """
            [MAS Audit Verification Complete]
            Target Child: ${child.full_name}
            Child ID: ${child.ChildID}
            Calculated Age Boundary: $ageInMonths Months
            Audit Status: ${if (missingVaccines.isEmpty()) "ON TRACK" else "ACTION REQUIRED"}
        """.trimIndent()

        val auditResponseMsg = CarelyoMessage(
            sender = agentName,
            receiver = "DashboardViewModelAgent",
            messageType = "INFORM_VACCINATION_AUDIT_REPORT",
            content = mapOf(
                "reportLog" to detailedLogReport,
                "childId" to child.ChildID,
                "ageInMonths" to ageInMonths,
                "missingVaccines" to missingVaccines
            )
        )
        CarelyoMessageBroker.passMessage(auditResponseMsg)
    }

    private fun getExpectedVaccinesForAge(ageInMonths: Int): List<Vaccination> {
        return cachedVaccinations.filter { vaccine ->
            vaccine.recommended_age_weeks?.let { weeks ->
                val recommendedMonths = weeks / 4
                recommendedMonths <= ageInMonths + 2
            } ?: false
        }
    }

    private fun sendFormErrorNotification(receiver: String, message: String) {
        CarelyoMessageBroker.passMessage(
            CarelyoMessage(
                sender = agentName,
                receiver = receiver,
                messageType = "INFORM_VACCINATION_ERROR",
                content = mapOf("error" to message)
            )
        )
    }
}