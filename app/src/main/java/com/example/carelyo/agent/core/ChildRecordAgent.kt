package com.example.carelyo.agent.core

import com.example.carelyo.agent.infra.CarelyoAgent
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.agent.infra.CarelyoMessageBroker
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.User
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChildRecordAgent(private val scope: CoroutineScope) : CarelyoAgent {
    override val agentName: String = "ChildRecordAgent"

    init {
        CarelyoMessageBroker.registerAgent(this)
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        when (message.messageType) {
            "INFORM_USER_SESSION_ACTIVE", "INFORM_REGISTRATION_SUCCESSFUL" -> {
                val user = message.content["user"] as? User
                if (user != null) {
                    println("[$agentName]: Detected active user context for ID: ${user.UserID}. Fetching linked child metrics...")

                    // BUG FIX #3: Guard against UserID=0. This happens when the User object
                    // was restored from SharedPrefs and @EncodeDefault(NEVER) stripped the
                    // UserID field during serialisation — resulting in the default value of 0.
                    if (user.UserID <= 0) {
                        println("[$agentName]: UserID is 0 or invalid — session object is corrupt. Aborting child fetch.")
                        CarelyoMessageBroker.passMessage(
                            CarelyoMessage(
                                sender = agentName,
                                receiver = "DashboardViewModelAgent",
                                messageType = "CHILD_FETCH_ERROR",
                                content = mapOf("reason" to "Invalid UserID in session. Please log out and log in again.")
                            )
                        )
                        return
                    }

                    fetchChildProfilesForParent(user.UserID)
                }
            }
        }
    }

    private fun fetchChildProfilesForParent(parentId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseClient.client.postgrest["CHILD"].select {
                    filter { eq("Parent_ID", parentId) }
                }
                val childrenList = result.decodeList<Child>()

                if (childrenList.isNotEmpty()) {
                    println("[$agentName]: Located ${childrenList.size} registered child profiles for parent ID: $parentId")

                    // BUG FIX #1 — CRITICAL:
                    // The original code sent this message ONLY to "VaccinationMonitoringAgent".
                    // DashboardViewModelAgent listens for "INFORM_CHILD_PROFILES_READY" but
                    // never received it, so _childrenList was never populated and the progress
                    // bar spun indefinitely.
                    //
                    // Fix: send as BROADCAST so BOTH DashboardViewModelAgent (UI) and
                    // VaccinationMonitoringAgent (audit) receive it in one pass.
                    val childrenLoadedMsg = CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",                  // ← was "VaccinationMonitoringAgent"
                        messageType = "INFORM_CHILD_PROFILES_READY",
                        content = mapOf("children" to childrenList)
                    )
                    CarelyoMessageBroker.passMessage(childrenLoadedMsg)
                } else {
                    println("[$agentName]: No child records found for parent ID: $parentId")

                    // Notify dashboard so it can hide the loading spinner
                    CarelyoMessageBroker.passMessage(
                        CarelyoMessage(
                            sender = agentName,
                            receiver = "DashboardViewModelAgent",
                            messageType = "INFORM_CHILD_PROFILES_READY",
                            content = mapOf("children" to emptyList<Child>())
                        )
                    )
                }
            } catch (e: Exception) {
                println("[$agentName]: Error querying CHILD table: ${e.localizedMessage}")
                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = agentName,
                        receiver = "DashboardViewModelAgent",
                        messageType = "CHILD_FETCH_ERROR",
                        content = mapOf("reason" to (e.localizedMessage ?: "Unknown error"))
                    )
                )
            }
        }
    }
}