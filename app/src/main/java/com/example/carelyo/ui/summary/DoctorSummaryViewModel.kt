package com.example.carelyo.ui.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.carelyo.BuildConfig
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.DoctorVisit
import com.example.carelyo.data.entity.DoctorVisitInsert
import com.example.carelyo.data.session.SessionManager
import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class DoctorSummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _summaryState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val summaryState: StateFlow<UiState<String>> = _summaryState

    private val _childrenList = MutableStateFlow<List<Child>>(emptyList())
    val childrenList: StateFlow<List<Child>> = _childrenList

    private val _doctorVisits = MutableStateFlow<List<DoctorVisit>>(emptyList())
    val doctorVisits: StateFlow<List<DoctorVisit>> = _doctorVisits

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Initialize Gemini Model
    private val generativeModel by lazy {
        try {
            GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = BuildConfig.GEMINI_API_KEY
            )
        } catch (e: Exception) {
            // Fallback for development - remove in production
            GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = "YOUR_API_KEY_HERE"
            )
        }
    }

    init {
        loadChildren()
    }

    fun loadChildren() {
        _isLoading.value = true
        val currentUser = sessionManager.getUserSession()

        if (currentUser == null) {
            _isLoading.value = false
            _errorMessage.value = "No active session found"
            return
        }

        viewModelScope.launch {
            try {
                println("[DoctorSummaryVM]: Fetching children for Parent_ID: ${currentUser.UserID}")

                val children = SupabaseClient.client.postgrest["CHILD"]
                    .select {
                        filter {
                            eq("parent_id", currentUser.UserID)
                        }
                    }.decodeList<Child>()

                println("[DoctorSummaryVM]: Found ${children.size} children")
                _childrenList.value = children
                _isLoading.value = false

                if (children.isNotEmpty()) {
                    // Load doctor visits for all children
                    loadDoctorVisitsForChildren(children.map { it.ChildID })
                }
            } catch (e: Exception) {
                println("[DoctorSummaryVM]: Error loading children: ${e.localizedMessage}")
                e.printStackTrace()
                _isLoading.value = false
                _errorMessage.value = "Failed to load children: ${e.localizedMessage}"
            }
        }
    }

    fun loadDoctorVisits() {
        val children = _childrenList.value
        if (children.isEmpty()) {
            loadChildren()
            return
        }
        loadDoctorVisitsForChildren(children.map { it.ChildID })
    }

    private fun loadDoctorVisitsForChildren(childIds: List<Int>) {
        viewModelScope.launch {
            try {
                println("[DoctorSummaryVM]: Fetching doctor visits for children: $childIds")

                val allVisits = mutableListOf<DoctorVisit>()

                for (childId in childIds) {
                    try {
                        val visits = SupabaseClient.client.postgrest["DOCTOR_VISIT"]
                            .select {
                                filter {
                                    eq("childid", childId)
                                }
                            }
                            .decodeList<DoctorVisit>()

                        // Sort manually by visit_date descending
                        val sortedVisits = visits.sortedByDescending { it.visit_date }
                        allVisits.addAll(sortedVisits)

                        println("[DoctorSummaryVM]: Found ${visits.size} visits for child $childId")
                    } catch (e: Exception) {
                        println("[DoctorSummaryVM]: Error fetching visits for child $childId: ${e.localizedMessage}")
                        // Continue with other children
                    }
                }

                // Sort all visits by date descending
                allVisits.sortByDescending { it.visit_date }

                println("[DoctorSummaryVM]: Found ${allVisits.size} total doctor visits")
                _doctorVisits.value = allVisits
            } catch (e: Exception) {
                println("[DoctorSummaryVM]: Error loading doctor visits: ${e.localizedMessage}")
                e.printStackTrace()
                _errorMessage.value = "Failed to load doctor visits: ${e.localizedMessage}"
            }
        }
    }

    fun generateSummaryFromNotes(
        childId: Int,
        doctorName: String,
        clinicName: String,
        rawNotes: String
    ) {
        _summaryState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val prompt = """
                    You are an expert pediatric healthcare assistant. 
                    Review the following consultation notes, which were generated via a speech-to-text tool and may contain minor phonetic spelling errors or typos of medical terms.

                    1. Clean up and correct any misheard medical terminology.
                    2. Generate a clear, parent-friendly summary.
                    3. Format the output into a clear structure.

                    Extract the following details precisely:
                    • Gejala Anak (Child Symptoms)
                    • Diagnosis
                    • Ubat-Ubatan (Medication Prescribed with dosages if mentioned)
                    • Nasihat Susulan (Follow-up Advice)

                    Raw Consultation Notes:
                    $rawNotes
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val responseText = response.text

                if (!responseText.isNullOrEmpty()) {
                    // Save to database
                    saveDoctorVisit(
                        childId = childId,
                        doctorName = doctorName,
                        clinicName = clinicName,
                        rawNotes = rawNotes,
                        aiSummary = responseText,
                        summaryLanguage = "ms-MY"
                    )
                    _summaryState.value = UiState.Success(responseText)
                } else {
                    _summaryState.value = UiState.Error("Gemini returned an empty response.")
                }
            } catch (e: Exception) {
                _summaryState.value = UiState.Error("AI Error: ${e.localizedMessage}")
            }
        }
    }

    private fun saveDoctorVisit(
        childId: Int,
        doctorName: String,
        clinicName: String,
        rawNotes: String,
        aiSummary: String,
        summaryLanguage: String
    ) {
        viewModelScope.launch {
            try {
                val currentDate = dateFormat.format(Date())

                // Create the doctor visit object
                val newVisit = DoctorVisitInsert(
                    ChildID = childId,
                    visit_date = currentDate,
                    clinic_name = clinicName,
                    doctor_name = doctorName,
                    raw_notes = rawNotes,
                    ai_summary = aiSummary,
                    summary_language = summaryLanguage
                )

                println("[DoctorSummaryVM]: Saving doctor visit to Supabase")
                println("[DoctorSummaryVM]: ChildID: $childId, Doctor: $doctorName")

                // Insert into Supabase
                val result = SupabaseClient.client.postgrest["DOCTOR_VISIT"]
                    .insert(newVisit) { select() }
                    .decodeSingle<DoctorVisit>()

                println("[DoctorSummaryVM]: Doctor visit saved successfully with ID: ${result.DocVisitID}")

                // Refresh the list
                loadDoctorVisits()
            } catch (e: Exception) {
                println("[DoctorSummaryVM]: Error saving doctor visit: ${e.localizedMessage}")
                e.printStackTrace()
                _errorMessage.value = "Failed to save doctor visit: ${e.localizedMessage}"
            }
        }
    }

    fun resetState() {
        _summaryState.value = UiState.Idle
    }

    fun clearError() {
        _errorMessage.value = null
    }
}