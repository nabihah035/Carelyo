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
            val freqFormatted = com.example.carelyo.utils.MedicationSchedulerHelper.formatFrequencyWithTimes(medication.frequency)
            binding.tvMedDetails.text = if (dosage.isNotEmpty()) {
                "$dosage • $freqFormatted"
            } else {
                freqFormatted
            }

            // Display formatted start and end dates
            val startDateFormatted = formatMedDate(medication.start_date)
            val endDateFormatted = formatMedDate(medication.end_date)
            val datesText = when {
                startDateFormatted.isNotEmpty() && endDateFormatted.isNotEmpty() ->
                    "Start: $startDateFormatted • End: $endDateFormatted"
                startDateFormatted.isNotEmpty() ->
                    "Start: $startDateFormatted"
                endDateFormatted.isNotEmpty() ->
                    "End: $endDateFormatted"
                else -> ""
            }
            if (datesText.isNotEmpty()) {
                binding.tvMedDates.text = datesText
                binding.tvMedDates.visibility = android.view.View.VISIBLE
            } else {
                binding.tvMedDates.visibility = android.view.View.GONE
            }

            // Remove listener temporarily so we don't trigger it while setting state
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = medication.is_active
            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive(medication, isChecked)
            }

            binding.btnDeleteMed.setOnClickListener {
                onDeleteClick(medication)
            }
        }

        private fun formatMedDate(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return ""
            val patterns = listOf("yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm:ss")
            for (p in patterns) {
                try {
                    val parsed = java.text.SimpleDateFormat(p, java.util.Locale.getDefault()).parse(dateString)
                    if (parsed != null) {
                        return java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(parsed)
                    }
                } catch (_: Exception) {}
            }
            return dateString
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
