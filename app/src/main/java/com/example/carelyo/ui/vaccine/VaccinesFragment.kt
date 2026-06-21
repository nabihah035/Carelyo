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
import com.example.carelyo.databinding.DialogAddChildVaccineBinding
import com.example.carelyo.databinding.DialogAddVaccineBinding
import com.example.carelyo.databinding.DialogChooseVaccineBinding
import com.example.carelyo.databinding.DialogViewVaccineDetailBinding
import com.example.carelyo.databinding.FragmentVaccinesBinding
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
    private lateinit var scheduleAdapter: VaccineScheduleAdapter
    private var selectedChild: Child? = null
    private var selectedVaccine: Vaccination? = null
    private var selectedDate: String? = null
    private var selectedTime: String? = null

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
        setupChipFilters()
        observeViewModel()
        setupAddButton()
        setupSwipeRefresh()

        // Request data
        val sessionManager = com.example.carelyo.data.session.SessionManager(requireContext())
        val user = sessionManager.getUserSession()
        user?.let {
            viewModel.requestVaccinationData(it.UserID)
        }
    }

    private fun setupRecyclerView() {
        scheduleAdapter = VaccineScheduleAdapter { item ->
            showVaccineDetailDialog(item)
        }
        binding.rvVaccineSchedule.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }
    }

    private fun setupChipFilters() {
        binding.chipGroupStatus.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            when (checkedIds.first()) {
                R.id.chipAll -> scheduleAdapter.filterByStatus(null)
                R.id.chipDone -> scheduleAdapter.filterByStatus(VaccineStatus.DONE)
                R.id.chipUpcoming -> scheduleAdapter.filterByStatus(VaccineStatus.UPCOMING)
                R.id.chipOverdue -> scheduleAdapter.filterByStatus(VaccineStatus.OVERDUE)
            }
        }
    }

    private fun setupAddButton() {
        binding.btnAddVaccine.setOnClickListener {
            showChildSelectionDialog()
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
        binding.tvDoneCount.text = state.completedCount.toString()
        binding.tvUpcomingCount.text = state.upcomingCount.toString()
        binding.tvOverdueCount.text = state.overdueCount.toString()
        scheduleAdapter.submitList(state.scheduleItems)
    }

    // ── Step 1: Select Child ────────────────────────────────────────────
    private fun showChildSelectionDialog() {
        val children = viewModel.children.value ?: emptyList()
        if (children.isEmpty()) {
            Toast.makeText(requireContext(), "No children available. Please add a child first.", Toast.LENGTH_LONG).show()
            return
        }

        val dialog = BottomSheetDialog(requireContext())
        val binding = DialogAddChildVaccineBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setContentView(binding.root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        var tempSelectedChild: Child? = null

        val adapter = ChildSelectionAdapter(children, ::calculateAge) { child ->
            tempSelectedChild = child
        }
        binding.rvChildrenSelection.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        binding.btnDialogClose.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnContinue.setOnClickListener {
            if (tempSelectedChild == null) {
                Toast.makeText(requireContext(), "Please select a child", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedChild = tempSelectedChild
            dialog.dismiss()
            showVaccineSelectionDialog()
        }

        dialog.show()
    }

    // ── Step 2: Select Vaccine ──────────────────────────────────────────
    private fun showVaccineSelectionDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val binding = DialogChooseVaccineBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setContentView(binding.root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        // Set child name
        val child = selectedChild
        if (child != null) {
            val emoji = if (child.gender == "Female") "👧" else "👦"
            binding.tvChildEmoji.text = emoji
            binding.tvChildNameSubtitle.text = child.full_name ?: "Unknown"
        }

        // Get already taken vaccine IDs for this child
        val takenVaccineIds = viewModel.getTakenVaccineIdsForChild(child?.ChildID ?: 0)

        // Setup vaccine list adapter - filter out already taken vaccines
        val allVaccines = viewModel.availableVaccines.value ?: emptyList()
        val availableVaccines = allVaccines.filter {
            takenVaccineIds?.contains(it.VaccineID) != true
        }

        if (availableVaccines.isEmpty()) {
            Toast.makeText(requireContext(), "All vaccines have been taken for this child", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            return
        }

        val vaccineAdapter = VaccineSelectionAdapter(availableVaccines) { vaccine ->
            selectedVaccine = vaccine
            dialog.dismiss()
            showAddVaccineDetailsDialog()
        }
        binding.rvVaccineSelection.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = vaccineAdapter
        }

        // Search functionality
        binding.etSearchVaccine.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                vaccineAdapter.filter(s.toString())
            }
        })

        binding.btnBack.setOnClickListener {
            dialog.dismiss()
            showChildSelectionDialog()
        }

        dialog.show()
    }

    // ── Step 3: Add Vaccine Details (Date) ─────────────────────────────
    private fun showAddVaccineDetailsDialog() {
        if (selectedVaccine == null) {
            Toast.makeText(requireContext(), "Please select a vaccine first", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(requireContext())
        val binding = DialogAddVaccineBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setContentView(binding.root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        // Set vaccine info
        val vaccine = selectedVaccine!!
        binding.tvVaccineTitle.text = vaccine.vaccine_name ?: "Unknown Vaccine"
        binding.tvVaccineLongDescription.text = vaccine.description ?: "No description available"

        // Set child info
        val child = selectedChild
        if (child != null) {
            val emoji = if (child.gender == "Female") "👧" else "👦"
            binding.tvChildEmoji.text = "$emoji "
            val recommendedText = vaccine.recommended_age_weeks?.let { weeks ->
                when {
                    weeks <= 4 -> "Recommended: At birth"
                    weeks <= 12 -> "Recommended: $weeks weeks (${weeks / 4} months)"
                    else -> "Recommended: ${weeks / 4} months"
                }
            } ?: ""
            binding.tvSubtitleDetails.text = "${child.full_name ?: "Unknown"} · $recommendedText"
        }

        // Setup status spinner
        val statusSpinner = dialog.findViewById<android.widget.Spinner>(R.id.spinnerStatus)
        if (statusSpinner != null) {
            val statusOptions = arrayOf("Done", "Upcoming", "Overdue")
            val adapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                statusOptions
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            statusSpinner.adapter = adapter
            
            // Auto-select "Done" for past dates, "Upcoming" for future dates? Let user choose.
        }

        // Setup date picker
        binding.btnDatePickerContainer.setOnClickListener {
            showDatePickerDialog { date ->
                selectedDate = date
                binding.tvSelectedDate.text = date
                binding.tvSelectedDate.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            }
        }

        // Setup time picker
        binding.btnTimePickerContainer.setOnClickListener {
            showTimePickerDialog { time ->
                selectedTime = time
                binding.tvSelectedTime.text = time
                binding.tvSelectedTime.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            }
        }

        // Back button - go back to vaccine selection
        binding.btnBackToVaccines.setOnClickListener {
            dialog.dismiss()
            showVaccineSelectionDialog()
        }

        binding.btnSaveRecord.setOnClickListener {
            saveVaccineRecord(dialog)
        }

        dialog.show()
    }

    private fun showDatePickerDialog(onDateSelected: (String) -> Unit) {
        val datePicker = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val date = LocalDate.of(year, month + 1, dayOfMonth)
                val formattedDate = date.format(dateFormatter)
                onDateSelected(formattedDate)
            },
            LocalDate.now().year,
            LocalDate.now().monthValue - 1,
            LocalDate.now().dayOfMonth
        )
        datePicker.show()
    }

    private fun showTimePickerDialog(onTimeSelected: (String) -> Unit) {
        val timePicker = android.app.TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val time = java.time.LocalTime.of(hourOfDay, minute)
                val formattedTime = time.format(timeFormatter)
                onTimeSelected(formattedTime)
            },
            java.time.LocalTime.now().hour,
            java.time.LocalTime.now().minute,
            true
        )
        timePicker.show()
    }

    private fun saveVaccineRecord(dialog: BottomSheetDialog) {
        val child = selectedChild
        val vaccine = selectedVaccine

        if (child == null) {
            Toast.makeText(requireContext(), "Please select a child", Toast.LENGTH_SHORT).show()
            return
        }

        if (vaccine == null) {
            Toast.makeText(requireContext(), "Please select a vaccine", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedDate == null) {
            Toast.makeText(requireContext(), "Please select a date", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedTime == null) {
            Toast.makeText(requireContext(), "Please select a time", Toast.LENGTH_SHORT).show()
            return
        }

        val date = try {
            LocalDate.parse(selectedDate, dateFormatter)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Invalid date format", Toast.LENGTH_SHORT).show()
            return
        }

        val time = try {
            java.time.LocalTime.parse(selectedTime, timeFormatter)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Invalid time format", Toast.LENGTH_SHORT).show()
            return
        }

        val dateTime = java.time.LocalDateTime.of(date, time)
        val zonedDateTime = dateTime.atZone(java.time.ZoneId.systemDefault())

        // Get status
        val statusSpinner = dialog.findViewById<android.widget.Spinner>(R.id.spinnerStatus)
        val selectedStatus = statusSpinner?.selectedItem?.toString() ?: "Done"

        // Get notes
        val notesEditText = dialog.findViewById<android.widget.EditText>(R.id.etVaccineNotes)
        val notes = notesEditText?.text?.toString() ?: ""

        val childVaccine = ChildVaccine(
            ChildID = child.ChildID,
            VaccineID = vaccine.VaccineID,
            status = selectedStatus,
            administered_date = date.format(dbDateFormatter),
            administered_at = zonedDateTime.format(dbDateTimeFormatter),
            notes = notes
        )

        viewModel.addChildVaccine(childVaccine)

        Toast.makeText(requireContext(), "Vaccine record saved successfully!", Toast.LENGTH_SHORT).show()
        dialog.dismiss()

        // Refresh data
        val sessionManager = com.example.carelyo.data.session.SessionManager(requireContext())
        val user = sessionManager.getUserSession()
        user?.let {
            viewModel.requestVaccinationData(it.UserID)
        }

        // Reset selected values
        selectedDate = null
        selectedTime = null
        selectedVaccine = null
        selectedChild = null
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

        // Show existing notes if any
        binding.tvNotesValue.text = if (item.description.isNotEmpty()) item.description else "No notes available"

        binding.btnMarkTaken.setOnClickListener {
            // Directly update the vaccine status to Done
            viewModel.markVaccineAsTaken(
                childId = item.childId,
                vaccineId = item.vaccineId,
                notes = "Marked as taken on ${LocalDate.now().format(dateFormatter)}"
            )
            dialog.dismiss()
            Toast.makeText(requireContext(), "Vaccine marked as taken!", Toast.LENGTH_SHORT).show()
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

// ── Vaccine Schedule Adapter ─────────────────────────────────────────────

class VaccineScheduleAdapter(
    private val onItemClick: (VaccineScheduleItem) -> Unit
) : RecyclerView.Adapter<VaccineScheduleAdapter.ViewHolder>() {

    private var allItems: List<VaccineScheduleItem> = emptyList()
    private var filteredItems: List<VaccineScheduleItem> = emptyList()
    private var filterStatus: VaccineStatus? = null

    fun submitList(newItems: List<VaccineScheduleItem>) {
        allItems = newItems
        applyFilter()
    }

    fun filterByStatus(status: VaccineStatus?) {
        filterStatus = status
        applyFilter()
    }

    private fun applyFilter() {
        filteredItems = if (filterStatus == null) {
            allItems
        } else {
            allItems.filter { it.status == filterStatus }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVaccineScheduleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(filteredItems[position])
        holder.itemView.setOnClickListener {
            onItemClick(filteredItems[position])
        }
    }

    override fun getItemCount(): Int = filteredItems.size

    class ViewHolder(
        private val binding: ItemVaccineScheduleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VaccineScheduleItem) {
            val iconRes: Int
            val iconBgColor: Int
            val textColor: Int

            when (item.status) {
                VaccineStatus.DONE -> {
                    iconRes = R.drawable.ic_check_circle
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
            binding.ivStatusIcon.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, iconBgColor)
            )

            binding.tvVaccineName.text = item.vaccineName
            binding.tvVaccineName.setTextColor(
                ContextCompat.getColor(binding.root.context, textColor)
            )

            binding.tvChildNameBadge.visibility = View.VISIBLE
            binding.tvChildNameBadge.text = item.childName

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