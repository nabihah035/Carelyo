package com.example.carelyo.ui.aihelp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    // Unique IDs to distinguish between user messages and bot messages
    private val VIEW_TYPE_USER = 1
    private val VIEW_TYPE_BOT = 0

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        // Choose the correct XML design file based on who sent the message
        val layoutId = if (viewType == VIEW_TYPE_USER) {
            R.layout.item_user_message
        } else {
            R.layout.item_bot_message
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        fun bind(message: ChatMessage) {
            if (message.isTyping) {
                messageText.text = "Typing..."

                // Add a simple fade animation so the user knows the app hasn't frozen
                messageText.animate()
                    .alpha(0.3f)
                    .setDuration(800)
                    .withEndAction {
                        messageText.animate().alpha(1.0f).setDuration(800).start()
                    }.start()

            } else {
                // Clear any running animation states when reuse patterns apply standard text
                messageText.animate().cancel()
                messageText.alpha = 1.0f
                messageText.text = message.message
            }
        }
    }
}