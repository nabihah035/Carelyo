package com.example.carelyo.ui.summary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.databinding.ItemListSummariesBinding
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.DoctorVisit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DoctorSummaryAdapter(
    private val onItemClick: (DoctorVisit) -> Unit
) : ListAdapter<DoctorVisit, DoctorSummaryAdapter.ViewHolder>(DiffCallback()) {

    private var childrenMap: Map<Int, String> = emptyMap()

    fun setChildren(children: List<Child>) {
        childrenMap = children.associate { it.ChildID to (it.full_name ?: "Child") }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemListSummariesBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick, { childrenMap })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemListSummariesBinding,
        private val onItemClick: (DoctorVisit) -> Unit,
        private val getChildrenMap: () -> Map<Int, String>
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(visit: DoctorVisit) {
            val docLine = visit.raw_notes?.lineSequence()
                ?.find { it.trim().startsWith("Doctor:", ignoreCase = true) }
            val extractedDoctor = docLine?.substringAfter("Doctor:")?.trim()
            val doctorTitle = extractedDoctor?.takeIf { it.isNotBlank() } ?: "Doctor Visit"

            val childName = visit.ChildID?.let { getChildrenMap()[it] }
            val headerText = if (!childName.isNullOrBlank()) "$childName • $doctorTitle" else doctorTitle

            binding.tvDoctorName.text = headerText

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

            // Display AI summary if available, otherwise raw notes preview
            if (!visit.summary.isNullOrBlank()) {
                binding.tvNotesLabel.text = "AI Summary (Qwen2.5:3b):"
                binding.tvAiSummaryText.text = visit.summary.take(200) + if (visit.summary.length > 200) "..." else ""
            } else {
                binding.tvNotesLabel.text = "Doctor Visit Notes:"
                binding.tvAiSummaryText.text = visit.raw_notes?.take(150) ?: "No notes available"
            }

            binding.llKeyPointsContainer.removeAllViews()
            binding.llKeyPointsContainer.visibility = View.GONE
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