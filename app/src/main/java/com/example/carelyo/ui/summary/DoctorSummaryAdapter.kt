package com.example.carelyo.ui.summary

import android.view.LayoutInflater
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

            // Show AI summary preview
            val summaryPreview = visit.ai_summary?.take(100) ?: "No summary available"
            binding.tvAiSummaryText.text = summaryPreview

            // Parse and show key points
            val keyPoints = parseKeyPoints(visit.ai_summary ?: "")
            val llKeyPoints = binding.llKeyPointsContainer
            llKeyPoints.removeAllViews()

            // Show up to 3 key points
            val pointsToShow = keyPoints.take(3)
            pointsToShow.forEach { point ->
                val pointView = LayoutInflater.from(binding.root.context)
                    .inflate(R.layout.item_key_point, llKeyPoints, false)
                val tvPoint = pointView.findViewById<android.widget.TextView>(R.id.tvKeyPoint)
                tvPoint.text = point
                llKeyPoints.addView(pointView)
            }

            // Show "more points" if there are more than 3
            binding.tvMorePointsLink.visibility = if (keyPoints.size > 3) {
                binding.tvMorePointsLink.text = "+${keyPoints.size - 3} more points"
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            // Set clinic name icon
            binding.ivDocIcon.setImageResource(R.drawable.ic_doctor_visit)

            binding.root.setOnClickListener {
                onItemClick(visit)
            }
        }

        private fun parseKeyPoints(summary: String): List<String> {
            val lines = summary.split("\n")
            val keyPoints = mutableListOf<String>()

            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("•") -> keyPoints.add(trimmed.drop(1).trim())
                    trimmed.startsWith("-") -> keyPoints.add(trimmed.drop(1).trim())
                    trimmed.matches(Regex("^\\d+\\..*")) -> keyPoints.add(trimmed.substringAfter(".").trim())
                    trimmed.matches(Regex("^\\d+\\) .*")) -> keyPoints.add(trimmed.substringAfter(")").trim())
                    trimmed.startsWith("Gejala") || trimmed.startsWith("Diagnosis") ||
                            trimmed.startsWith("Ubat") || trimmed.startsWith("Nasihat") -> {
                        val content = trimmed.substringAfter(":").trim()
                        if (content.isNotEmpty()) keyPoints.add(content)
                    }
                }
            }

            return if (keyPoints.isEmpty()) {
                summary.split(".").map { it.trim() }.filter { it.length > 10 }
            } else {
                keyPoints
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