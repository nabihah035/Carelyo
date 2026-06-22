package com.example.carelyo.ui.reminder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.data.entity.Reminder
import com.example.carelyo.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.*

class ReminderAdapter(
    private val onDismissClick: (Reminder) -> Unit,
    private val onItemClick: (Reminder) -> Unit,
    private val onMarkAsReadClick: (Reminder) -> Unit
) : ListAdapter<Reminder, ReminderAdapter.ReminderViewHolder>(ReminderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = getItem(position)
        holder.bind(reminder)

        holder.itemView.setOnClickListener {
            onItemClick(reminder)
            // Mark as read when clicked if not already read
            if (reminder.noti_status == "Unread") {
                onMarkAsReadClick(reminder) // This will mark as read
            }
        }

        holder.binding.btnDismiss.setOnClickListener {
            onDismissClick(reminder)
        }
    }

    class ReminderViewHolder(val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reminder: Reminder) {
            // Set title based on reminder type
            val title = when (reminder.reminder_type?.lowercase()) {
                "vaccine" -> "Vaccine Reminder"
                "medication" -> "Medication Reminder"
                "appointment" -> "Appointment Reminder"
                else -> "Reminder"
            }
            binding.tvTitle.text = title

            // Set subtitle based on reference type
            val subtitle = when (reminder.reminder_type?.lowercase()) {
                "vaccine" -> "Vaccine scheduled for ${formatDate(reminder.scheduled_at)}"
                "medication" -> "Medication due at ${formatTime(reminder.scheduled_at)}"
                "appointment" -> "Appointment at ${formatDateTime(reminder.scheduled_at)}"
                else -> "Reminder scheduled for ${formatDateTime(reminder.scheduled_at)}"
            }
            binding.tvSubtitle.text = subtitle

            // Set timestamp
            binding.tvTimestamp.text = formatRelativeTime(reminder.created_at)

            // Set icon based on reminder type
            val iconRes = when (reminder.reminder_type?.lowercase()) {
                "vaccine" -> R.drawable.ic_vaccine
                "medication" -> R.drawable.ic_pill
                "appointment" -> R.drawable.ic_calendar
                else -> R.drawable.ic_notification
            }
            binding.ivReminderIcon.setImageResource(iconRes)

            // Show/hide unread dot
            binding.viewUnreadDot.visibility = if (reminder.noti_status == "Unread") {
                View.VISIBLE
            } else {
                View.GONE
            }

            // Change background color based on read status
            if (reminder.noti_status == "Unread") {
                binding.itemContainer.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.unread_background)
                )
            } else {
                binding.itemContainer.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
            }
        }

        private fun formatDate(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return "Unknown date"
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = format.parse(dateString)
                val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                "Unknown date"
            }
        }

        private fun formatTime(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return "Unknown time"
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = format.parse(dateString)
                val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                "Unknown time"
            }
        }

        private fun formatDateTime(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return "Unknown time"
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = format.parse(dateString)
                val outputFormat = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                "Unknown time"
            }
        }

        private fun formatRelativeTime(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return "Just now"
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = format.parse(dateString)
                val now = Date()
                val diff = now.time - (date?.time ?: 0)

                when {
                    diff < 60000 -> "Just now"
                    diff < 3600000 -> "${diff / 60000}m ago"
                    diff < 86400000 -> "${diff / 3600000}h ago"
                    diff < 172800000 -> "Yesterday"
                    else -> {
                        val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                        outputFormat.format(date ?: Date())
                    }
                }
            } catch (e: Exception) {
                "Just now"
            }
        }
    }

    class ReminderDiffCallback : DiffUtil.ItemCallback<Reminder>() {
        override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
            return oldItem.RemindID == newItem.RemindID
        }

        override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
            return oldItem == newItem
        }
    }
}