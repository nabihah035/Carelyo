package com.example.carelyo.ui.reminder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.data.entity.Notification
import com.example.carelyo.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.*

class ReminderAdapter(
    private val onDismissClick: (Notification) -> Unit,
    private val onItemClick: (Notification) -> Unit,
    private val onMarkAsReadClick: (Notification) -> Unit
) : ListAdapter<Notification, ReminderAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = getItem(position)
        holder.bind(notification)

        holder.itemView.setOnClickListener {
            onItemClick(notification)
            // Mark as read when clicked if not already read
            if (notification.is_read != true) {
                onMarkAsReadClick(notification)
            }
        }

        holder.binding.btnDismiss.setOnClickListener {
            onDismissClick(notification)
        }
    }

    class NotificationViewHolder(val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: Notification) {
            // Set title based on notification title or type
            val title = notification.title?.takeIf { it.isNotEmpty() } ?: when (notification.type?.lowercase()) {
                "vaccine" -> "Vaccine Reminder"
                "medication" -> "Medication Reminder"
                "appointment" -> "Appointment Reminder"
                else -> "Reminder"
            }
            binding.tvTitle.text = title

            // Set message / subtitle
            binding.tvSubtitle.text = notification.message ?: ""

            // Set timestamp
            binding.tvTimestamp.text = formatRelativeTime(notification.created_at)

            // Set icon based on type
            val iconRes = when (notification.type?.lowercase()) {
                "vaccine" -> R.drawable.ic_vaccine
                "medication" -> R.drawable.ic_pill
                "appointment" -> R.drawable.ic_calendar
                else -> R.drawable.ic_notification
            }
            binding.ivReminderIcon.setImageResource(iconRes)

            // Show/hide unread dot
            val isUnread = notification.is_read != true
            binding.viewUnreadDot.visibility = if (isUnread) View.VISIBLE else View.GONE

            // Change background color based on read status
            if (isUnread) {
                binding.itemContainer.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.unread_background)
                )
            } else {
                binding.itemContainer.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
            }
        }

        private fun formatRelativeTime(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return ""
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
            )
            var date: Date? = null
            for (pattern in patterns) {
                try {
                    val format = SimpleDateFormat(pattern, Locale.getDefault())
                    date = format.parse(dateString)
                    if (date != null) break
                } catch (_: Exception) {
                }
            }

            if (date == null) return dateString

            val outputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            return outputFormat.format(date)
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.NotificationID == newItem.NotificationID
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
        }
    }
}