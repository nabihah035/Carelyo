package com.example.carelyo.ui.health.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.data.entity.Allergie
import com.example.carelyo.databinding.ItemAllergyBinding

class AllergyAdapter(
    private var items: List<Allergie>,
    private val onDeleteClick: (Allergie) -> Unit,
    private val onItemClick: (Allergie) -> Unit
) : RecyclerView.Adapter<AllergyAdapter.AllergyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AllergyViewHolder {
        val binding = ItemAllergyBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AllergyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AllergyViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<Allergie>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class AllergyViewHolder(
        private val binding: ItemAllergyBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(allergy: Allergie) {
            binding.tvAllergyName.text = allergy.allergy_name ?: "Unknown Allergy"
            binding.tvAllergyType.text = allergy.allergy_type ?: "Unknown Type"

            val severityText = allergy.severity ?: "Unknown"
            binding.tvSeverity.text = severityText

            val severityColor = when (severityText.lowercase()) {
                "severe", "high" -> R.color.allergy_severe
                "moderate", "medium" -> R.color.allergy_moderate
                "mild", "low" -> R.color.allergy_mild
                else -> R.color.allergy_unknown
            }
            binding.tvSeverity.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, severityColor)
            )

            binding.tvNotes.text = allergy.notes ?: "No additional notes"
            binding.tvNotes.visibility = if (allergy.notes.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE

            val childName = binding.root.tag as? String
            if (!childName.isNullOrEmpty()) {
                binding.tvChildNameBadge.text = childName
                binding.tvChildNameBadge.visibility = android.view.View.VISIBLE
            } else {
                binding.tvChildNameBadge.visibility = android.view.View.GONE
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(allergy)
            }

            binding.root.setOnClickListener {
                onItemClick(allergy)
            }
        }
    }
}