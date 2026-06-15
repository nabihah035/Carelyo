package com.example.carelyo.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.carelyo.agent.core.AuthenticationAgent
import com.example.carelyo.agent.infra.CarelyoAgent
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.agent.infra.CarelyoMessageBroker
import com.example.carelyo.data.entity.User
import com.example.carelyo.data.session.SessionManager

class AuthViewModel(application: Application) : AndroidViewModel(application), CarelyoAgent {
    override val agentName: String = "AuthViewModelAgent"
    private val sessionManager = SessionManager(application)
    private val authAgent = AuthenticationAgent(viewModelScope)

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    init {
        CarelyoMessageBroker.registerAgent(this)
    }

    fun login(email: String, passwordEntered: String) {
        _authState.value = AuthState.Loading
        authAgent.performUserLogin(email, passwordEntered)
    }

    fun register(email: String, passwordEntered: String, name: String, phone: String, role: String = "Parent") {
        _authState.value = AuthState.Loading
        authAgent.registerNewUser(email, passwordEntered, name, phone, role)
    }

    fun resetPassword(email: String) {
        _authState.value = AuthState.Loading
        authAgent.sendPasswordResetEmail(email)
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        // We use postValue instead of value because agents run operations on background IO threads
        when (message.messageType) {
            "INFORM_USER_SESSION_ACTIVE", "INFORM_REGISTRATION_SUCCESSFUL" -> {
                // The content map may deserialize User as a LinkedHashMap at runtime.
                // Reconstruct explicitly from the known fields instead of a direct cast.
                val raw = message.content["user"]
                val user: User? = when (raw) {
                    is User -> raw  // happy path — object survived the broker intact
                    is Map<*, *> -> {
                        // Broker serialized/deserialized it as a Map — reconstruct manually
                        @Suppress("UNCHECKED_CAST")
                        val m = raw as Map<String, Any?>
                        runCatching {
                            User(
                                UserID          = (m["UserID"] as? Number)?.toInt() ?: 0,
                                email           = m["email"] as? String ?: "",
                                password        = m["password"] as? String ?: "",
                                full_name       = m["full_name"] as? String,
                                phone_number    = m["phone_number"] as? String,
                                role            = m["role"] as? String,
                                firebase_fcm_token = m["firebase_fcm_token"] as? String,
                                created_at      = m["created_at"] as? String,
                                updated_at      = m["updated_at"] as? String
                            )
                        }.getOrNull()
                    }
                    else -> null
                }

                if (user != null) {
                    sessionManager.saveUserSession(user)
                    _authState.postValue(AuthState.Success(user))
                }
            }

            // 🔹 ADD THE NEW CONDITION RIGHT HERE
            "PASSWORD_RESET_SENT" -> {
                // 🔹 FIX: Use explicit named arguments to avoid positional parameter mismatches
                _authState.postValue(
                    AuthState.Success(
                        User(
                            UserID = 0,
                            firebase_fcm_token = null,
                            email = "",
                            password = "",
                            full_name = null,
                            role = null
                        )
                    )
                )
            }

            "LOGIN_UNAUTHORIZED", "REGISTRATION_FAILED", "LOGIN_EXCEPTION", "PASSWORD_RESET_FAILURE" -> {
                val reason = message.content["reason"] as? String ?: "Authentication failed"
                _authState.postValue(AuthState.Error(reason))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        CarelyoMessageBroker.unregisterAgent(agentName)
    }
}

sealed class AuthState {
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}