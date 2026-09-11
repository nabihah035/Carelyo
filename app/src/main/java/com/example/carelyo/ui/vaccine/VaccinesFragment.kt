package com.example.carelyo.ui.vaccine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.ChildVaccine
import com.example.carelyo.data.entity.Vaccination
import com.example.carelyo.databinding.DialogMarkVaccineTakenBinding
import com.example.carelyo.databinding.DialogViewVaccineDetailBinding
import com.example.carelyo.databinding.FragmentVaccinesBinding
import com.example.carelyo.databinding.ItemChildVaccineGroupBinding
import com.example.carelyo.databinding.ItemDialogChildSelectBinding
import com.example.carelyo.databinding.ItemDialogVaccineSelectBinding
import com.example.carelyo.databinding.ItemVaccineScheduleBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter



class VaccinesFragment : Fragment() {

    private var _binding: FragmentVaccinesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VaccineViewModel by viewModels()
    private lateinit var groupAdapter: ChildVaccineGroupAdapter


    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dbDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dbDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

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
        setupSwipeRefresh()

        // Request data
        val sessionManager = com.example.carelyo.data.session.SessionManager(requireContext())
        val user = sessionManager.getUserSession()
        user?.let {
            viewModel.requestVaccinationData(it.UserID)
        }
    }

    private fun setupRecyclerView() {
        groupAdapter = ChildVaccineGroupAdapter { item ->
            showVaccineDetailDialog(item)
        }
        binding.rvVaccineSchedule.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = groupAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            val sessionManager = com.example.carelyo.data.session.SessionManager(requireContext())
            val user = sessionManager.getUserSession()
            if (user != null) {
                viewModel.requestVaccinationData(user.UserID)
            } else {
                binding.swipeRefreshLayout.isRefreshing = false
            }
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
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.mainContent.visibility = View.VISIBLE
                    updateUI(state)
                }
                is VaccineState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(requireContext(), "Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.children.observe(viewLifecycleOwner) { children ->
            // Children loaded
        }

        viewModel.availableVaccines.observe(viewLifecycleOwner) { vaccines ->
            // Vaccines loaded
        }

        viewModel.isFormReady.observe(viewLifecycleOwner) { isReady ->
            if (isReady) {
                // Form data is ready
            }
        }
    }

    private fun updateUI(state: VaccineState.Success) {
        groupAdapter.submitList(state.childGroups)
    }


    // ── View Vaccine Detail with Mark as Taken ─────────────────────────

    private fun showVaccineDetailDialog(item: VaccineScheduleItem) {
        val dialog = BottomSheetDialog(requireContext())
        val binding = DialogViewVaccineDetailBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setContentView(binding.root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        binding.tvVaccineTitle.text = item.vaccineName

        when (item.status) {
            VaccineStatus.DONE -> {
                binding.tvStatusBadge.text = "Completed"
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            }
            VaccineStatus.UPCOMING -> {
                binding.tvStatusBadge.text = "Upcoming"
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.info))
            }
            VaccineStatus.OVERDUE -> {
                binding.tvStatusBadge.text = "Overdue"
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
            }
        }

        binding.tvRecommendedAgeValue.text = item.ageRequirement

        if (item.status == VaccineStatus.DONE) {
            if (item.givenDate != null) {
                binding.tvDateGivenValue.text = item.givenDate.format(dateFormatter)
                binding.cvDateGiven.visibility = View.VISIBLE
            } else {
                binding.cvDateGiven.visibility = View.GONE
            }
            binding.btnMarkTaken.visibility = View.GONE
        } else {
            binding.cvDateGiven.visibility = View.GONE
            binding.btnMarkTaken.visibility = View.VISIBLE
            binding.btnMarkTaken.text = "Mark as Taken"
            binding.btnMarkTaken.isEnabled = true
        }

        // Show clinic and notes
        val fullNotes = item.description
        val clinicRegex = Regex("(?i)Clinic:\\s*([^\\n]+)")
        val match = clinicRegex.find(fullNotes)
        if (match != null) {
            val clinicVal = match.groupValues[1].trim()
            binding.cvClinic.visibility = View.VISIBLE
            binding.tvClinicValue.text = clinicVal
            val cleanNotes = fullNotes.replace(match.value, "").trim()
            binding.tvNotesValue.text = if (cleanNotes.isNotEmpty()) cleanNotes else "No additional notes"
        } else {
            binding.cvClinic.visibility = View.GONE
            binding.tvNotesValue.text = if (fullNotes.isNotEmpty()) fullNotes else "No notes available"
        }

        binding.btnMarkTaken.setOnClickListener {
            dialog.dismiss()
            showMarkVaccineTakenDialog(item)
        }

        binding.ibClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ── Mark Vaccine as Taken Dialog ──────────────────────────────────
    private fun showMarkVaccineTakenDialog(item: VaccineScheduleItem) {
        val dialog = BottomSheetDialog(requireContext())
        val binding = DialogMarkVaccineTakenBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setContentView(binding.root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        binding.tvVaccineTitle.text = item.vaccineName
        binding.tvSubtitleDetails.text = "${item.childName} · Administration Details"

        // Setup default date to today
        var selectedAdminDate = LocalDate.now()
        binding.tvSelectedDate.text = selectedAdminDate.format(dateFormatter)

        binding.btnDatePickerContainer.setOnClickListener {
            val datePicker = android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedAdminDate = LocalDate.of(year, month + 1, dayOfMonth)
                    binding.tvSelectedDate.text = selectedAdminDate.format(dateFormatter)
                },
                selectedAdminDate.year,
                selectedAdminDate.monthValue - 1,
                selectedAdminDate.dayOfMonth
            )
            datePicker.show()
        }

        // Setup default time to now
        var selectedAdminTime = java.time.LocalTime.now()
        binding.tvSelectedTime.text = selectedAdminTime.format(timeFormatter)

        binding.btnTimePickerContainer.setOnClickListener {
            val timePicker = android.app.TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    selectedAdminTime = java.time.LocalTime.of(hourOfDay, minute)
                    binding.tvSelectedTime.text = selectedAdminTime.format(timeFormatter)
                },
                selectedAdminTime.hour,
                selectedAdminTime.minute,
                true
            )
            timePicker.show()
        }

        // Setup Clinic Spinner — only show clinics from the database
        val fetchedClinics = viewModel.clinics.value
            ?.mapNotNull { it.clinic_name?.takeIf { name -> name.isNotBlank() } }
            ?: emptyList()

        val clinicOptions = mutableListOf("Select Clinic / Hospital (Optional)")
        clinicOptions.addAll(fetchedClinics)

        val spinnerAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            clinicOptions
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerClinic.adapter = spinnerAdapter

        binding.btnConfirmMarkTaken.setOnClickListener {
            val selectedPosition = binding.spinnerClinic.selectedItemPosition
            val chosenClinic = if (selectedPosition > 0) {
                clinicOptions[selectedPosition]
            } else {
                null
            }

            val userNotes = binding.etVaccineNotes.text?.toString()?.trim()
            val dateTime = java.time.LocalDateTime.of(selectedAdminDate, selectedAdminTime)
            val zonedDateTime = dateTime.atZone(java.time.ZoneId.systemDefault())

            viewModel.markVaccineAsTaken(
                childId = item.childId,
                vaccineId = item.vaccineId,
                administeredDate = selectedAdminDate.format(dbDateFormatter),
                administeredAt = zonedDateTime.format(dbDateTimeFormatter),
                clinicName = chosenClinic,
                notes = userNotes
            )

            Toast.makeText(requireContext(), "Vaccine marked as completed!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        binding.ibClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun calculateAge(dateOfBirth: String): String {
        return try {
            val birthDate = LocalDate.parse(dateOfBirth)
            val currentDate = LocalDate.now()
            val years = java.time.Period.between(birthDate, currentDate).years
            val months = java.time.Period.between(birthDate, currentDate).months
            when {
                years > 0 -> "$years year${if (years > 1) "s" else ""} ${months} month${if (months > 1) "s" else ""}"
                months > 0 -> "$months month${if (months > 1) "s" else ""} old"
                else -> "Newborn"
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ── Child Selection Adapter ────────────────────────────────────────────

class ChildSelectionAdapter(
    private val children: List<Child>,
    private val calculateAge: (String) -> String,
    private val onChildSelected: (Child) -> Unit
) : RecyclerView.Adapter<ChildSelectionAdapter.ViewHolder>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDialogChildSelectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val child = children[position]
        holder.bind(child, position == selectedPosition, calculateAge)
        holder.itemView.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousSelected)
            notifyItemChanged(selectedPosition)
            onChildSelected(child)
        }
    }

    override fun getItemCount() = children.size

    class ViewHolder(private val binding: ItemDialogChildSelectBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(child: Child, isSelected: Boolean, calculateAge: (String) -> String) {
            binding.tvChildName.text = child.full_name ?: "Unknown"
            binding.tvChildAge.text = child.date_of_birth?.let { calculateAge(it) } ?: ""
            binding.ivCheckSelection.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }
}

// ── Vaccine Selection Adapter ────────────────────────────────────────────

class VaccineSelectionAdapter(
    private var items: List<Vaccination>,
    private val onItemClick: (Vaccination) -> Unit
) : RecyclerView.Adapter<VaccineSelectionAdapter.ViewHolder>() {

    private var filteredItems: List<Vaccination> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDialogVaccineSelectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredItems[position]
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = filteredItems.size

    fun filter(query: String) {
        filteredItems = if (query.isEmpty()) {
            items
        } else {
            items.filter {
                it.vaccine_name?.contains(query, ignoreCase = true) == true ||
                        it.description?.contains(query, ignoreCase = true) == true
            }
        }
        notifyDataSetChanged()
    }

    class ViewHolder(
        private val binding: ItemDialogVaccineSelectBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(vaccine: Vaccination) {
            binding.tvSelectVaccineName.text = vaccine.vaccine_name ?: "Unknown Vaccine"
            binding.tvSelectVaccineDescription.text = vaccine.description ?: "No description"

            val weeks = vaccine.recommended_age_weeks ?: 0
            val recommendedText = when {
                weeks <= 4 -> "Recommended: At birth"
                weeks <= 12 -> "Recommended: $weeks weeks (${weeks / 4} months)"
                else -> "Recommended: ${weeks / 4} months"
            }
            binding.tvSelectVaccineRecommended.text = recommendedText
        }
    }
}

// ── Child Vaccine Group Adapter ──────────────────────────────────────────

class ChildVaccineGroupAdapter(
    private val onVaccineClick: (VaccineScheduleItem) -> Unit
) : RecyclerView.Adapter<ChildVaccineGroupAdapter.GroupViewHolder>() {

    private var groups: List<ChildVaccineGroup> = emptyList()

    fun submitList(newGroups: List<ChildVaccineGroup>) {
        groups = newGroups
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemChildVaccineGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount(): Int = groups.size

    inner class GroupViewHolder(
        private val binding: ItemChildVaccineGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: ChildVaccineGroup) {
            val child = group.child
            binding.tvGroupChildName.text = child.full_name ?: "Child"

            val subtitleParts = mutableListOf<String>()
            if (group.ageText.isNotEmpty()) {
                subtitleParts.add(group.ageText)
            }
            subtitleParts.add("${group.completedCount}/${group.totalCount} completed")
            binding.tvGroupChildSubtitle.text = subtitleParts.joinToString(" • ")

            val isFemale = child.gender?.equals("Female", ignoreCase = true) == true
            if (isFemale) {
                binding.ivChildAvatar.setImageResource(R.drawable.ic_avatar_female)
                binding.flChildAvatarContainer.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#009688"))
            } else {
                binding.ivChildAvatar.setImageResource(R.drawable.ic_avatar_male1)
                binding.flChildAvatarContainer.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#009688"))
            }

            val childVaccineAdapter = VaccineScheduleAdapter(onVaccineClick)
            binding.rvChildVaccines.apply {
                layoutManager = LinearLayoutManager(binding.root.context)
                adapter = childVaccineAdapter
            }
            childVaccineAdapter.submitList(group.items)
        }
    }
}

// ── Vaccine Schedule Adapter ─────────────────────────────────────────────

class VaccineScheduleAdapter(
    private val onItemClick: (VaccineScheduleItem) -> Unit
) : RecyclerView.Adapter<VaccineScheduleAdapter.ViewHolder>() {

    private var allItems: List<VaccineScheduleItem> = emptyList()

    fun submitList(newItems: List<VaccineScheduleItem>) {
        allItems = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVaccineScheduleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(allItems[position])
        holder.itemView.setOnClickListener {
            onItemClick(allItems[position])
        }
    }

    override fun getItemCount(): Int = allItems.size

    class ViewHolder(
        private val binding: ItemVaccineScheduleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VaccineScheduleItem) {
            val iconRes: Int
            val iconBgColor: Int
            val textColor: Int

            when (item.status) {
                VaccineStatus.DONE -> {
                    iconRes = R.drawable.ic_check
                    iconBgColor = R.color.success_soft
                    textColor = R.color.success
                }
                VaccineStatus.UPCOMING -> {
                    iconRes = R.drawable.ic_schedule
                    iconBgColor = R.color.info_soft
                    textColor = R.color.info
                }
                VaccineStatus.OVERDUE -> {
                    iconRes = R.drawable.ic_warning
                    iconBgColor = R.color.error_soft
                    textColor = R.color.error
                }
            }

            binding.ivStatusIcon.setImageResource(iconRes)
            binding.cvStatusIconBackground.setCardBackgroundColor(
                ContextCompat.getColor(binding.root.context, iconBgColor)
            )

            binding.tvVaccineName.text = item.vaccineName

            binding.tvChildNameBadge.visibility = View.GONE

            binding.tvVaccineAge.text = item.ageRequirement

            val dateText = if (item.status == VaccineStatus.DONE && item.givenDate != null) {
                val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
                "Given: ${item.givenDate.format(formatter)}"
            } else if (item.status == VaccineStatus.UPCOMING) {
                if (item.dueDate != null) {
                    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
                    "Due: ${item.dueDate.format(formatter)}"
                } else {
                    "Due: Not yet scheduled"
                }
            } else {
                if (item.dueDate != null) {
                    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
                    "Overdue since: ${item.dueDate.format(formatter)}"
                } else {
                    "Overdue: Please schedule"
                }
            }
            binding.tvVaccineStatusDate.text = dateText
            binding.tvVaccineStatusDate.setTextColor(
                ContextCompat.getColor(binding.root.context, textColor)
            )
        }
    }
}