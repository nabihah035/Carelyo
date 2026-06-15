package com.example.carelyo.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.carelyo.agent.infra.CarelyoAgent
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.agent.infra.CarelyoMessageBroker
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.Medication
import com.example.carelyo.data.entity.MedicationSchedule
import com.example.carelyo.data.entity.Appointment
import com.example.carelyo.data.session.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel(application: Application) : AndroidViewModel(application), CarelyoAgent {
    override val agentName: String = "DashboardViewModelAgent"
    private val sessionManager = SessionManager(application)

    private val _childrenList = MutableLiveData<List<Child>>()
    val childrenList: LiveData<List<Child>> = _childrenList
    private val _systemLogs = MutableLiveData<String>()
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _upcomingVaccinations = MutableLiveData<List<UpcomingVaccination>>()
    val upcomingVaccinations: LiveData<List<UpcomingVaccination>> = _upcomingVaccinations

    private val _upcomingMedications = MutableLiveData<List<UpcomingMedication>>()
    val upcomingMedications: LiveData<List<UpcomingMedication>> = _upcomingMedications

    private val _upcomingAppointments = MutableLiveData<List<UpcomingAppointment>>()
    val upcomingAppointments: LiveData<List<UpcomingAppointment>> = _upcomingAppointments

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        CarelyoMessageBroker.registerAgent(this)
    }

    fun loadDashboardData() {
        _isLoading.value = true
        val currentUser = sessionManager.getUserSession()

        if (currentUser == null) {
            println("[$agentName]: No session found in SharedPrefs.")
            _isLoading.postValue(false)
            return
        }

        if (currentUser.UserID <= 0) {
            // UserID=0 means the session was saved before the DB returned a real ID.
            // Don't evict — try to repair it by re-fetching from Supabase by email.
            println("[$agentName]: UserID=0 detected — attempting session repair for ${currentUser.email}")
            repairSessionAndContinue(currentUser.email)
            return
        }

        val triggerMsg = CarelyoMessage(
            sender = agentName,
            receiver = "ChildRecordAgent",
            messageType = "INFORM_USER_SESSION_ACTIVE",
            content = mapOf("user" to currentUser)
        )
        CarelyoMessageBroker.passMessage(triggerMsg)
    }

    private fun repairSessionAndContinue(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userList = SupabaseClient.client.postgrest["USER"]
                    .select { filter { eq("email", email) } }
                    .decodeList<com.example.carelyo.data.entity.User>()

                val repairedUser = userList.firstOrNull()
                if (repairedUser != null && repairedUser.UserID > 0) {
                    sessionManager.saveUserSession(repairedUser)
                    println("[$agentName]: Session repaired — UserID is now ${repairedUser.UserID}")

                    val triggerMsg = CarelyoMessage(
                        sender = agentName,
                        receiver = "ChildRecordAgent",
                        messageType = "INFORM_USER_SESSION_ACTIVE",
                        content = mapOf("user" to repairedUser)
                    )
                    CarelyoMessageBroker.passMessage(triggerMsg)
                } else {
                    // DB also has no valid user — only now is it safe to ask for re-login
                    println("[$agentName]: Session repair failed — no valid user found in DB for $email")
                    _isLoading.postValue(false)
                    _errorMessage.postValue("Session could not be restored. Please log in again.")
                }
            } catch (e: Exception) {
                println("[$agentName]: Session repair exception: ${e.localizedMessage}")
                _isLoading.postValue(false)
                _errorMessage.postValue("Network error during session restore. Please retry.")
            }
        }
    }

    fun fetchUpcomingDataForChild(childId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fetchUpcomingVaccinations(childId)
                fetchUpcomingMedications(childId)
                fetchUpcomingAppointments(childId)
            } catch (e: Exception) {
                println("[DashboardViewModel]: Error fetching upcoming data: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun fetchUpcomingVaccinations(childId: Int) {
        try {
            val childVaccines = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                .select { filter { eq("ChildID", childId) } }
                .decodeList<com.example.carelyo.data.entity.ChildVaccine>()

            val allVaccines = SupabaseClient.client.postgrest["VACCINATION"]
                .select()
                .decodeList<com.example.carelyo.data.entity.Vaccination>()

            val upcomingVaccines = mutableListOf<UpcomingVaccination>()
            childVaccines.forEach { cv ->
                if (cv.status == "Pending" || cv.status == "Overdue") {
                    val vaccine = allVaccines.find { it.VaccineID == cv.VaccineID }
                    vaccine?.let {
                        upcomingVaccines.add(
                            UpcomingVaccination(
                                vaccineName = it.vaccine_name ?: "Unknown Vaccine",
                                dueDate = cv.administered_date,
                                childId = childId
                            )
                        )
                    }
                }
            }
            upcomingVaccines.sortBy { it.dueDate }
            _upcomingVaccinations.postValue(upcomingVaccines)
        } catch (e: Exception) {
            println("[DashboardViewModel]: Error fetching vaccinations: ${e.localizedMessage}")
            _upcomingVaccinations.postValue(emptyList())
        }
    }

    private suspend fun fetchUpcomingMedications(childId: Int) {
        try {
            val medications = SupabaseClient.client.postgrest["MEDICATION"]
                .select {
                    filter {
                        eq("ChildID", childId)
                        eq("is_active", true)
                    }
                }.decodeList<Medication>()

            val upcomingMeds = mutableListOf<UpcomingMedication>()
            val today = Date()

            medications.forEach { med ->
                val endDate = med.end_date?.let {
                    try { dateFormat.parse(it) } catch (_: Exception) { null }
                }
                if (endDate == null || endDate.after(today)) {
                    val schedules = SupabaseClient.client.postgrest["MEDICATION_SCHEDULE"]
                        .select { filter { eq("MedID", med.MedID) } }
                        .decodeList<MedicationSchedule>()

                    schedules.forEach { schedule ->
                        upcomingMeds.add(
                            UpcomingMedication(
                                medicationName = med.medication_name ?: "Unknown",
                                dosage = med.dosage,
                                // BUG FIX #4: scheduled_time is TIMESTAMPTZ from DB,
                                // returns as "2026-06-05T08:00:00+08:00". Pass it as-is;
                                // HomeFragment.formatTime() now handles ISO-8601.
                                scheduledTime = schedule.scheduled_time,
                                childId = childId
                            )
                        )
                    }
                }
            }
            upcomingMeds.sortBy { it.scheduledTime }
            _upcomingMedications.postValue(upcomingMeds)
        } catch (e: Exception) {
            println("[DashboardViewModel]: Error fetching medications: ${e.localizedMessage}")
            _upcomingMedications.postValue(emptyList())
        }
    }

    private suspend fun fetchUpcomingAppointments(childId: Int) {
        try {
            val today = dateFormat.format(Date())
            val appointments = SupabaseClient.client.postgrest["APPOINTMENT"]
                .select {
                    filter {
                        eq("ChildID", childId)
                        gte("appointment_date", today)
                        // BUG FIX #5: Only show appointments that are actually upcoming
                        eq("status", "Upcoming")
                    }
                }.decodeList<Appointment>()

            val upcomingApps = appointments
                .sortedBy { it.appointment_date }
                .map { appointment ->
                    UpcomingAppointment(
                        clinicName = appointment.clinic_name,
                        appointmentDate = appointment.appointment_date,
                        appointmentTime = appointment.appointment_time,
                        childId = childId
                    )
                }
            _upcomingAppointments.postValue(upcomingApps)
        } catch (e: Exception) {
            println("[DashboardViewModel]: Error fetching appointments: ${e.localizedMessage}")
            _upcomingAppointments.postValue(emptyList())
        }
    }

    fun fetchChildGrowthData(childId: Int, callback: (weight: Float, height: Float) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Growth table not yet implemented — return zeroes so UI shows "Not recorded"
            callback(0f, 0f)
        }
    }

    override fun processIncomingMessage(message: CarelyoMessage) {
        when (message.messageType) {
            // BUG FIX #1: Now received because ChildRecordAgent broadcasts to BROADCAST
            // instead of sending directly to VaccinationMonitoringAgent only.
            "INFORM_CHILD_PROFILES_READY" -> {
                val list = message.content["children"] as? List<*>
                _childrenList.postValue(list?.filterIsInstance<Child>() ?: emptyList())
                _isLoading.postValue(false)
            }
            "INFORM_VACCINATION_AUDIT_REPORT" -> {
                val log = message.content["reportLog"] as? String ?: ""
                _systemLogs.postValue(log)
            }
            "CHILD_FETCH_ERROR" -> {
                val reason = message.content["reason"] as? String ?: "Unknown error"
                println("[$agentName]: Child fetch failed — $reason")
                _isLoading.postValue(false)
                _errorMessage.postValue(reason)
            }
        }
    }

    fun uploadFcmToken() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUser = sessionManager.getUserSession()
                if (currentUser != null && currentUser.UserID > 0) {
                    val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                    SupabaseClient.client.postgrest["USER"].update({
                        set("firebase_fcm_token", token)
                    }) {
                        filter { eq("UserID", currentUser.UserID) }
                    }
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