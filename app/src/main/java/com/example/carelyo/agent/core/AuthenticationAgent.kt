package com.example.carelyo.agent.core

import com.example.carelyo.agent.infra.CarelyoAgent
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.agent.infra.CarelyoMessageBroker
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.User
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthenticationAgent(private val scope: CoroutineScope) : CarelyoAgent {
    override val agentName: String = "AuthenticationAgent"

    init {
        CarelyoMessageBroker.registerAgent(this)
    }

    /**
     * Registers the user in Supabase FIRST, then fetches the auto-assigned ID,*/
    fun registerNewUser(email: String, fullName: String, phoneNumber: String, role: String) {
        scope.launch(Dispatchers.IO) {
            println("[$agentName]: Initiating Registration for: $email")
            try {
                val newUser = User(
                    email = email,
                    full_name = fullName,
                    phone_number = phoneNumber,
                    role = role,
                    firebase_fcm_token = null
                )

                // SINGLE network call: insert and get the saved row back in one shot
                val savedUser = SupabaseClient.client.postgrest["USER"]
                    .insert(newUser) { select() }
                    .decodeSingle<User>()

                println("[$agentName]: Registered with ID: ${savedUser.UserID}")

                // Broadcast success immediately — UI navigates NOW
                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",
                        messageType = "INFORM_REGISTRATION_SUCCESSFUL",
                        content = mapOf("user" to savedUser)
                    )
                )

            } catch (e: Exception) {
                println("[$agentName]: Registration failed: ${e.localizedMessage}")
                sendFailureNotification("REGISTRATION_FAILED", e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun performUserLogin(email: String) {
        scope.launch(Dispatchers.IO) {
            println("[$agentName]: Login attempt for: $email")
            try {
                val userList = SupabaseClient.client.postgrest["USER"]
                    .select { filter { eq("email", email) } }
                    .decodeList<User>()

                if (userList.isNotEmpty()) {
                    val user = userList.first()
                    println("[$agentName]: Login verified for ${user.full_name}")
                    refreshFcmTokenLifecycle(user)

                    val successMsg = CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",
                        messageType = "INFORM_USER_SESSION_ACTIVE",
                        content = mapOf("user" to user)
                    )
                    CarelyoMessageBroker.passMessage(successMsg)
                } else {
                    sendFailureNotification("LOGIN_UNAUTHORIZED", "No account found for: $email")
                }
            } catch (e: Exception) {
                sendFailureNotification("LOGIN_EXCEPTION", e.localizedMessage ?: "Connection error")
            }
        }
    }

    private fun refreshFcmTokenLifecycle(user: User) {
        scope.launch(Dispatchers.IO) {
            try {
                val updatedToken = FirebaseMessaging.getInstance().token.await()
                if (user.firebase_fcm_token != updatedToken) {
                    SupabaseClient.client.postgrest["USER"].update({
                        set("firebase_fcm_token", updatedToken)
                    }) {
                        filter { eq("UserID", user.UserID) }
                    }
                    println("[$agentName]: FCM token refreshed on login.")
                }
            } catch (e: Exception) {
                println("[$agentName]: Background FCM refresh skipped: ${e.localizedMessage}")
            }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        scope.launch(Dispatchers.IO) {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(email)
                    .await()

                val successMsg = CarelyoMessage(
                    sender = agentName,
                    receiver = "BROADCAST",
                    messageType = "PASSWORD_RESET_SENT",
                    content = mapOf("email" to email)
                )
                CarelyoMessageBroker.passMessage(successMsg)
            } catch (e: Exception) {
                sendFailureNotification("PASSWORD_RESET_FAILURE", e.localizedMessage ?: "Unknown error")
            }
        }
    }

    private fun sendFailureNotification(type: String, errorReason: String) {
        CarelyoMessageBroker.passMessage(
            CarelyoMessage(
                sender = agentName,
                receiver = "BROADCAST",
                messageType = type,
                content = mapOf("reason" to errorReason)
            )
        )
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        // Reserved for future system-wide logout or session expiry handling
    }
}