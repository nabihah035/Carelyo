package com.example.carelyo.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.Medication
import com.example.carelyo.data.entity.MedicationSchedule
import com.example.carelyo.data.entity.Appointment
import com.example.carelyo.data.session.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import com.example.carelyo.data.entity.UpcomingMedication
import java.text.SimpleDateFormat
import java.util.*
import com.example.carelyo.data.entity.UpcomingVaccination
import com.example.carelyo.data.entity.UpcomingAppointment
import com.example.carelyo.data.entity.Reminder

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DashboardViewModel"
    private val sessionManager = SessionManager(application)

    private val _childrenList = MutableLiveData<List<Child>>()
    val childrenList: LiveData<List<Child>> = _childrenList

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

    private val _unreadRemindersCount = MutableLiveData<Int>()
    val unreadRemindersCount: LiveData<Int> = _unreadRemindersCount

    // Cache to avoid re-fetching if already loaded
    private var cachedChildren: List<Child>? = null
    private var lastFetchTime: Long = 0
    private val CACHE_DURATION_MS = 30000L // 30 seconds cache

    fun loadDashboardData() {
        val currentTime = System.currentTimeMillis()
        if (cachedChildren != null && (currentTime - lastFetchTime) < CACHE_DURATION_MS) {
            _childrenList.postValue(cachedChildren)
            _isLoading.postValue(false)
            return
        }

        _isLoading.value = true
        val currentUser = sessionManager.getUserSession()

        if (currentUser == null) {
            println("[$TAG]: No session found in SharedPrefs.")
            _isLoading.postValue(false)
            _errorMessage.postValue("No active session found")
            return
        }

        if (currentUser.UserID <= 0) {
            println("[$TAG]: UserID=0 detected — attempting session repair for ${currentUser.email}")
            repairSessionAndContinue(currentUser.email)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("[$TAG]: Fetching children for Parent_ID: ${currentUser.UserID}")
                fetchChildrenFromSupabase(currentUser.UserID)
                fetchUnreadRemindersCount(currentUser.UserID)
            } catch (e: Exception) {
                println("[$TAG]: Error in loadDashboardData: ${e.localizedMessage}")
                e.printStackTrace()
                _isLoading.postValue(false)
                _errorMessage.postValue("Error loading data: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun fetchChildrenFromSupabase(parentId: Int) {
        try {
            println("[$TAG]: Querying CHILD table for Parent_ID: $parentId")

            val children = try {
                SupabaseClient.client.postgrest["CHILD"]
                    .select {
                        filter {
                            eq("parent_id", parentId)
                        }
                    }.decodeList<Child>()
            } catch (e: Exception) {
                println("[$TAG]: Trying with exact case...")
                SupabaseClient.client.postgrest["CHILD"]
                    .select {
                        filter {
                            eq("\"Parent_ID\"", parentId)
                        }
                    }.decodeList<Child>()
            }

            println("[$TAG]: Found ${children.size} children")
            children.forEach { child ->
                println("[$TAG]: Child: ${child.full_name}, ID: ${child.ChildID}")
            }

            cachedChildren = children
            lastFetchTime = System.currentTimeMillis()

            _childrenList.postValue(children)
            _isLoading.postValue(false)

            if (children.isNotEmpty()) {
                println("[$TAG]: Fetching data for first child: ${children[0].full_name}")
                fetchUpcomingDataForChild(children[0].ChildID)
            } else {
                println("[$TAG]: No children found for this parent")
                _errorMessage.postValue("No children registered for this account")
            }
        } catch (e: Exception) {
            println("[$TAG]: Error fetching children: ${e.localizedMessage}")
            e.printStackTrace()
            _isLoading.postValue(false)
            _errorMessage.postValue("Failed to load children data: ${e.localizedMessage}")
        }
    }

    private fun fetchUnreadRemindersCount(parentId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reminders = SupabaseClient.client.postgrest["REMINDER"]
                    .select {
                        filter {
                            eq("parentid", parentId)
                            eq("is_sent", false)
                        }
                    }.decodeList<Reminder>()

                _unreadRemindersCount.postValue(reminders.size)
            } catch (e: Exception) {
                println("[$TAG]: Error fetching reminders count: ${e.localizedMessage}")
                _unreadRemindersCount.postValue(0)
            }
        }
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
                    println("[$TAG]: Session repaired — UserID is now ${repairedUser.UserID}")
                    fetchChildrenFromSupabase(repairedUser.UserID)
                } else {
                    println("[$TAG]: Session repair failed — no valid user found in DB for $email")
                    _isLoading.postValue(false)
                    _errorMessage.postValue("Session could not be restored. Please log in again.")
                }
            } catch (e: Exception) {
                println("[$TAG]: Session repair exception: ${e.localizedMessage}")
                _isLoading.postValue(false)
                _errorMessage.postValue("Network error during session restore. Please retry.")
            }
        }
    }

    fun fetchUpcomingDataForChild(childId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("[$TAG]: Fetching upcoming data for child: $childId")

                val deferredVaccinations = async { fetchUpcomingVaccinations(childId) }
                val deferredMedications = async { fetchUpcomingMedications(childId) }
                val deferredAppointments = async { fetchUpcomingAppointments(childId) }

                val vaccinations = deferredVaccinations.await()
                val medications = deferredMedications.await()
                val appointments = deferredAppointments.await()

                println("[$TAG]: Found ${vaccinations.size} vaccines, ${medications.size} medications, ${appointments.size} appointments")

                _upcomingVaccinations.postValue(vaccinations)
                _upcomingMedications.postValue(medications)
                _upcomingAppointments.postValue(appointments)

                _isLoading.postValue(false)
            } catch (e: Exception) {
                println("[$TAG]: Error fetching upcoming data: ${e.localizedMessage}")
                e.printStackTrace()
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun fetchUpcomingVaccinations(childId: Int): List<UpcomingVaccination> {
        return try {
            val childVaccines = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                .select { filter { eq("childid", childId) } }
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
            upcomingVaccines
        } catch (e: Exception) {
            println("[$TAG]: Error fetching vaccinations: ${e.localizedMessage}")
            emptyList()
        }
    }

    private suspend fun fetchUpcomingMedications(childId: Int): List<UpcomingMedication> {
        return try {
            val medications = SupabaseClient.client.postgrest["MEDICATION"]
                .select {
                    filter {
                        eq("childid", childId)
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
                        .select { filter { eq("medid", med.MedID) } }
                        .decodeList<MedicationSchedule>()

                    schedules.forEach { schedule ->
                        upcomingMeds.add(
                            UpcomingMedication(
                                medicationName = med.medication_name ?: "Unknown",
                                dosage = med.dosage,
                                scheduledTime = schedule.scheduled_time,
                                childId = childId
                            )
                        )
                    }
                }
            }
            upcomingMeds.sortBy { it.scheduledTime }
            upcomingMeds
        } catch (e: Exception) {
            println("[$TAG]: Error fetching medications: ${e.localizedMessage}")
            emptyList()
        }
    }

    private suspend fun fetchUpcomingAppointments(childId: Int): List<UpcomingAppointment> {
        return try {
            val today = dateFormat.format(Date())
            val appointments = SupabaseClient.client.postgrest["APPOINTMENT"]
                .select {
                    filter {
                        eq("childid", childId)
                        gte("appointment_date", today)
                        eq("status", "Upcoming")
                    }
                }.decodeList<Appointment>()

            appointments
                .sortedBy { it.appointment_date }
                .map { appointment ->
                    UpcomingAppointment(
                        clinicName = appointment.clinic_name,
                        appointmentDate = appointment.appointment_date,
                        appointmentTime = appointment.appointment_time,
                        childId = childId
                    )
                }
        } catch (e: Exception) {
            println("[$TAG]: Error fetching appointments: ${e.localizedMessage}")
            emptyList()
        }
    }

    fun fetchChildGrowthData(childId: Int, callback: (weight: Float, height: Float) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            callback(0f, 0f)
        }
    }

    fun invalidateCache() {
        cachedChildren = null
        lastFetchTime = 0
    }
}