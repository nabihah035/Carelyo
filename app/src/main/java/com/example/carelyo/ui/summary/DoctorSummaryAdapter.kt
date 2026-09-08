package com.example.carelyo.ui.summary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.databinding.ItemListSummariesBinding
import com.example.carelyo.data.entity.DoctorVisit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DoctorSummaryAdapter(
    private val onItemClick: (DoctorVisit) -> Unit
) : ListAdapter<DoctorVisit, DoctorSummaryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemListSummariesBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemListSummariesBinding,
        private val onItemClick: (DoctorVisit) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(visit: DoctorVisit) {
            binding.tvDoctorName.text = visit.doctor_name ?: "Unknown Doctor"

            // Format date
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            binding.tvVisitDate.text = visit.visit_date?.let {
                try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it)
                    dateFormat.format(date ?: Date())
                } catch (e: Exception) {
                    it
                }
            } ?: "No date"

            // Display notes preview directly
            val previewText = visit.raw_notes?.take(100) ?: "No notes available"
            binding.tvAiSummaryText.text = previewText

            // Clear extra key points containers if not needed
            binding.llKeyPointsContainer.removeAllViews()
            binding.tvMorePointsLink.visibility = View.GONE

            binding.ivDocIcon.setImageResource(R.drawable.ic_doctor_visit)

            binding.root.setOnClickListener {
                onItemClick(visit)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DoctorVisit>() {
        override fun areItemsTheSame(oldItem: DoctorVisit, newItem: DoctorVisit): Boolean {
            return oldItem.DocVisitID == newItem.DocVisitID
        }

        override fun areContentsTheSame(oldItem: DoctorVisit, newItem: DoctorVisit): Boolean {
            return oldItem == newItem
        }
    }
}