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

class VaccinationMonitoringAgent(private val scope: CoroutineScope) : CarelyoAgent {
    override val agentName: String = "VaccinationMonitoringAgent"

    // Cache for data
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
                    println("[$agentName]: Received ${it.size} child profiles")
                    it.forEach { child ->
                        println("[$agentName]: Running immunisation audit evaluation for: ${child.full_name}")
                        evaluateScheduleMetrics(child)
                    }
                }
            }
            "REQUEST_VACCINATION_DATA" -> {
                // Handle request from VaccineViewModel
                val sender = message.sender
                val child = message.content["child"] as? Child
                // Launch coroutine to handle suspend function
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

    private suspend fun performDetailedAudit(child: Child) {
        try {
            val birthDate = LocalDate.parse(child.date_of_birth)
            val currentDate = LocalDate.now()
            val ageInMonths = ChronoUnit.MONTHS.between(birthDate, currentDate).toInt()

            // Get child's completed vaccines
            val childCompletedVaccines = cachedChildVaccines.filter {
                it.ChildID == child.ChildID &&
                        it.status?.equals("Administered", ignoreCase = true) == true
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
                // Fetch all vaccines from VACCINATION table if not cached
                if (cachedVaccinations.isEmpty()) {
                    val vaccinationsResult = SupabaseClient.client.postgrest["VACCINATION"]
                        .select()
                        .decodeList<Vaccination>()
                    cachedVaccinations = vaccinationsResult
                    println("[$agentName]: Fetched ${cachedVaccinations.size} vaccines")
                }

                // Fetch all child vaccines if not cached
                if (cachedChildVaccines.isEmpty()) {
                    val childVaccinesResult = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                        .select()
                        .decodeList<ChildVaccine>()
                    cachedChildVaccines = childVaccinesResult
                    println("[$agentName]: Fetched ${cachedChildVaccines.size} child vaccine records")
                }

                // Filter by child if specified
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

                println("[$agentName]: Vaccination data sent to $requestingAgent. Vaccines: ${cachedVaccinations.size}, Child Vaccines: ${filteredChildVaccines.size}")
            } catch (e: Exception) {
                println("[$agentName]: Error fetching vaccination data: ${e.localizedMessage}")
                e.printStackTrace()
                val errorMsg = CarelyoMessage(
                    sender = agentName,
                    receiver = requestingAgent,
                    messageType = "INFORM_VACCINATION_ERROR",
                    content = mapOf("error" to (e.localizedMessage ?: "Unknown error"))
                )
                CarelyoMessageBroker.passMessage(errorMsg)
            }
        }
    }

    private fun evaluateScheduleMetrics(child: Child) {
        val birthDateStr = child.date_of_birth ?: return
        try {
            val birthDate = LocalDate.parse(birthDateStr)
            val currentDate = LocalDate.now()
            val ageInWeeks = ChronoUnit.WEEKS.between(birthDate, currentDate).toInt()
            val ageInMonths = ChronoUnit.MONTHS.between(birthDate, currentDate).toInt()

            println("[$agentName]: Chronological metrics calculated for ${child.full_name} -> Age: $ageInWeeks Weeks ($ageInMonths Months)")

            val targetedVaccines = getTargetedVaccinesByAge(ageInMonths)

            println("[$agentName]: KKM immunization audit complete for ${child.full_name}. Recommended actions: $targetedVaccines")

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
            ageInMonths in 9..11 -> listOf("MMR Dose 1", "JE Dose 1 (Sarawak only)")
            ageInMonths in 12..17 -> listOf("MMR Dose 2", "JE Dose 2 (Sarawak only)")
            ageInMonths in 18..23 -> listOf("DTaP Booster", "IPV Booster")
            ageInMonths in 24..71 -> listOf("MMR Booster (if needed)", "JE Booster (Sarawak only)")
            else -> listOf("School entry boosters: dTap-IPV, MMR, HPV (adolescent)")
        }
    }

    private suspend fun executeImmunisationAudit(child: Child, ageInMonths: Int) {
        val targetedVaccines = getTargetedVaccinesByAge(ageInMonths)

        // Get child's completed vaccines
        val childCompletedVaccines = cachedChildVaccines.filter {
            it.ChildID == child.ChildID &&
                    it.status?.equals("Administered", ignoreCase = true) == true
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
            Expected KKM Immunisations: ${targetedVaccines.joinToString()}
            Completed Immunisations: ${completedVaccineNames.joinToString()}
            Missing Immunisations: ${missingVaccines.joinToString()}
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
                "missingVaccines" to missingVaccines,
                "completedCount" to completedVaccineNames.size,
                "expectedCount" to targetedVaccines.size
            )
        )
        CarelyoMessageBroker.passMessage(auditResponseMsg)
    }

    private fun getExpectedVaccinesForAge(ageInMonths: Int): List<Vaccination> {
        return cachedVaccinations.filter { vaccine ->
            vaccine.recommended_age_weeks?.let { weeks ->
                val recommendedMonths = weeks / 4
                recommendedMonths <= ageInMonths + 2 // Allow 2 months grace period
            } ?: false
        }
    }

    fun refreshData() {
        cachedVaccinations = emptyList()
        cachedChildVaccines = emptyList()
    }
}