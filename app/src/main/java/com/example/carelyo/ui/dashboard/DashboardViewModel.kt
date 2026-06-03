package com.example.carelyo.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.carelyo.agent.core.ChildRecordAgent
import com.example.carelyo.agent.core.VaccinationMonitoringAgent
import com.example.carelyo.agent.infra.CarelyoAgent
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.agent.infra.CarelyoMessageBroker
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.session.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import io.github.jan.supabase.postgrest.postgrest

class DashboardViewModel(application: Application) : AndroidViewModel(application), CarelyoAgent {
    override val agentName: String = "DashboardViewModelAgent"
    private val sessionManager = SessionManager(application)

    private val childRecordAgent = ChildRecordAgent(viewModelScope)
    private val vaccinationAgent = VaccinationMonitoringAgent()

    private val _childrenList = MutableLiveData<List<Child>>()
    val childrenList: LiveData<List<Child>> = _childrenList

    private val _systemLogs = MutableLiveData<String>()
    val systemLogs: LiveData<String> = _systemLogs

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        CarelyoMessageBroker.registerAgent(this)
    }

    fun loadDashboardData() {
        _isLoading.value = true
        val currentUser = sessionManager.getUserSession()
        if (currentUser != null) {
            val triggerMsg = CarelyoMessage(
                sender = agentName,
                receiver = "ChildRecordAgent",
                messageType = "INFORM_USER_SESSION_ACTIVE",
                content = mapOf("user" to currentUser)
            )
            CarelyoMessageBroker.passMessage(triggerMsg)
        } else {
            _isLoading.value = false
        }
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        when (message.messageType) {
            "INFORM_CHILD_PROFILES_READY" -> {
                val list = message.content["children"] as? List<*>
                _childrenList.postValue(list?.filterIsInstance<Child>() ?: emptyList())
                _isLoading.postValue(false)
            }
            "INFORM_VACCINATION_AUDIT_REPORT" -> {
                val log = message.content["reportLog"] as? String ?: ""
                _systemLogs.postValue(log)
            }
        }
    }

    fun uploadFcmToken() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUser = sessionManager.getUserSession()
                if (currentUser != null && currentUser.UserID > 0) {
                    println("[$agentName]: Requesting token from Firebase engine...")

                    // Fetch the token directly from Google Play Services
                    val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()

                    println("[$agentName]: Token retrieved successfully. Uploading to Supabase for UserID: ${currentUser.UserID}")

                    // Cleaned up: Removed the broken package string line
                    com.example.carelyo.api.supabase.SupabaseClient.client.postgrest["USER"].update({
                        // If your User data class uses a different property name for the column,
                        // make sure this string matches your database column name exactly
                        set("firebase_fcm_token", token)
                    }) {
                        filter {
                            // Filters by the auto-incremented primary key
                            eq("UserID", currentUser.UserID)
                        }
                    }
                    println("[$agentName]: Supabase background token sync complete.")
                }
            } catch (e: Exception) {
                println("[$agentName]: Silent token upload skipped: ${e.localizedMessage}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        CarelyoMessageBroker.unregisterAgent(agentName)
    }
}