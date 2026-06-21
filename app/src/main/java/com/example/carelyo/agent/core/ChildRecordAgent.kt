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

                    if (user.UserID <= 0) {
                        println("[$agentName]: UserID is 0 or invalid. Aborting child fetch.")
                        broadcastError("Invalid UserID in session. Please log out and log in again.")
                        return
                    }
                    fetchChildProfilesForParent(user.UserID)
                }
            }

            "REQUEST_CHILD_DATA" -> {
                val parentId = message.content["parentId"] as? Int
                if (parentId != null && parentId > 0) {
                    println("[$agentName]: Received request to fetch children for parent ID: $parentId")
                    fetchChildProfilesForParent(parentId)
                } else {
                    println("[$agentName]: Invalid parent ID in REQUEST_CHILD_DATA")
                    broadcastError("Invalid parent ID")
                }
            }

            "REQUEST_ADD_CHILD" -> {
                val child = message.content["child"] as? Child
                if (child != null) {
                    println("[$agentName]: Received request to add new child: ${child.full_name} for Parent ID: ${child.Parent_ID}")
                    insertChildRecord(child)
                } else {
                    println("[$agentName]: Received invalid or null child payload inside REQUEST_ADD_CHILD")
                    broadcastError("Invalid child data")
                }
            }
        }
    }

    private fun insertChildRecord(child: Child) {
        scope.launch(Dispatchers.IO) {
            try {
                // Create a copy with only the fields we want to insert
                // The database will auto-generate childid
                val childToInsert = mapOf(
                    "parent_id" to child.Parent_ID,
                    "full_name" to child.full_name,
                    "date_of_birth" to child.date_of_birth,
                    "gender" to child.gender,
                    "blood_type" to child.blood_type,
                    "weight" to child.weight?.toString(),
                    "height" to child.height?.toString(),
                    "profile_photo_url" to child.profile_photo_url
                )

                // Insert using the map to ensure proper column mapping
                SupabaseClient.client.postgrest["CHILD"].insert(childToInsert)
                println("[$agentName]: Successfully inserted new child record into database.")

                // Refresh the parent collection stack so UI updates instantly
                fetchChildProfilesForParent(child.Parent_ID)

                // Notify via Broadcast that child registration completed cleanly
                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",
                        messageType = "INFORM_CHILD_ADD_SUCCESS",
                        content = emptyMap()
                    )
                )
            } catch (e: Exception) {
                println("[$agentName]: Failed to insert child record: ${e.localizedMessage}")
                broadcastError("Failed to save child data: ${e.localizedMessage}")
            }
        }
    }

    private fun fetchChildProfilesForParent(parentId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseClient.client.postgrest["CHILD"].select {
                    filter { eq("parent_id", parentId) }
                }
                val childrenList = result.decodeList<Child>()

                println("[$agentName]: Located ${childrenList.size} registered child profiles for parent ID: $parentId")

                val childrenLoadedMsg = CarelyoMessage(
                    sender = agentName,
                    receiver = "BROADCAST",
                    messageType = "INFORM_CHILD_PROFILES_READY",
                    content = mapOf("children" to childrenList)
                )
                CarelyoMessageBroker.passMessage(childrenLoadedMsg)
            } catch (e: Exception) {
                println("[$agentName]: Error querying CHILD table: ${e.localizedMessage}")
                broadcastError(e.localizedMessage ?: "Unknown database error encountered.")
            }
        }
    }

    private fun broadcastError(reason: String) {
        CarelyoMessageBroker.passMessage(
            CarelyoMessage(
                sender = agentName,
                receiver = "BROADCAST",
                messageType = "CHILD_FETCH_ERROR",
                content = mapOf("reason" to reason)
            )
        )
    }
}