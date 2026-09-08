package com.example.carelyo.ui.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.DoctorVisit
import com.example.carelyo.data.entity.DoctorVisitInsert
import com.example.carelyo.data.session.SessionManager
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
                val children = SupabaseClient.client.postgrest["CHILD"]
                    .select {
                        filter {
                            eq("parent_id", currentUser.UserID)
                        }
                    }.decodeList<Child>()

                _childrenList.value = children
                _isLoading.value = false

                if (children.isNotEmpty()) {
                    loadDoctorVisitsForChildren(children.map { it.ChildID })
                }
            } catch (e: Exception) {
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

                        allVisits.addAll(visits)
                    } catch (e: Exception) {
                        // Skip if one fails
                    }
                }

                allVisits.sortByDescending { it.visit_date }
                _doctorVisits.value = allVisits
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load doctor visits: ${e.localizedMessage}"
            }
        }
    }

    // Direct save without Gemini API calls
    fun saveConsultationNotes(
        childId: Int,
        doctorName: String,
        clinicName: String,
        rawNotes: String
    ) {
        _summaryState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val currentDate = dateFormat.format(Date())

                val newVisit = DoctorVisitInsert(
                    ChildID = childId,
                    visit_date = currentDate,
                    clinic_name = clinicName,
                    doctor_name = doctorName,
                    raw_notes = rawNotes,
                    ai_summary = rawNotes, // Storing raw notes directly into summary field
                    summary_language = "ms-MY"
                )

                val result = SupabaseClient.client.postgrest["DOCTOR_VISIT"]
                    .insert(newVisit) { select() }
                    .decodeSingle<DoctorVisit>()

                loadDoctorVisits()
                _summaryState.value = UiState.Success("Saved successfully")
            } catch (e: Exception) {
                _summaryState.value = UiState.Error("Error: ${e.localizedMessage}")
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