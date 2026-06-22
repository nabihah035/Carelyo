package com.example.carelyo.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carelyo.R
import com.example.carelyo.data.entity.Allergie
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.UpcomingMedication
import com.example.carelyo.data.session.SessionManager
import com.example.carelyo.databinding.FragmentHomeBinding
import com.example.carelyo.ui.dashboard.adapters.MedicationAdapter
import com.example.carelyo.ui.dashboard.adapters.UpcomingEventAdapter
import com.example.carelyo.ui.dashboard.models.MedicationItem
import com.example.carelyo.ui.dashboard.models.UpcomingEvent
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.*
import android.widget.ArrayAdapter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels()
    private lateinit var sessionManager: SessionManager
    private var selectedChild: Child? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Adapters
    private lateinit var medicationAdapter: MedicationAdapter
    private lateinit var upcomingEventAdapter: UpcomingEventAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())

        // Initialize adapters
        setupAdapters()

        observeViewModel()
        loadDashboardData()

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.invalidateCache()
            loadDashboardData()
        }
    }

    private fun setupAdapters() {
        // Medication adapter
        medicationAdapter = MedicationAdapter(
            items = emptyList(),
            onItemCheckChanged = { item, isChecked ->
                Toast.makeText(requireContext(), "${item.name} ${if (isChecked) "completed" else "unchecked"}", Toast.LENGTH_SHORT).show()
                // Update progress
                updateMedicationProgress()
            }
        )
        binding.rvMedications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = medicationAdapter
        }

        // Upcoming events adapter
        upcomingEventAdapter = UpcomingEventAdapter(
            items = emptyList(),
            onItemClick = { event ->
                Toast.makeText(requireContext(), "Clicked: ${event.title}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvUpcomingEvents.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = upcomingEventAdapter
        }
    }

    private fun observeViewModel() {
        // Observe children list
        viewModel.childrenList.observe(viewLifecycleOwner) { children ->
            if (!children.isNullOrEmpty()) {
                binding.childSelectorMenu.visibility = View.VISIBLE
                renderChildSelector(children)
                if (selectedChild == null) {
                    selectChild(children[0])
                }
                binding.progressBar.visibility = View.GONE
                showContent()
            } else {
                showEmptyState()
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
                hideContent()
            } else {
                binding.swipeRefreshLayout.isRefreshing = false
                if (viewModel.childrenList.value != null) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
                showEmptyState()
            }
        }

        // Observe upcoming vaccinations
        viewModel.upcomingVaccinations.observe(viewLifecycleOwner) { vaccines ->
            updateUpcomingEventsFromData()
        }

        // Observe upcoming medications
        viewModel.upcomingMedications.observe(viewLifecycleOwner) { medications ->
            updateMedicationListFromData(medications)
            updateUpcomingEventsFromData()
        }

        // Observe upcoming appointments
        viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
            updateUpcomingEventsFromData()
        }

        // Observe child allergies
        viewModel.childAllergies.observe(viewLifecycleOwner) { allergies ->
            updateAllergyAlert(allergies)
        }
    }

    private fun renderChildSelector(children: List<Child>) {
        val childNames = children.map { it.full_name ?: "Unnamed Child" }

        // Create a standard Android dropdown list layout adapter
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            childNames
        )

        // Bind the options list data directly to the XML view element
        binding.actvChildSelector.setAdapter(adapter)

        // Set default display name text if it hasn't been chosen yet
        if (selectedChild == null && children.isNotEmpty()) {
            binding.actvChildSelector.setText(childNames.first(), false)
        } else {
            binding.actvChildSelector.setText(selectedChild?.full_name ?: "Select Child", false)
        }

        // Capture the selection index switch directly from the XML element click
        binding.actvChildSelector.setOnItemClickListener { _, _, position, _ ->
            selectChild(children[position])
        }
    }

    private fun selectChild(child: Child) {
        selectedChild = child
        // FIXED: Set the text directly inside the AutoCompleteTextView component
        binding.actvChildSelector.setText(child.full_name ?: "Unnamed Child", false)
        displayChildDetails(child)
        viewModel.fetchAllDataForChild(child.ChildID)
    }

    private fun displayChildDetails(child: Child) {
        // Set profile image based on gender
        val drawableId = if (child.gender == "Female") {
            R.drawable.ic_avatar_female
        } else {
            R.drawable.ic_avatar_male1
        }
        binding.ivChildProfile.setImageResource(drawableId)

        // Set child name (Age mapping removed here)
        binding.tvChildName.text = child.full_name ?: "Unnamed Child"

        // Set growth status
        binding.tvGrowthStatus.text = "Normal Growth"
        binding.tvGrowthStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.growth_normal))

        // Set vitals
        binding.tvWeightValue.text = if (child.weight != null) "${child.weight} kg" else "--"
        binding.tvHeightValue.text = if (child.height != null) "${child.height} cm" else "--"
        binding.tvBloodTypeValue.text = child.blood_type ?: "--"
    }

    private fun updateAllergyAlert(allergies: List<Allergie>) {
        if (allergies.isEmpty()) {
            binding.cardAllergyAlert.visibility = View.GONE
            return
        }

        binding.cardAllergyAlert.visibility = View.VISIBLE
        binding.chipGroupAllergies.removeAllViews()

        allergies.forEach { allergy ->
            val chip = Chip(requireContext()).apply {
                text = "${allergy.allergy_name ?: "Unknown"} (${allergy.severity ?: "Unknown"})"
                isCloseIconVisible = false

                when (allergy.severity?.lowercase()) {
                    "severe", "high" -> setChipBackgroundColorResource(R.color.allergy_severe)
                    "moderate", "medium" -> setChipBackgroundColorResource(R.color.allergy_moderate)
                    "mild", "low" -> setChipBackgroundColorResource(R.color.allergy_mild)
                    else -> setChipBackgroundColorResource(R.color.allergy_unknown)
                }
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            }
            binding.chipGroupAllergies.addView(chip)
        }

        binding.chipGroupAllergies.visibility = View.VISIBLE

        val titleText = if (allergies.size == 1) {
            "Allergy Alert (${allergies.size})"
        } else {
            "Allergy Alerts (${allergies.size})"
        }
        binding.tvAllergyTitle.text = titleText
    }

    private fun updateMedicationListFromData(medications: List<UpcomingMedication>) {
        if (medications.isEmpty()) {
            binding.tvMedicationProgress.text = "No medications"
            medicationAdapter.updateItems(emptyList())
            return
        }

        val items = medications.map { med ->
            MedicationItem(
                name = med.medicationName,
                dosage = med.dosage ?: "",
                time = formatTime(med.scheduledTime),
                isCompleted = false,
                childId = med.childId
            )
        }

        medicationAdapter.updateItems(items)
        updateMedicationProgress()
    }

    private fun updateMedicationProgress() {
        val items = medicationAdapter.getItems()
        val total = items.size
        val completed = items.count { it.isCompleted }
        binding.tvMedicationProgress.text = if (total > 0) {
            "$completed/$total done"
        } else {
            "No medications"
        }
    }

    private fun updateUpcomingEventsFromData() {
        val events = mutableListOf<UpcomingEvent>()

        viewModel.upcomingVaccinations.value?.forEach { vaccine ->
            events.add(
                UpcomingEvent(
                    type = UpcomingEvent.Type.VACCINATION,
                    title = vaccine.vaccineName,
                    description = "Due: ${formatDate(vaccine.dueDate)}",
                    date = formatDateShort(vaccine.dueDate),
                    childId = vaccine.childId
                )
            )
        }

        viewModel.upcomingMedications.value?.forEach { med ->
            events.add(
                UpcomingEvent(
                    type = UpcomingEvent.Type.MEDICATION,
                    title = med.medicationName,
                    description = "${med.dosage ?: ""} at ${formatTime(med.scheduledTime)}",
                    date = formatTimeShort(med.scheduledTime),
                    childId = med.childId
                )
            )
        }

        viewModel.upcomingAppointments.value?.forEach { app ->
            events.add(
                UpcomingEvent(
                    type = UpcomingEvent.Type.APPOINTMENT,
                    title = app.clinicName ?: "Clinic Visit",
                    description = app.appointmentDate ?: "",
                    date = formatDateShort(app.appointmentDate),
                    childId = app.childId
                )
            )
        }

        events.sortBy { it.date }

        if (events.isEmpty()) {
            binding.tvUpcomingEmpty.visibility = View.VISIBLE
            binding.rvUpcomingEvents.visibility = View.GONE
        } else {
            binding.tvUpcomingEmpty.visibility = View.GONE
            binding.rvUpcomingEvents.visibility = View.VISIBLE
            upcomingEventAdapter.updateItems(events)
        }
    }

    private fun showContent() {
        binding.cardChildDetails.visibility = View.VISIBLE
        binding.cardMedications.visibility = View.VISIBLE
        binding.cardUpcoming.visibility = View.VISIBLE
    }

    private fun hideContent() {
        binding.cardChildDetails.visibility = View.GONE
        binding.cardMedications.visibility = View.GONE
        binding.cardAllergyAlert.visibility = View.GONE
        binding.cardUpcoming.visibility = View.GONE
    }

    private fun showEmptyState() {
        // FIXED: Set fallback error texts to the new exposed menu items safely
        binding.actvChildSelector.setText("No Children Registered", false)
        binding.cardChildDetails.visibility = View.GONE
        binding.cardMedications.visibility = View.GONE
        binding.cardAllergyAlert.visibility = View.GONE
        binding.cardUpcoming.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun formatDate(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "Date not set"
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val output = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            output.format(input.parse(dateString) ?: return dateString)
        } catch (e: Exception) { dateString }
    }

    private fun formatDateShort(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "TBD"
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val output = SimpleDateFormat("MMM dd", Locale.getDefault())
            output.format(input.parse(dateString) ?: return dateString)
        } catch (e: Exception) { dateString }
    }

    private fun formatTime(timeString: String?): String {
        if (timeString.isNullOrEmpty()) return "Time not set"

        val isoFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ"
        )
        for (fmt in isoFormats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                val parsed = sdf.parse(timeString)
                if (parsed != null) {
                    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(parsed)
                }
            } catch (_: Exception) { }
        }

        return try {
            val input = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val output = SimpleDateFormat("h:mm a", Locale.getDefault())
            output.format(input.parse(timeString) ?: return timeString)
        } catch (e: Exception) { timeString }
    }

    private fun formatTimeShort(timeString: String?): String {
        if (timeString.isNullOrEmpty()) return "TBD"
        val formatted = formatTime(timeString)
        return if (formatted == timeString) "TBD" else formatted
    }

    private fun loadDashboardData() {
        val currentUser = sessionManager.getUserSession()
        if (currentUser != null) {
            binding.progressBar.visibility = View.VISIBLE
            hideContent()
            viewModel.loadDashboardData()
        } else {
            showEmptyState()
            binding.actvChildSelector.setText("Please Login", false)
            Toast.makeText(requireContext(), "Please log in to view your children", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}