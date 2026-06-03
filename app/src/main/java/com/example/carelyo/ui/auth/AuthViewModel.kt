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

    fun login(email: String) {
        _authState.value = AuthState.Loading
        authAgent.performUserLogin(email)
    }

    fun register(email: String, name: String, phone: String, role: String = "Parent") {
        _authState.value = AuthState.Loading
        authAgent.registerNewUser(email, name, phone, role)
    }

    fun resetPassword(email: String) {
        _authState.value = AuthState.Loading
        authAgent.sendPasswordResetEmail(email)
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        // We use postValue instead of value because agents run operations on background IO threads
        when (message.messageType) {
            "INFORM_USER_SESSION_ACTIVE", "INFORM_REGISTRATION_SUCCESSFUL" -> {
                val user = message.content["user"] as? User
                if (user != null) {
                    sessionManager.saveUserSession(user)
                    _authState.postValue(AuthState.Success(user))
                }
            }

            // 🔹 ADD THE NEW CONDITION RIGHT HERE
            "PASSWORD_RESET_SENT" -> {
                // Uses a dummy/blank User payload just to flag the success architecture channel cleanly
                _authState.postValue(AuthState.Success(User(0, null, "", null, null, null)))
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