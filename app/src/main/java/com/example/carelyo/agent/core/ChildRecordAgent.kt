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
                    fetchChildProfilesForParent(user.UserID)
                }
            }
        }
    }

    private fun fetchChildProfilesForParent(parentId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // Fetch rows from the CHILD table matching the Parent_ID foreign key constraint
                val result = SupabaseClient.client.postgrest["CHILD"].select {
                    filter {
                        eq("Parent_ID", parentId)
                    }
                }
                val childrenList = result.decodeList<Child>()

                if (childrenList.isNotEmpty()) {
                    println("[$agentName]: Located ${childrenList.size} registered child profiles associated with Parent Account.")

                    // Route child data details to the specialized domain tracking agents
                    val childrenLoadedMsg = CarelyoMessage(
                        sender = agentName,
                        receiver = "VaccinationMonitoringAgent",
                        messageType = "INFORM_CHILD_PROFILES_READY",
                        content = mapOf("children" to childrenList)
                    )
                    CarelyoMessageBroker.passMessage(childrenLoadedMsg)
                } else {
                    println("[$agentName]: Database query returned empty array. No child records initialized for parent reference ID: $parentId")
                }
            } catch (e: Exception) {
                println("[$agentName]: Error querying database: ${e.localizedMessage}")
            }
        }
    }
}