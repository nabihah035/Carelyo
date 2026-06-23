package com.example.carelyo.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.data.entity.Child
import com.example.carelyo.databinding.ItemChildBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ChildrenAdapter(
    private val onChildClick: (Child) -> Unit,
    private val onDeleteClick: (Child) -> Unit
) : ListAdapter<Child, ChildrenAdapter.ChildViewHolder>(ChildDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
        val binding = ItemChildBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChildViewHolder(binding, onChildClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChildViewHolder(
        private val binding: ItemChildBinding,
        private val onChildClick: (Child) -> Unit,
        private val onDeleteClick: (Child) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(child: Child) {
            binding.tvChildName.text = child.full_name ?: "Unknown"

            val formattedDob = formatDateOfBirth(child.date_of_birth)
            binding.tvChildDob.text = "Born: $formattedDob"

            val details = buildString {
                if (!child.gender.isNullOrEmpty()) {
                    append(child.gender)
                }
                if (!child.blood_type.isNullOrEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(child.blood_type)
                }
                if (child.weight != null) {
                    if (isNotEmpty()) append(" • ")
                    append("${child.weight}kg")
                }
                if (child.height != null) {
                    if (isNotEmpty()) append(" • ")
                    append("${child.height}cm")
                }
                if (isEmpty()) {
                    append("No details")
                }
            }
            binding.tvChildDetails.text = details

            val avatarRes = when (child.gender?.lowercase()) {
                "female" -> R.drawable.ic_avatar_female
                "male" -> R.drawable.ic_avatar_male1
                else -> R.drawable.ic_person
            }
            binding.ivChildAvatar.setImageResource(avatarRes)

            binding.root.setOnClickListener {
                onChildClick(child)
            }
            binding.btnDeleteChild.setOnClickListener {
                onDeleteClick(child)
            }
        }

        private fun formatDateOfBirth(dateOfBirth: String?): String {
            if (dateOfBirth.isNullOrEmpty()) return "Unknown"
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateOfBirth)
                if (date != null) {
                    outputFormat.format(date)
                } else {
                    dateOfBirth
                }
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