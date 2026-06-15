package com.example.carelyo.api.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _messagesList = MutableStateFlow<List<Message>>(emptyList())
    val messagesList: StateFlow<List<Message>> = _messagesList

    init {
        // Inject a Pediatrics system guardrail prompt right away
        _messagesList.value = listOf(
            Message(
                role = "system",
                content = "You are Carelyo's Pediatric AI Assistant. Ground your suggestions strictly in medical guidelines. Never provide final diagnoses or medication dosages without instructing the user to confirm with a doctor. Don't answer question that is not relate to medical problem, if they are just reply we are not provided to reply to unrelated medical question. No need to bold the answer. Be concise. Use bullet points if needed. Don't use emoji. no need to put ** ** symbol to bold words."
            )
        )
    }

    fun sendMessageToMeditron(userPrompt: String) {
        // 1. Immediately append the user message to the UI
        val currentList = _messagesList.value.toMutableList()
        val userMessage = Message(role = "user", content = userPrompt)
        currentList.add(userMessage)
        _messagesList.value = currentList

        // 2. Fire off the network operation via coroutines
        viewModelScope.launch {
            try {
                val requestPayload = ChatRequest(messages = _messagesList.value)
                val response = NetworkClient.ollamaApi.sendChatMessage(requestPayload)

                val updatedList = _messagesList.value.toMutableList()
                updatedList.add(response.message)
                _messagesList.value = updatedList
            } catch (e: Exception) {
                // Print the real error message to Android Studio's logcat
                e.printStackTrace()

                val errorList = _messagesList.value.toMutableList()
                // Show the actual technical error on screen so we can pinpoint it
                errorList.add(Message(role = "assistant", content = "Error details: ${e.localizedMessage}"))
                _messagesList.value = errorList
            }
        }
    }
}