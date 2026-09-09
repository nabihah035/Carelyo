package com.example.carelyo.ui.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.carelyo.api.chat.ChatRequest
import com.example.carelyo.api.chat.Message
import com.example.carelyo.api.chat.NetworkClient
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.DoctorVisit
import com.example.carelyo.data.entity.DoctorVisitInsert
import com.example.carelyo.data.session.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    data class Loading(val message: String = "Processing...") : UiState<Nothing>()
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
                } else {
                    loadDoctorVisitsForChildren(emptyList())
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
                val allVisits = mutableMapOf<Int, DoctorVisit>()

                for (childId in childIds) {
                    try {
                        val visits = SupabaseClient.client.postgrest["DOCTOR_VISIT"]
                            .select {
                                filter {
                                    eq("childid", childId)
                                }
                            }
                            .decodeList<DoctorVisit>()

                        visits.forEach { allVisits[it.DocVisitID] = it }
                    } catch (e: Exception) {
                        // Skip individual errors
                    }
                }

                // Also load visits recorded by current user
                val currentUser = sessionManager.getUserSession()
                if (currentUser != null) {
                    try {
                        val userVisits = SupabaseClient.client.postgrest["DOCTOR_VISIT"]
                            .select {
                                filter {
                                    eq("userid", currentUser.UserID)
                                }
                            }
                            .decodeList<DoctorVisit>()

                        userVisits.forEach { allVisits[it.DocVisitID] = it }
                    } catch (e: Exception) {
                        // Skip
                    }
                }

                val sortedList = allVisits.values.sortedByDescending { it.visit_date ?: it.created_at }
                _doctorVisits.value = sortedList
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load doctor visits: ${e.localizedMessage}"
            }
        }
    }

    // Save notes taken from doctor visit with AI summarization using Qwen2.5:3b
    fun saveConsultationNotes(
        childId: Int,
        doctorName: String,
        clinicName: String,
        rawNotes: String
    ) {
        _summaryState.value = UiState.Loading("Generating AI summary with Qwen2.5:3b... Please wait.")

        viewModelScope.launch {
            val currentDate = dateFormat.format(Date())
            val currentUser = sessionManager.getUserSession()

            val formattedNotes = buildString {
                if (doctorName.isNotBlank()) append("Doctor: $doctorName\n")
                if (clinicName.isNotBlank()) append("Clinic: $clinicName\n")
                if (isNotEmpty() && rawNotes.isNotBlank()) append("\n")
                append(rawNotes)
            }.trim()

            // 1. Generate summary using Qwen2.5:3b via Ollama
            var aiGeneratedSummary: String? = null
            try {
                aiGeneratedSummary = withContext(Dispatchers.IO) {
                    val systemPrompt = Message(
                        role = "system",
                        content = "You are Carelyo's Clinical Pediatric Assistant. Summarize the following doctor visit notes concisely for the child's parents. Use bullet points under clear headings: 1. Diagnosis / Findings, 2. Treatment & Instructions, 3. Prescribed Medications (if any), 4. Follow-up / Red Flags. Do not use bold markdown tags like ** **. Keep it clear, concise, and easy to read. Do not use emojis."
                    )
                    val userPrompt = Message(
                        role = "user",
                        content = "Please summarize these doctor consultation notes:\n$formattedNotes"
                    )

                    val requestPayload = ChatRequest(
                        model = "qwen2.5:3b",
                        messages = listOf(systemPrompt, userPrompt),
                        stream = false,
                        options = mapOf(
                            "num_predict" to 250,
                            "temperature" to 0.3
                        )
                    )

                    val response = NetworkClient.ollamaApi.sendChatMessage(requestPayload)
                    response.message.content.trim()
                }
            } catch (aiEx: Exception) {
                // LLM generation error or timeout - log and proceed with raw notes so data is not lost
                aiEx.printStackTrace()
            }

            // 2. Persist to Supabase DOCTOR_VISIT with summary column
            try {
                val newVisit = DoctorVisitInsert(
                    ChildID = childId,
                    visit_date = currentDate,
                    raw_notes = formattedNotes,
                    userid = currentUser?.UserID,
                    clinicid = null,
                    summary = aiGeneratedSummary
                )

                withContext(Dispatchers.IO) {
                    SupabaseClient.client.postgrest["DOCTOR_VISIT"]
                        .insert(newVisit) { select() }
                        .decodeSingle<DoctorVisit>()
                }

                loadDoctorVisits()
                if (aiGeneratedSummary != null) {
                    _summaryState.value = UiState.Success("Note & AI Summary saved successfully")
                } else {
                    _summaryState.value = UiState.Success("Note saved (AI summary timed out)")
                }
            } catch (dbEx: Exception) {
                _summaryState.value = UiState.Error("Failed to save doctor note: ${dbEx.localizedMessage}")
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