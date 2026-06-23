package com.example.carelyo.ui.health

import android.app.Application
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
import java.text.SimpleDateFormat
import java.util.*

class HealthRecordsViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "HealthRecordsViewModel"
    private val sessionManager = SessionManager(application)

    private val _childrenList = MutableLiveData<List<Child>>()
    val childrenList: LiveData<List<Child>> = _childrenList

    private val _allergies = MutableLiveData<List<Allergie>>()
    val allergies: LiveData<List<Allergie>> = _allergies

    private val _medicalHistory = MutableLiveData<List<MedicalHistory>>()
    val medicalHistory: LiveData<List<MedicalHistory>> = _medicalHistory

    private val _doctorVisits = MutableLiveData<List<DoctorVisit>>()
    val doctorVisits: LiveData<List<DoctorVisit>> = _doctorVisits

    private val _childVaccines = MutableLiveData<List<ChildVaccine>>()
    val childVaccines: LiveData<List<ChildVaccine>> = _childVaccines

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _filteredAllergies = MutableLiveData<List<Allergie>>()
    val filteredAllergies: LiveData<List<Allergie>> = _filteredAllergies

    private val _filteredMedicalHistory = MutableLiveData<List<MedicalHistory>>()
    val filteredMedicalHistory: LiveData<List<MedicalHistory>> = _filteredMedicalHistory

    private val _filteredDoctorVisits = MutableLiveData<List<DoctorVisit>>()
    val filteredDoctorVisits: LiveData<List<DoctorVisit>> = _filteredDoctorVisits

    private var allAllergies: List<Allergie> = emptyList()
    private var allMedicalHistory: List<MedicalHistory> = emptyList()
    private var allDoctorVisits: List<DoctorVisit> = emptyList()
    private var isLoadingData = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun loadHealthData() {
        // Prevent multiple simultaneous loads
        if (isLoadingData) {
            println("[$TAG]: Already loading data, skipping duplicate call")
            return
        }

        isLoadingData = true
        _isLoading.value = true
        val currentUser = sessionManager.getUserSession()

        if (currentUser == null) {
            println("[$TAG]: No session found")
            _isLoading.postValue(false)
            _errorMessage.postValue("No active session found")
            isLoadingData = false
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("[$TAG]: Loading health data for user: ${currentUser.UserID}")

                val children = fetchChildren(currentUser.UserID)
                _childrenList.postValue(children)

                if (children.isNotEmpty()) {
                    val allAllergiesList = mutableListOf<Allergie>()
                    val allMedicalHistoryList = mutableListOf<MedicalHistory>()
                    val allDoctorVisitsList = mutableListOf<DoctorVisit>()
                    val allChildVaccinesList = mutableListOf<ChildVaccine>()

                    children.forEach { child ->
                        try {
                            val allergies = fetchAllergies(child.ChildID)
                            val medicalHistory = fetchMedicalHistory(child.ChildID)
                            val doctorVisits = fetchDoctorVisits(child.ChildID)
                            val childVaccines = fetchChildVaccines(child.ChildID)

                            allAllergiesList.addAll(allergies)
                            allMedicalHistoryList.addAll(medicalHistory)
                            allDoctorVisitsList.addAll(doctorVisits)
                            allChildVaccinesList.addAll(childVaccines)
                        } catch (e: Exception) {
                            println("[$TAG]: Error processing child ${child.ChildID}: ${e.message}")
                        }
                    }

                    allAllergies = allAllergiesList
                    allMedicalHistory = allMedicalHistoryList
                    allDoctorVisits = allDoctorVisitsList

                    _allergies.postValue(allAllergiesList)
                    _filteredAllergies.postValue(allAllergiesList)
                    _medicalHistory.postValue(allMedicalHistoryList)
                    _filteredMedicalHistory.postValue(allMedicalHistoryList)
                    _doctorVisits.postValue(allDoctorVisitsList)
                    _filteredDoctorVisits.postValue(allDoctorVisitsList)
                    _childVaccines.postValue(allChildVaccinesList)

                    println("[$TAG]: Loaded ${allAllergiesList.size} allergies, ${allMedicalHistoryList.size} medical records, ${allDoctorVisitsList.size} doctor visits, ${allChildVaccinesList.size} vaccines")
                }

                _isLoading.postValue(false)
                isLoadingData = false
            } catch (e: Exception) {
                println("[$TAG]: Error loading health data: ${e.localizedMessage}")
                e.printStackTrace()
                _isLoading.postValue(false)
                _errorMessage.postValue("Error loading data: ${e.localizedMessage}")
                isLoadingData = false
            }
        }
    }

    private suspend fun fetchChildren(parentId: Int): List<Child> {
        return try {
            val children = SupabaseClient.client.postgrest["CHILD"]
                .select {
                    filter {
                        eq("parent_id", parentId)
                    }
                }.decodeList<Child>()

            println("[$TAG]: Found ${children.size} children")
            children
        } catch (e: Exception) {
            println("[$TAG]: Error fetching children: ${e.localizedMessage}")
            emptyList()
        }
    }

    private suspend fun fetchAllergies(childId: Int): List<Allergie> {
        return try {
            val allergies = SupabaseClient.client.postgrest["ALLERGIE"]
                .select {
                    filter {
                        eq("childid", childId)
                    }
                }.decodeList<Allergie>()

            println("[$TAG]: Found ${allergies.size} allergies for child $childId")
            allergies
        } catch (e: Exception) {
            println("[$TAG]: Error fetching allergies: ${e.localizedMessage}")
            emptyList()
        }
    }

    private suspend fun fetchMedicalHistory(childId: Int): List<MedicalHistory> {
        return try {
            val history = SupabaseClient.client.postgrest["MEDICAL_HISTORY"]
                .select {
                    filter {
                        eq("childid", childId)
                    }
                }.decodeList<MedicalHistory>()

            println("[$TAG]: Found ${history.size} medical history records for child $childId")
            history
        } catch (e: Exception) {
            println("[$TAG]: Error fetching medical history: ${e.localizedMessage}")
            emptyList()
        }
    }

    private suspend fun fetchDoctorVisits(childId: Int): List<DoctorVisit> {
        return try {
            val visits = SupabaseClient.client.postgrest["DOCTOR_VISIT"]
                .select {
                    filter {
                        eq("childid", childId)
                    }
                }.decodeList<DoctorVisit>()

            println("[$TAG]: Found ${visits.size} doctor visits for child $childId")
            visits
        } catch (e: Exception) {
            println("[$TAG]: Error fetching doctor visits: ${e.localizedMessage}")
            emptyList()
        }
    }

    private suspend fun fetchChildVaccines(childId: Int): List<ChildVaccine> {
        return try {
            val vaccines = SupabaseClient.client.postgrest["CHILD_VACCINE"]
                .select {
                    filter {
                        eq("childid", childId)
                    }
                }.decodeList<ChildVaccine>()

            println("[$TAG]: Found ${vaccines.size} vaccines for child $childId")
            vaccines
        } catch (e: Exception) {
            println("[$TAG]: Error fetching child vaccines: ${e.localizedMessage}")
            emptyList()
        }
    }

    fun deleteAllergy(allergieId: Int, callback: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.postgrest["ALLERGIE"]
                    .delete {
                        filter {
                            eq("allergieid", allergieId)
                        }
                    }

                allAllergies = allAllergies.filter { it.AllergieID != allergieId }
                _allergies.postValue(allAllergies)
                _filteredAllergies.postValue(allAllergies)

                println("[$TAG]: Deleted allergy $allergieId")
                kotlinx.coroutines.withContext(Dispatchers.Main) { callback(true) }
            } catch (e: Exception) {
                println("[$TAG]: Error deleting allergy: ${e.localizedMessage}")
                kotlinx.coroutines.withContext(Dispatchers.Main) { callback(false) }
            }
        }
    }

    fun addAllergy(
        childId: Int,
        allergyName: String,
        allergyType: String,
        severity: String,
        notes: String?,
        callback: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newAllergy = AllergieInsert(
                    ChildID = childId,
                    allergy_name = allergyName,
                    allergy_type = allergyType,
                    severity = severity,
                    notes = notes
                )

                val result = SupabaseClient.client.postgrest["ALLERGIE"]
                    .insert(newAllergy) { select() }
                    .decodeList<Allergie>()
                    .firstOrNull()

                if (result != null) {
                    allAllergies = allAllergies + result
                    _allergies.postValue(allAllergies)
                    _filteredAllergies.postValue(allAllergies)
                    println("[$TAG]: Added allergy: ${result.allergy_name}")
                    kotlinx.coroutines.withContext(Dispatchers.Main) { callback(true) }
                } else {
                    kotlinx.coroutines.withContext(Dispatchers.Main) { callback(false) }
                }
            } catch (e: Exception) {
                println("[$TAG]: Error adding allergy: ${e.localizedMessage}")
                kotlinx.coroutines.withContext(Dispatchers.Main) { callback(false) }
            }
        }
    }

    fun addMedicalRecord(
        childId: Int,
        doctorName: String,
        clinicName: String,
        diagnosis: String,
        notes: String,
        recordType: String,
        callback: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUser = sessionManager.getUserSession()
                if (currentUser == null) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) { callback(false) }
                    return@launch
                }

                val today = dateFormat.format(Date())

                // Insert into MEDICAL_HISTORY table
                val newMedicalHistory = MedicalHistoryInsert(
                    ChildID = childId,
                    condition_name = diagnosis,
                    diagnosis_date = today,
                    treatment = recordType,
                    notes = notes
                )

                println("[$TAG]: Inserting medical history: $newMedicalHistory")

                val historyResult = SupabaseClient.client.postgrest["MEDICAL_HISTORY"]
                    .insert(newMedicalHistory) { select() }
                    .decodeList<MedicalHistory>()
                    .firstOrNull()

                if (historyResult != null) {
                    // Also insert into DOCTOR_VISIT table for additional details
                    val newDoctorVisit = DoctorVisitInsert(
                        ChildID = childId,
                        visit_date = today,
                        clinic_name = clinicName,
                        doctor_name = doctorName,
                        raw_notes = notes,
                        ai_summary = "Medical record: $diagnosis",
                        summary_language = "en"
                    )

                    println("[$TAG]: Inserting doctor visit: $newDoctorVisit")

                    SupabaseClient.client.postgrest["DOCTOR_VISIT"]
                        .insert(newDoctorVisit) { select() }
                        .decodeList<DoctorVisit>()
                        .firstOrNull()

                    // Update local lists
                    allMedicalHistory = allMedicalHistory + historyResult
                    _medicalHistory.postValue(allMedicalHistory)
                    _filteredMedicalHistory.postValue(allMedicalHistory)

                    // Refresh doctor visits for this child
                    val updatedDoctorVisits = fetchDoctorVisits(childId)
                    allDoctorVisits = allDoctorVisits + updatedDoctorVisits
                    _doctorVisits.postValue(allDoctorVisits)
                    _filteredDoctorVisits.postValue(allDoctorVisits)

                    println("[$TAG]: Added medical record: ${historyResult.condition_name}")
                    kotlinx.coroutines.withContext(Dispatchers.Main) { callback(true) }
                } else {
                    println("[$TAG]: Failed to insert medical history")
                    kotlinx.coroutines.withContext(Dispatchers.Main) { callback(false) }
                }
            } catch (e: Exception) {
                println("[$TAG]: Error adding medical record: ${e.localizedMessage}")
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) { callback(false) }
            }
        }
    }

    fun getChildName(childId: Int): String? {
        return _childrenList.value?.find { it.ChildID == childId }?.full_name
    }

    fun getDoctorVisitForHistory(history: MedicalHistory): DoctorVisit? {
        return allDoctorVisits.find {
            it.ChildID == history.ChildID &&
                    it.visit_date == history.diagnosis_date &&
                    it.raw_notes == history.notes
        }
    }

    fun clearError() {
        _errorMessage.postValue(null)
    }
}

@kotlinx.serialization.Serializable
data class AllergieInsert(
    @kotlinx.serialization.SerialName("childid") val ChildID: Int,
    val allergy_name: String,
    val allergy_type: String,
    val severity: String,
    val notes: String? = null
)

@kotlinx.serialization.Serializable
data class MedicalHistoryInsert(
    @kotlinx.serialization.SerialName("childid") val ChildID: Int,
    val condition_name: String,
    val diagnosis_date: String,
    val treatment: String,
    val notes: String? = null
)