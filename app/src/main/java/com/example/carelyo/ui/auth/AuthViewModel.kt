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
import com.example.carelyo.utils.EmailService
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application), CarelyoAgent {
    override val agentName: String = "AuthViewModelAgent"
    private val sessionManager = SessionManager(application)
    private val authAgent = AuthenticationAgent(viewModelScope)

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    // Store OTP for verification
    companion object {
        private var storedOtp: String = ""
        private var storedEmail: String = ""
    }

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

    // MODIFIED: Send OTP via email instead of Firebase reset
    fun sendPasswordResetOtp(email: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                // Generate 6-digit OTP
                val otp = generateOtp()
                storedOtp = otp
                storedEmail = email

                // Build HTML email
                val htmlBody = EmailService.buildPasswordResetOtpHtml(email, otp)

                // Send email
                val result = EmailService.sendHtmlEmail(email, "Carelyo - Password Reset OTP", htmlBody)

                if (result.isSuccess) {
                    _authState.postValue(AuthState.OtpSent(email))
                } else {
                    _authState.postValue(AuthState.Error("Failed to send OTP. Please try again."))
                }
            } catch (e: Exception) {
                _authState.postValue(AuthState.Error("Error: ${e.localizedMessage}"))
            }
        }
    }

    // NEW: Verify OTP
    fun verifyOtp(email: String, otp: String) {
        if (email == storedEmail && otp == storedOtp) {
            _authState.value = AuthState.OtpVerified(email)
        } else {
            _authState.value = AuthState.Error("Invalid OTP. Please try again.")
        }
    }

    // NEW: Reset password with new password
    fun resetPasswordWithOtp(email: String, newPassword: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val success = authAgent.updateUserPassword(email, newPassword)
                if (success) {
                    _authState.postValue(AuthState.PasswordResetSuccess("Password reset successfully!"))
                } else {
                    _authState.postValue(AuthState.Error("Failed to reset password. Please try again."))
                }
            } catch (e: Exception) {
                _authState.postValue(AuthState.Error("Error: ${e.localizedMessage}"))
            }
        }
    }

    private fun generateOtp(): String {
        return (100000..999999).random().toString()
    }

    // Keep existing resetPassword method for compatibility if needed
    fun resetPassword(email: String) {
        sendPasswordResetOtp(email)
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        when (message.messageType) {
            "INFORM_USER_SESSION_ACTIVE", "INFORM_REGISTRATION_SUCCESSFUL" -> {
                val raw = message.content["user"]
                val user: User? = when (raw) {
                    is User -> raw
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val m = raw as Map<String, Any?>
                        runCatching {
                            User(
                                UserID = (m["UserID"] as? Number)?.toInt() ?: 0,
                                email = m["email"] as? String ?: "",
                                password = m["password"] as? String ?: "",
                                full_name = m["full_name"] as? String,
                                phone_number = m["phone_number"] as? String,
                                role = m["role"] as? String,
                                created_at = m["created_at"] as? String,
                                updated_at = m["updated_at"] as? String
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

    // NEW STATES FOR OTP FLOW
    data class OtpSent(val email: String) : AuthState()
    data class OtpVerified(val email: String) : AuthState()
    data class PasswordResetSuccess(val message: String) : AuthState()
}