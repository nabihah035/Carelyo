package com.example.carelyo.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.*
import com.example.carelyo.data.session.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import java.text.SimpleDateFormat
import java.util.*

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

    private val _childAllergies = MutableLiveData<List<Allergie>>()
    val childAllergies: LiveData<List<Allergie>> = _childAllergies

    private val _unreadRemindersCount = MutableLiveData<Int>()
    val unreadRemindersCount: LiveData<Int> = _unreadRemindersCount

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var lastResetDay: String = ""

    // Cache to avoid re-fetching if already loaded
    private var cachedChildren: List<Child>? = null
    private var lastFetchTime: Long = 0
    private val CACHE_DURATION_MS = 30000L // 30 seconds cache

    fun loadDashboardData() {
        // Check and reset medication states for new day
        checkAndResetMedicationStates()

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

    /**
     * Check if a new day has started and reset medication states accordingly
     */
    private fun checkAndResetMedicationStates() {
        try {
            val today = dateFormat.format(Date())
            if (lastResetDay != today) {
                // Only clear entries from previous days, not today's
                val prefs = getApplication<Application>().getSharedPreferences("medication_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()

                prefs.all.keys.forEach { key ->
                    if (key.startsWith("med_") && !key.contains(today)) {
                        editor.remove(key)
                    }
                }
                editor.apply()

                lastResetDay = today
                println("[$TAG]: Cleaned up old medication entries for new day: $today")
            }
        } catch (e: Exception) {
            println("[$TAG]: Error resetting medication states: ${e.localizedMessage}")
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
                println("[$TAG]: Loading data for first child: ${children[0].full_name}")
                // Fetch data for the first child by default
                fetchAllDataForChild(children[0].ChildID)
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
                            eq("noti_status", "Unread")
                        }
                    }.decodeList<Reminder>()

                val count = reminders.size
                _unreadRemindersCount.postValue(count)

                // Save to SharedPreferences for badge visibility
                val prefs = getApplication<Application>().getSharedPreferences("carelyo_prefs", Context.MODE_PRIVATE)
                prefs.edit().putInt("unread_count", count).apply()

                println("[$TAG]: Unread reminders count: $count")
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

    fun fetchAllDataForChild(childId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear existing data immediately to avoid old child's data showing up in UI
                _upcomingVaccinations.postValue(emptyList())
                _upcomingMedications.postValue(emptyList())
                _upcomingAppointments.postValue(emptyList())
                _childAllergies.postValue(emptyList())

                println("[$TAG]: === FETCHING ALL DATA FOR CHILD ID: $childId ===")

                val deferredVaccinations = async { fetchUpcomingVaccinations(childId) }
                val deferredMedications = async { fetchUpcomingMedications(childId) }
                val deferredAppointments = async { fetchUpcomingAppointments(childId) }
                val deferredAllergies = async { fetchAllergies(childId) }

                val vaccinations = deferredVaccinations.await()
                val medications = deferredMedications.await()
                val appointments = deferredAppointments.await()
                val allergies = deferredAllergies.await()

                println("[$TAG]: === RESULTS FOR CHILD $childId ===")
                println("[$TAG]: Vaccinations: ${vaccinations.size}")
                vaccinations.forEach { println("[$TAG]:   - ${it.vaccineName} - ${it.dueDate}") }

                println("[$TAG]: Medications: ${medications.size}")
                medications.forEach { println("[$TAG]:   - ${it.medicationName} - ${it.scheduledTime}") }

                println("[$TAG]: Appointments: ${appointments.size}")
                appointments.forEach { println("[$TAG]:   - ${it.clinicName} - ${it.appointmentDate}") }

                println("[$TAG]: Allergies: ${allergies.size}")
                allergies.forEach { allergy ->
                    println("[$TAG]:   - ${allergy.allergy_name} - ${allergy.severity}")
                }

                _upcomingVaccinations.postValue(vaccinations)
                _upcomingMedications.postValue(medications)
                _upcomingAppointments.postValue(appointments)
                _childAllergies.postValue(allergies)

                _isLoading.postValue(false)
            } catch (e: Exception) {
                println("[$TAG]: Error fetching all data: ${e.localizedMessage}")
                e.printStackTrace()
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun fetchUpcomingVaccinations(childId: Int): List<UpcomingVaccination> {
        return try {
            println("[$TAG]: Fetching vaccinations for child $childId")

            // First, get the child's vaccines
            val childVaccines = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                .select {
                    filter {
                        eq("childid", childId)
                    }
                }.decodeList<ChildVaccine>()

            println("[$TAG]: Found ${childVaccines.size} child vaccine records")
            childVaccines.forEach { cv ->
                println("[$TAG]:   - VaccineID: ${cv.VaccineID}, Status: ${cv.status}")
            }

            // Get all vaccines
            val allVaccines = SupabaseClient.client.postgrest["VACCINATION"]
                .select()
                .decodeList<Vaccination>()

            println("[$TAG]: Found ${allVaccines.size} total vaccines in system")

            val upcomingVaccines = mutableListOf<UpcomingVaccination>()
            childVaccines.forEach { cv ->
                // Check if vaccine is pending or overdue
                if (cv.status == "Pending" || cv.status == "Overdue" || cv.status == "Complete") {
                    val vaccine = allVaccines.find { it.VaccineID == cv.VaccineID }
                    vaccine?.let {
                        val vaccination = UpcomingVaccination(
                            vaccineName = it.vaccine_name ?: "Unknown Vaccine",
                            dueDate = cv.administered_date,
                            childId = childId
                        )
                        upcomingVaccines.add(vaccination)
                        println("[$TAG]: Added vaccination: ${vaccination.vaccineName} - ${vaccination.dueDate}")
                    }
                }
            }
            upcomingVaccines.sortBy { it.dueDate }
            upcomingVaccines
        } catch (e: Exception) {
            println("[$TAG]: Error fetching vaccinations: ${e.localizedMessage}")
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun fetchUpcomingMedications(childId: Int): List<UpcomingMedication> {
        return try {
            println("[$TAG]: Fetching medications for child $childId")

            val medications = SupabaseClient.client.postgrest["MEDICATION"]
                .select {
                    filter {
                        eq("childid", childId)
                        eq("is_active", true)
                    }
                }.decodeList<Medication>()

            println("[$TAG]: Found ${medications.size} active medications")
            medications.forEach { med ->
                println("[$TAG]:   - ${med.medication_name}, MedID: ${med.MedID}")
            }

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

                    println("[$TAG]: Found ${schedules.size} schedules for medication ${med.medication_name}")

                    schedules.forEach { schedule ->
                        val upcomingMed = UpcomingMedication(
                            medicationName = med.medication_name ?: "Unknown",
                            dosage = med.dosage,
                            scheduledTime = schedule.scheduled_time,
                            childId = childId
                        )
                        upcomingMeds.add(upcomingMed)
                        println("[$TAG]: Added medication: ${upcomingMed.medicationName} at ${upcomingMed.scheduledTime}")
                    }
                }
            }
            upcomingMeds.sortBy { it.scheduledTime }
            upcomingMeds
        } catch (e: Exception) {
            println("[$TAG]: Error fetching medications: ${e.localizedMessage}")
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun fetchUpcomingAppointments(childId: Int): List<UpcomingAppointment> {
        return try {
            println("[$TAG]: Fetching appointments for child $childId")

            val today = dateFormat.format(Date())
            val appointments = SupabaseClient.client.postgrest["APPOINTMENT"]
                .select {
                    filter {
                        eq("childid", childId)
                        gte("appointment_date", today)
                    }
                }.decodeList<Appointment>()

            println("[$TAG]: Found ${appointments.size} upcoming appointments")
            appointments.forEach { app ->
                println("[$TAG]:   - ${app.clinic_name} on ${app.appointment_date}")
            }

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
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun fetchAllergies(childId: Int): List<Allergie> {
        return try {
            println("[$TAG]: Fetching allergies for child $childId")

            val allergies = SupabaseClient.client.postgrest["ALLERGIE"]
                .select {
                    filter {
                        eq("childid", childId)
                    }
                }.decodeList<Allergie>()

            println("[$TAG]: Found ${allergies.size} allergies for child $childId")
            allergies.forEach { allergy ->
                println("[$TAG]:   - ${allergy.allergy_name} (${allergy.severity})")
            }
            allergies
        } catch (e: Exception) {
            println("[$TAG]: Error fetching allergies: ${e.localizedMessage}")
            e.printStackTrace()
            emptyList()
        }
    }

    fun invalidateCache() {
        cachedChildren = null
        lastFetchTime = 0
    }
}