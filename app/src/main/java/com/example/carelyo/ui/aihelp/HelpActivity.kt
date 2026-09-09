package com.example.carelyo.ui.aihelp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.api.chat.ChatViewModel
import com.example.carelyo.databinding.ActivityHelpBinding
import kotlinx.coroutines.launch

class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding
    private val viewModel: ChatViewModel by viewModels()

    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupNavigation()
        setupChatRecyclerView()
        setupSuggestedQuestions()
        setupMessageInput()

        observeViewModel()
    }

    private fun setupWindowInsets() {
        // Adjust for soft keyboard (IME) and system bars so input is never obscured
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else systemBarsInsets.bottom

            binding.root.setPadding(
                systemBarsInsets.left,
                systemBarsInsets.top,
                systemBarsInsets.right,
                bottomInset
            )

            // Auto-scroll RecyclerView to bottom when keyboard opens
            if (imeInsets.bottom > 0 && chatMessages.isNotEmpty()) {
                binding.chatRecyclerView.postDelayed({
                    binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                }, 100)
            }

            windowInsets
        }
    }

    private fun setupNavigation() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnNewChat.setOnClickListener {
            viewModel.startNewSession()
            chatMessages.clear()
            chatAdapter.notifyDataSetChanged()
            updateVisibility(hasMessages = false)
            Toast.makeText(this, "Started new chat session", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupChatRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = false
        }
        binding.chatRecyclerView.layoutManager = layoutManager
        binding.chatRecyclerView.adapter = chatAdapter

        // Scroll to latest message when layout changes (e.g. keyboard opens/closes)
        binding.chatRecyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && chatMessages.isNotEmpty()) {
                binding.chatRecyclerView.post {
                    binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                }
            }
        }
    }

    private fun updateVisibility(hasMessages: Boolean) {
        if (hasMessages) {
            binding.welcomeContainer.visibility = View.GONE
            binding.chatRecyclerView.visibility = View.VISIBLE
        } else {
            binding.welcomeContainer.visibility = View.VISIBLE
            binding.chatRecyclerView.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messagesList.collect { apiMessages ->
                    // 1. Filter out system prompt setup messages
                    val displayableMessages = apiMessages.filter { it.role != "system" }

                    // 2. Map backend data models to UI chat items
                    val uiMessages = displayableMessages.map { apiMsg ->
                        ChatMessage(
                            message = apiMsg.content,
                            isUser = apiMsg.role == "user",
                            isTyping = false
                        )
                    }.toMutableList()

                    // 3. If the last message came from the user, AI is processing -> append typing state
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

                    // 5. Update welcome container vs chat list visibility
                    updateVisibility(hasMessages = chatMessages.isNotEmpty())

                    // 6. Scroll down to follow the latest message
                    if (chatMessages.isNotEmpty()) {
                        binding.chatRecyclerView.post {
                            binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
                        }
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) {
            return
        }

        // Clear input field immediately
        messageInput.text.clear()

        // Send to ViewModel
        viewModel.sendMessageToMeditron(message)
    }

    private fun setupSuggestedQuestions() {
        val questions = listOf(
            "What should I do if my child has a fever?",
            "When should my baby start solid foods?",
            "What is the recommended vaccination schedule in Malaysia?",
            "What are signs of dengue fever in kids?",
            "How to handle common cold in toddlers?"
        )

        val suggestionsLayout = binding.suggestedQuestionsLayout
        suggestionsLayout.removeAllViews()

        val density = resources.displayMetrics.density
        val paddingHorizontal = (18 * density).toInt()
        val paddingVertical = (14 * density).toInt()
        val marginBottomPx = (10 * density).toInt()

        questions.forEach { question ->
            val questionView = TextView(this).apply {
                text = question
                textSize = 15f
                setTextColor(Color.parseColor("#0F766E"))
                setTypeface(null, Typeface.BOLD)
                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                background = ContextCompat.getDrawable(this@HelpActivity, R.drawable.bg_suggestion_pill)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = marginBottomPx
                }

                setOnClickListener {
                    binding.messageInput.setText(question)
                    sendMessage()
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

        messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && chatMessages.isNotEmpty()) {
                binding.chatRecyclerView.postDelayed({
                    binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                }, 150)
            }
        }
    }
}