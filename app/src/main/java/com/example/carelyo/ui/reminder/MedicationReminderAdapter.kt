package com.example.carelyo.ui.reminder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.data.entity.Medication
import com.example.carelyo.databinding.ItemMedicationReminderBinding

class MedicationReminderAdapter(
    private val onDeleteClick: (Medication) -> Unit,
    private val onToggleActive: (Medication, Boolean) -> Unit
) : ListAdapter<Medication, MedicationReminderAdapter.ViewHolder>(MedicationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicationReminderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMedicationReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(medication: Medication) {
            binding.tvMedName.text = medication.medication_name ?: "Unknown"
            
            val dosage = medication.dosage ?: ""
            val freq = medication.frequency ?: ""
            binding.tvMedDetails.text = if (dosage.isNotEmpty() && freq.isNotEmpty()) {
                "$dosage, $freq"
            } else if (dosage.isNotEmpty()) {
                dosage
            } else {
                freq
            }

            // Remove listener temporarily so we don't trigger it while setting state
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = medication.is_active ?: false
            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive(medication, isChecked)
            }

            binding.btnDeleteMed.setOnClickListener {
                onDeleteClick(medication)
            }
        }
    }

    class MedicationDiffCallback : DiffUtil.ItemCallback<Medication>() {
        override fun areItemsTheSame(oldItem: Medication, newItem: Medication): Boolean {
            return oldItem.MedID == newItem.MedID
        }

        override fun areContentsTheSame(oldItem: Medication, newItem: Medication): Boolean {
            return oldItem == newItem
        }
    }
}
