package com.example.carelyo.api.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.carelyo.api.supabase.SupabaseClient
import com.example.carelyo.data.entity.ChatMessage as DbChatMessage
import com.example.carelyo.data.entity.ChatMessageInsert
import com.example.carelyo.data.entity.ChatSession
import com.example.carelyo.data.entity.ChatSessionInsert
import com.example.carelyo.data.session.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private var currentSessionId: Int? = null

    private val _messagesList = MutableStateFlow<List<Message>>(emptyList())
    val messagesList: StateFlow<List<Message>> = _messagesList

    private val _isLoadingHistory = MutableStateFlow<Boolean>(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory

    private val systemPromptMessage = Message(
        role = "system",
        content = "You are Carelyo's Pediatric AI Assistant. Ground your suggestions strictly in medical guidelines. Never provide final diagnoses or medication dosages without instructing the user to confirm with a doctor. Don't answer question that is not relate to medical problem, if they are just reply we are not provided to reply to unrelated medical question. No need to bold the answer. Be concise. Use bullet points if needed. Don't use emoji. no need to put ** ** symbol to bold words. Refer to Malaysian guidelines only."
    )

    init {
        _messagesList.value = listOf(systemPromptMessage)
        loadChatHistory()
    }

    fun startNewSession() {
        currentSessionId = null
        _messagesList.value = listOf(systemPromptMessage)
    }

    fun loadChatHistory() {
        val user = sessionManager.getUserSession() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingHistory.value = true
            try {
                // Fetch the latest session for this user
                val sessions = SupabaseClient.client.postgrest["CHAT_SESSION"]
                    .select {
                        filter {
                            eq("user_id", user.UserID)
                        }
                        order("sessionid", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<ChatSession>()

                if (sessions.isNotEmpty()) {
                    val latestSession = sessions.first()
                    currentSessionId = latestSession.SessionID

                    // Fetch all messages for this session
                    val dbMessages = SupabaseClient.client.postgrest["CHAT_MESSAGES"]
                        .select {
                            filter {
                                eq("sessionid", latestSession.SessionID)
                            }
                            order("messageid", Order.ASCENDING)
                        }
                        .decodeList<DbChatMessage>()

                    if (dbMessages.isNotEmpty()) {
                        val mapped = mutableListOf<Message>()
                        mapped.add(systemPromptMessage)

                        for (dbMsg in dbMessages) {
                            val role = when (dbMsg.sender_type.uppercase()) {
                                "USER" -> "user"
                                "BOT" -> "assistant"
                                "SYSTEM" -> "system"
                                else -> "assistant"
                            }
                            if (role == "system") continue
                            mapped.add(Message(role = role, content = dbMsg.content))
                        }

                        withContext(Dispatchers.Main) {
                            _messagesList.value = mapped
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }

    fun sendMessageToMeditron(userPrompt: String) {
        // 1. Immediately append the user message to the UI
        val currentList = _messagesList.value.toMutableList()
        val userMessage = Message(role = "user", content = userPrompt)
        currentList.add(userMessage)
        _messagesList.value = currentList

        val user = sessionManager.getUserSession()

        // 2. Fire network and database operations via coroutines
        viewModelScope.launch(Dispatchers.IO) {
            val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowIso = isoDateFormat.format(Date())

            // Persist USER message to Supabase
            if (user != null) {
                try {
                    if (currentSessionId == null) {
                        val sessionTitle = if (userPrompt.length > 40) {
                            userPrompt.take(40) + "..."
                        } else {
                            userPrompt
                        }

                        val newSession = SupabaseClient.client.postgrest["CHAT_SESSION"]
                            .insert(
                                ChatSessionInsert(
                                    user_id = user.UserID,
                                    title = sessionTitle,
                                    created_at = nowIso,
                                    update_at = nowIso
                                )
                            ) { select() }
                            .decodeSingle<ChatSession>()

                        currentSessionId = newSession.SessionID
                    }

                    val activeSessionId = currentSessionId
                    if (activeSessionId != null) {
                        val tokenEstimate = userPrompt.split("\\s+".toRegex()).size
                        SupabaseClient.client.postgrest["CHAT_MESSAGES"]
                            .insert(
                                ChatMessageInsert(
                                    sender_type = "USER",
                                    content = userPrompt,
                                    sessionid = activeSessionId,
                                    token_count = tokenEstimate
                                )
                            )
                    }
                } catch (dbErr: Exception) {
                    dbErr.printStackTrace()
                }
            }

            // Call Ollama API
            try {
                val requestPayload = ChatRequest(messages = _messagesList.value)
                val response = NetworkClient.ollamaApi.sendChatMessage(requestPayload)
                val botMessage = response.message

                withContext(Dispatchers.Main) {
                    val updatedList = _messagesList.value.toMutableList()
                    updatedList.add(botMessage)
                    _messagesList.value = updatedList
                }

                // Persist BOT message to Supabase
                if (user != null && currentSessionId != null) {
                    try {
                        val botTokens = botMessage.content.split("\\s+".toRegex()).size
                        SupabaseClient.client.postgrest["CHAT_MESSAGES"]
                            .insert(
                                ChatMessageInsert(
                                    sender_type = "BOT",
                                    content = botMessage.content,
                                    sessionid = currentSessionId,
                                    token_count = botTokens
                                )
                            )

                        val updateTime = isoDateFormat.format(Date())
                        SupabaseClient.client.postgrest["CHAT_SESSION"]
                            .update(mapOf("update_at" to updateTime)) {
                                filter { eq("sessionid", currentSessionId!!) }
                            }
                    } catch (dbErr: Exception) {
                        dbErr.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val errorList = _messagesList.value.toMutableList()
                    errorList.add(Message(role = "assistant", content = "Error details: ${e.localizedMessage}"))
                    _messagesList.value = errorList
                }
            }
        }
    }
}