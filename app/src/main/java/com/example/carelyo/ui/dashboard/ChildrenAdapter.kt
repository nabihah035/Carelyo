package com.example.carelyo.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.data.entity.Child
import com.example.carelyo.databinding.ItemChildBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChildrenAdapter(
    private val onChildClick: (Child) -> Unit
) : ListAdapter<Child, ChildrenAdapter.ChildViewHolder>(ChildDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
        val binding = ItemChildBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChildViewHolder(binding, onChildClick)
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChildViewHolder(
        private val binding: ItemChildBinding,
        private val onChildClick: (Child) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(child: Child) {
            binding.tvChildName.text = child.full_name ?: "Unknown"

            // Calculate age from date of birth
            val age = calculateAge(child.date_of_birth)
            binding.tvChildAge.text = if (age != null) "$age years old" else "Age unknown"

            // Format date of birth
            val formattedDob = formatDateOfBirth(child.date_of_birth)
            binding.tvChildDob.text = "Born: $formattedDob"

            binding.root.setOnClickListener {
                onChildClick(child)
            }
        }

        private fun calculateAge(dateOfBirth: String?): Int? {
            if (dateOfBirth.isNullOrEmpty()) return null
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val birthDate = format.parse(dateOfBirth)
                val currentDate = Date()
                val ageInMillis = currentDate.time - birthDate?.time!! ?: return null
                val ageInYears = (ageInMillis / (1000L * 60 * 60 * 24 * 365)).toInt()
                ageInYears
            } catch (e: Exception) {
                null
            }
        }

        private fun formatDateOfBirth(dateOfBirth: String?): String {
            if (dateOfBirth.isNullOrEmpty()) return "Unknown"
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateOfBirth)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                dateOfBirth
            }
        }
    }

    class ChildDiffCallback : DiffUtil.ItemCallback<Child>() {
        override fun areItemsTheSame(oldItem: Child, newItem: Child): Boolean {
            return oldItem.ChildID == newItem.ChildID
        }

        override fun areContentsTheSame(oldItem: Child, newItem: Child): Boolean {
            return oldItem == newItem
        }
    }
}