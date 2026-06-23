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

            "REQUEST_DELETE_CHILD" -> {
                val childId = message.content["childId"] as? Int
                val parentId = message.content["parentId"] as? Int
                if (childId != null && parentId != null) {
                    println("[$agentName]: Received request to delete child ID: $childId")
                    deleteChildRecord(childId, parentId)
                } else {
                    broadcastError("Invalid child ID for deletion")
                }
            }
        }
    }

    private fun insertChildRecord(child: Child) {
        scope.launch(Dispatchers.IO) {
            try {
                val childToInsert = com.example.carelyo.data.entity.ChildInsert(
                    Parent_ID = child.Parent_ID,
                    full_name = child.full_name ?: "",
                    status = "Active",
                    date_of_birth = child.date_of_birth?.takeIf { it.isNotEmpty() },
                    gender = child.gender?.takeIf { it.isNotEmpty() },
                    blood_type = child.blood_type?.takeIf { it.isNotEmpty() },
                    weight = child.weight?.takeIf { it.isNotEmpty() },
                    height = child.height?.takeIf { it.isNotEmpty() }
                )

                println("[$agentName]: Inserting child with data: $childToInsert")
                println("[$agentName]: Parent ID: ${child.Parent_ID}")

                // Insert using the data class
                val result = SupabaseClient.client.postgrest["CHILD"].insert(childToInsert)
                println("[$agentName]: Insert successful. Result: $result")

                // Refresh the parent collection stack so UI updates instantly
                fetchChildProfilesForParent(child.Parent_ID)

                // Notify via Broadcast that child registration completed cleanly
                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",
                        messageType = "INFORM_CHILD_ADD_SUCCESS",
                        content = mapOf("childName" to (child.full_name ?: "Unknown"))
                    )
                )
            } catch (e: Exception) {
                println("[$agentName]: Failed to insert child record: ${e.localizedMessage}")
                e.printStackTrace()

                val errorMessage = when {
                    e.localizedMessage?.contains("status") == true ->
                        "Status must be 'Active' or 'Inactive'"
                    e.localizedMessage?.contains("foreign key") == true ->
                        "Parent ID does not exist. Please login again."
                    else -> e.localizedMessage ?: "Unknown database error"
                }

                broadcastError(errorMessage)

                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",
                        messageType = "INFORM_CHILD_ADD_FAILED",
                        content = mapOf("error" to errorMessage)
                    )
                )
            }
        }
    }

    private fun deleteChildRecord(childId: Int, parentId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // Change status to Inactive (ENUM value must be exactly 'Inactive')
                SupabaseClient.client.postgrest["CHILD"].update(
                    {
                        set("status", "Inactive")
                    }
                ) {
                    filter { eq("childid", childId) }
                }

                println("[$agentName]: Successfully updated child status to Inactive.")

                // Refresh parent collection stack
                fetchChildProfilesForParent(parentId)

                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",
                        messageType = "INFORM_CHILD_DELETE_SUCCESS",
                        content = emptyMap()
                    )
                )
            } catch (e: Exception) {
                println("[$agentName]: Failed to delete child: ${e.localizedMessage}")
                e.printStackTrace()
                broadcastError("Failed to delete child: ${e.localizedMessage}")
            }
        }
    }

    private fun fetchChildProfilesForParent(parentId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseClient.client.postgrest["CHILD"].select {
                    filter { eq("parent_id", parentId) }
                }
                // Filter out Inactive children (ENUM value must be exactly 'Inactive')
                val childrenList = result.decodeList<Child>().filter { it.status != "Inactive" }

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
                e.printStackTrace()
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