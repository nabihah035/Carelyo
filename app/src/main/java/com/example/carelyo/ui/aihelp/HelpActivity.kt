package com.example.carelyo.ui.aihelp

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carelyo.R
import com.example.carelyo.api.chat.ChatViewModel
import com.example.carelyo.databinding.ActivityHelpBinding
import kotlinx.coroutines.launch
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.ContextCompat

class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding

    // Link the Activity to your architectural ChatViewModel
    private val viewModel: ChatViewModel by viewModels()

    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var messageInput: EditText
    private lateinit var sendButton: android.widget.ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding for Activity
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the back navigation button
        setupNavigation()

        setupChatRecyclerView()
        setupSuggestedQuestions()
        setupMessageInput()

        // Listen to the ViewModel for data updates
        observeViewModel()
    }

    private fun setupNavigation() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupChatRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = chatAdapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messagesList.collect { apiMessages ->
                    // 1. Filter out system prompt setup messages
                    val displayableMessages = apiMessages.filter { it.role != "system" }

                    // 2. Map backend data models to your standard chat UI items
                    val uiMessages = displayableMessages.map { apiMsg ->
                        ChatMessage(
                            message = apiMsg.content,
                            isUser = apiMsg.role == "user",
                            isTyping = false
                        )
                    }.toMutableList()

                    // 3. CRITICAL FIX: If the last message came from the user,
                    // it means the AI backend is currently processing. Append the loading state here!
                    if (displayableMessages.isNotEmpty() && displayableMessages.last().role == "user") {
                        uiMessages.add(
                            ChatMessage(
                                message = "...",
                                isUser = false,
                                isTyping = true
                            )
                        )
                    }

                    // 4. Update the active UI message array
                    chatMessages.clear()
                    chatMessages.addAll(uiMessages)
                    chatAdapter.notifyDataSetChanged()

                    // 5. Instantly autoscroll down to follow the animation frame
                    if (chatMessages.isNotEmpty()) {
                        binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        // Clear input field immediately
        messageInput.text.clear()

        // Send data directly to ViewModel
        viewModel.sendMessageToMeditron(message)
    }

    private fun setupSuggestedQuestions() {
        val questions = listOf(
            "What are common childhood illnesses in Malaysia?",
            "What is the recommended vaccination schedule in Malaysia?",
            "How to treat fever in children?",
            "What are signs of dengue fever in kids?",
            "Nutrition guidelines for Malaysian children"
        )

        val suggestionsLayout = binding.suggestedQuestionsLayout
        suggestionsLayout.removeAllViews() // Avoid duplication

        // Convert dps to pixels for accurate layout scaling
        val density = resources.displayMetrics.density
        val paddingHorizontal = (18 * density).toInt()
        val paddingVertical = (14 * density).toInt()
        val marginBottomPx = (10 * density).toInt()

        questions.forEach { question ->
            val questionView = TextView(this).apply {
                text = question // Removed bullet point to match image_2ed002.png
                textSize = 15f
                setTextColor(Color.parseColor("#0F766E")) // Deep teal/mint text
                setTypeface(null, Typeface.BOLD) // Bold text style
                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

                // Reference the light mint background drawable
                background = ContextCompat.getDrawable(this@HelpActivity, R.drawable.bg_suggestion_pill)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = marginBottomPx
                }

                setOnClickListener {
                    binding.messageInput.setText(question)
                    sendMessage() // Make sure this matches your layout variable binding name
                }
            }
            suggestionsLayout.addView(questionView)
        }
    }

    private fun setupMessageInput() {
        messageInput = binding.messageInput
        sendButton = binding.sendButton

        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun showTypingIndicator() {
        if (chatMessages.isEmpty() || !chatMessages.last().isTyping) {
            chatMessages.add(ChatMessage("...", false, isTyping = true))
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
        }
    }

    private fun hideTypingIndicator() {
        if (chatMessages.isNotEmpty() && chatMessages.last().isTyping) {
            val index = chatMessages.size - 1
            chatMessages.removeAt(index)
            chatAdapter.notifyItemRemoved(index)
        }
    }
}