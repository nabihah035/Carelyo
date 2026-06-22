package com.example.carelyo.agent.core

import com.example.carelyo.agent.infra.CarelyoAgent
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.agent.infra.CarelyoMessageBroker
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.User
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AuthenticationAgent(private val scope: CoroutineScope) : CarelyoAgent {
    override val agentName: String = "AuthenticationAgent"

    init {
        CarelyoMessageBroker.registerAgent(this)
    }

    fun registerNewUser(email: String, password: String, fullName: String, phoneNumber: String, role: String) {
        scope.launch(Dispatchers.IO) {
            println("[$agentName]: Handling secure registration flow for: $email")
            try {
                val newUser = User(
                    email = email,
                    password = password,
                    full_name = fullName,
                    phone_number = phoneNumber,
                    role = role
                )

                val savedUser = SupabaseClient.client.postgrest["USER"]
                    .insert(newUser) { select() }
                    .decodeSingle<User>()

                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = agentName,
                        receiver = "BROADCAST",
                        messageType = "INFORM_REGISTRATION_SUCCESSFUL",
                        content = mapOf("user" to savedUser)
                    )
                )
            } catch (e: Exception) {
                sendFailureNotification("REGISTRATION_FAILED", e.localizedMessage ?: "Database insertion failure.")
            }
        }
    }

    fun performUserLogin(email: String, passwordEntered: String) {
        scope.launch(Dispatchers.IO) {
            println("[$agentName]: Login validation request for parameter key: $email")
            try {
                val userList = SupabaseClient.client.postgrest["USER"]
                    .select { filter { eq("email", email) } }
                    .decodeList<User>()

                if (userList.isNotEmpty()) {
                    val user = userList.first()

                    if (user.password == passwordEntered) {
                        println("[$agentName]: Secure login matching verified for ${user.full_name}")

                        val successMsg = CarelyoMessage(
                            sender = agentName,
                            receiver = "BROADCAST",
                            messageType = "INFORM_USER_SESSION_ACTIVE",
                            content = mapOf("user" to user)
                        )
                        CarelyoMessageBroker.passMessage(successMsg)
                    } else {
                        sendFailureNotification("LOGIN_UNAUTHORIZED", "Invalid password security credential matching failed.")
                    }
                } else {
                    sendFailureNotification("LOGIN_UNAUTHORIZED", "No database profile tied to matching address string: $email")
                }
            } catch (e: Exception) {
                sendFailureNotification("LOGIN_EXCEPTION", e.localizedMessage ?: "Network query timed out.")
            }
        }
    }

    // NEW: Update user password
    suspend fun updateUserPassword(email: String, newPassword: String): Boolean {
        return try {
            val result = SupabaseClient.client.postgrest["USER"]
                .update(
                    mapOf("password" to newPassword)
                ) {
                    filter { eq("email", email) }
                }
            true
        } catch (e: Exception) {
            println("[$agentName]: Password update failed: ${e.localizedMessage}")
            false
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