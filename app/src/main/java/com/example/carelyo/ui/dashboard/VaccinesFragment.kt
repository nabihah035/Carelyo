package com.example.carelyo.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carelyo.R
import com.example.carelyo.databinding.FragmentVaccinesBinding
import com.example.carelyo.databinding.ItemVaccineScheduleBinding

class VaccinesFragment : Fragment() {

    private var _binding: FragmentVaccinesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VaccineViewModel by viewModels()
    private lateinit var scheduleAdapter: VaccineScheduleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVaccinesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        scheduleAdapter = VaccineScheduleAdapter { item ->
            // Handle item click to show description
            Toast.makeText(requireContext(), item.description, Toast.LENGTH_SHORT).show()
        }
        binding.rvVaccineSchedule.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.vaccineState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is VaccineState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.mainContent.visibility = View.GONE
                }
                is VaccineState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.mainContent.visibility = View.VISIBLE
                    updateUI(state)
                }
                is VaccineState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateUI(state: VaccineState.Success) {
        // Update progress section
        val totalVaccines = state.completedCount + state.upcomingCount + state.overdueCount
        val progressValue = if (totalVaccines > 0) {
            (state.completedCount.toFloat() / totalVaccines.toFloat() * 100).toInt()
        } else 0

        binding.progressBarHorizontal.progress = progressValue
        binding.tvPercentage.text = "$progressValue%"

        // Update stats cards
        binding.tvDoneCount.text = state.completedCount.toString()
        binding.tvUpcomingCount.text = state.upcomingCount.toString()
        binding.tvOverdueCount.text = state.overdueCount.toString()

        // Update schedule list
        scheduleAdapter.submitList(state.scheduleItems)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// RecyclerView Adapter for Vaccine Schedule
class VaccineScheduleAdapter(
    private val onItemClick: (VaccineScheduleItem) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<VaccineScheduleAdapter.ViewHolder>() {

    private var items: List<VaccineScheduleItem> = emptyList()
    private var expandedPositions = mutableSetOf<Int>()

    fun submitList(newItems: List<VaccineScheduleItem>) {
        items = newItems
        expandedPositions.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVaccineScheduleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isExpanded = expandedPositions.contains(position)
        holder.bind(item, isExpanded)

        holder.itemView.setOnClickListener {
            if (isExpanded) {
                expandedPositions.remove(position)
            } else {
                expandedPositions.add(position)
            }
            notifyItemChanged(position)
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemVaccineScheduleBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VaccineScheduleItem, isExpanded: Boolean) {
            // Set icon based on status
            val (iconRes, iconBgColor, textColor) = when (item.status) {
                VaccineStatus.DONE -> Triple(
                    R.drawable.ic_check_circle,
                    R.color.success_soft,
                    R.color.success
                )
                VaccineStatus.UPCOMING -> Triple(
                    R.drawable.ic_schedule,
                    R.color.info_soft,
                    R.color.info
                )
                VaccineStatus.OVERDUE -> Triple(
                    R.drawable.ic_warning,
                    R.color.error_soft,
                    R.color.error
                )
            }

            binding.ivStatusIcon.setImageResource(iconRes)
            binding.ivStatusIcon.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, iconBgColor)
            )

            binding.tvVaccineName.setTextColor(
                ContextCompat.getColor(binding.root.context, textColor)
            )
            binding.tvVaccineName.text = item.vaccineName
            binding.tvAgeRequirement.text = item.ageRequirement

            val dateText = if (item.status == VaccineStatus.DONE && item.givenDate != null) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
                "Given: ${item.givenDate.format(formatter)}"
            } else if (item.status == VaccineStatus.UPCOMING) {
                "Due: Not yet scheduled"
            } else {
                "Overdue: Please schedule"
            }
            binding.tvDateInfo.text = dateText

            // Expand/Collapse description
            if (isExpanded) {
                binding.tvDescription.visibility = View.VISIBLE
                binding.tvDescription.text = item.description
                binding.ivExpandIcon.rotation = 90f
            } else {
                binding.tvDescription.visibility = View.GONE
                binding.ivExpandIcon.rotation = 0f
            }
        }
    }
}