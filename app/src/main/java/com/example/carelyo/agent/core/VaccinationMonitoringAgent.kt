package com.example.carelyo.agent.core

import com.example.carelyo.agent.infra.CarelyoAgent
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.data.entity.Child
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.example.carelyo.agent.infra.CarelyoMessageBroker

class VaccinationMonitoringAgent : CarelyoAgent {
    override val agentName: String = "VaccinationMonitoringAgent"

    init {
        CarelyoMessageBroker.registerAgent(this)
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        when (message.messageType) {
            "INFORM_CHILD_PROFILES_READY" -> {
                val children = message.content["children"] as? List<*>
                children?.filterIsInstance<Child>()?.forEach { child ->
                    println("[$agentName]: Running immunisation audit evaluation for: ${child.full_name}")
                    evaluateScheduleMetrics(child)
                }
            }
        }
    }

    private fun evaluateScheduleMetrics(child: Child) {
        val birthDateStr = child.date_of_birth ?: return
        try {
            // Calculate child age metrics relative to system time (2026 reference plane)
            val birthDate = LocalDate.parse(birthDateStr)
            val currentDate = LocalDate.now()
            val ageInWeeks = ChronoUnit.WEEKS.between(birthDate, currentDate).toInt()
            val ageInMonths = ChronoUnit.MONTHS.between(birthDate, currentDate).toInt()

            println("[$agentName]: Chronological metrics calculated -> Age: $ageInWeeks Weeks ($ageInMonths Months)")

            // Deliberative evaluation logic mapping to Malaysia's National Immunisation Schedule rules
            val targetedVaccines = when {
                ageInMonths <= 0 -> listOf("BCG Dose 1", "Hepatitis B Dose 1")
                ageInMonths in 2..5 -> listOf("Hexavalent DTaP-IPV-HepB-Hib Dose 1 & 2", "Pneumococcal PCV Dose 1 & 2")
                ageInMonths in 6..8 -> listOf("Hexavalent DTaP-IPV-HepB-Hib Dose 3", "Pneumococcal PCV Booster")
                ageInMonths in 9..11 -> listOf("MMR Dose 1", "JE Dose 1 (Sarawak core variations)")
                else -> listOf("Routine primary infant core series sequence completed. Evaluate localized preschool boosters.")
            }

            println("[$agentName]: KKM immunization audit complete for ${child.full_name}. Recommended actions pending: $targetedVaccines")

        } catch (e: Exception) {
            println("[$agentName]: Failure computing dates for ${child.full_name}: ${e.localizedMessage}")
        }
    }

    // Inside your VaccinationMonitoringAgent.kt implementation file
    private fun executeImmunisationAudit(child: Child, ageInMonths: Int) {
        val targetedVaccines = when {
            ageInMonths <= 0 -> listOf("BCG Dose 1", "Hepatitis B Dose 1")
            ageInMonths in 2..5 -> listOf("Hexavalent DTaP-IPV-HepB-Hib Dose 1", "Pneumococcal PCV Dose 1")
            else -> listOf("Routine primary schedules consistent.")
        }

        val detailedLogReport = """
        [MAS Audit Verification Complete]
        Target Child: ${child.full_name}
        Calculated Age Boundary: $ageInMonths Months
        Pending KKM Immunisations: $targetedVaccines
    """.trimIndent()

        // Pass the message back into the system layer so the Dashboard View Bridge can display it
        val auditResponseMsg = CarelyoMessage(
            sender = agentName,
            receiver = "DashboardViewModelAgent",
            messageType = "INFORM_VACCINATION_AUDIT_REPORT",
            content = mapOf("reportLog" to detailedLogReport)
        )
        CarelyoMessageBroker.passMessage(auditResponseMsg)
    }
}