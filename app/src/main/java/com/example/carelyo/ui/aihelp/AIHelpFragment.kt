package com.example.carelyo.ui.aihelp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carelyo.R
import com.example.carelyo.api.chat.ChatViewModel
import com.example.carelyo.databinding.FragmentAiHelpBinding
import kotlinx.coroutines.launch

class AIHelpFragment : Fragment() {

    private var _binding: FragmentAiHelpBinding? = null
    private val binding get() = _binding!!

    // Link the Fragment to your new architectural ChatViewModel
    private val viewModel: ChatViewModel by viewModels()

    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var messageInput: EditText
    private lateinit var sendButton: AppCompatButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChatRecyclerView()
        setupSuggestedQuestions()
        setupMessageInput()

        // Listen to the ViewModel for data updates
        observeViewModel()
    }

    private fun setupChatRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRecyclerView.adapter = chatAdapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
            Toast.makeText(requireContext(), "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        // Clear input field immediately
        messageInput.text.clear()

        // Send data directly to ViewModel (the layout collection stream now automatically triggers loading)
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
        suggestionsLayout.removeAllViews() // Avoid duplication on view recreation

        questions.forEach { question ->
            val questionView = TextView(requireContext()).apply {
                text = "• $question"
                textSize = 14f
                setTextColor(resources.getColor(R.color.black, null))
                setPadding(16, 12, 16, 12)
                background = resources.getDrawable(R.drawable.input_background, null)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
                setOnClickListener {
                    messageInput.setText(question)
                    sendMessage()
                }
            }
            suggestionsLayout.addView(questionView)
        }
    }

    private fun setupMessageInput() {
        messageInput = binding.root.findViewById(R.id.messageInput)
        sendButton = binding.root.findViewById(R.id.sendButton)

        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun showTypingIndicator() {
        // Only append if the last element isn't already a typing indicator
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}