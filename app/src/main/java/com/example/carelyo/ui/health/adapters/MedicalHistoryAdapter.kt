package com.example.carelyo.ui.health.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.data.entity.MedicalHistory
import com.example.carelyo.databinding.ItemMedicalHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class MedicalHistoryAdapter(
    private var items: List<MedicalHistory>,
    private val onItemClick: (MedicalHistory) -> Unit
) : RecyclerView.Adapter<MedicalHistoryAdapter.MedicalHistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicalHistoryViewHolder {
        val binding = ItemMedicalHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MedicalHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MedicalHistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<MedicalHistory>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class MedicalHistoryViewHolder(
        private val binding: ItemMedicalHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(history: MedicalHistory) {
            binding.tvConditionName.text = history.condition_name ?: "Unknown Condition"
            binding.tvTreatment.text = history.treatment ?: "No treatment recorded"

            val date = history.diagnosis_date?.let {
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val parsedDate = inputFormat.parse(it)
                    if (parsedDate != null) {
                        dateFormat.format(parsedDate)
                    } else {
                        it
                    }
                } catch (e: Exception) {
                    it
                }
            } ?: "Date not recorded"

            binding.tvDate.text = date

            binding.tvNotes.text = history.notes ?: "No additional notes"
            binding.tvNotes.visibility = if (history.notes.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE

            val childName = binding.root.tag as? String
            if (!childName.isNullOrEmpty()) {
                binding.tvHistoryChildBadge.text = childName
                binding.tvHistoryChildBadge.visibility = android.view.View.VISIBLE
            } else {
                binding.tvHistoryChildBadge.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick(history)
            }
        }
    }
}