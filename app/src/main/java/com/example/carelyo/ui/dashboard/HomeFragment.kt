package com.example.carelyo.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carelyo.R
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.UpcomingMedication
import com.example.carelyo.data.session.SessionManager
import com.example.carelyo.databinding.FragmentHomeBinding
import com.example.carelyo.ui.dashboard.adapters.MedicationAdapter
import com.example.carelyo.ui.dashboard.adapters.UpcomingEventAdapter
import com.example.carelyo.ui.dashboard.models.MedicationItem
import com.example.carelyo.ui.dashboard.models.UpcomingEvent
import java.text.SimpleDateFormat
import java.util.*

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
    }

    private fun setupAdapters() {
        // Medication adapter
        medicationAdapter = MedicationAdapter(
            items = emptyList(),
            onItemCheckChanged = { item, isChecked ->
                val status = if (isChecked) "completed" else "unchecked"
                Toast.makeText(requireContext(), "${item.name} $status", Toast.LENGTH_SHORT).show()
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
                if (viewModel.childrenList.value != null) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        // Observe error messages - FIXED: removed postValue() call
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
                showEmptyState()
                // The error will be cleared by the ViewModel when new data is loaded
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
    }

    private fun renderChildSelector(children: List<Child>) {
        val childNames = children.map { it.full_name ?: "Unnamed Child" }

        // FIX: Show the currently selected child's name if it exists; otherwise fall back to the first child
        binding.tvChildSelectorText.text = selectedChild?.full_name ?: childNames.firstOrNull() ?: "Select Child"

        binding.ivDropdownIcon.visibility = if (children.size > 1) View.VISIBLE else View.GONE

        binding.childSelectorContainer.setOnClickListener {
            if (children.size > 1) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Select Child")
                    .setItems(childNames.toTypedArray()) { _, which ->
                        selectChild(children[which])
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun selectChild(child: Child) {
        selectedChild = child
        binding.tvChildSelectorText.text = child.full_name ?: "Unnamed Child"
        displayChildDetails(child)
        fetchAllergyAlert(child.ChildID)
        viewModel.fetchUpcomingDataForChild(child.ChildID)
    }

    private fun displayChildDetails(child: Child) {
        val drawableId = if (child.gender == "Female") {
            R.drawable.ic_avatar_female
        } else {
            R.drawable.ic_avatar_male1
        }
        binding.ivChildProfile.setImageResource(drawableId)

        binding.tvChildName.text = child.full_name ?: "Unnamed Child"
        binding.tvChildAge.text = calculateAge(child.date_of_birth)

        binding.tvGrowthStatus.text = "Normal Growth"
        binding.tvGrowthStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.growth_normal))

        binding.tvWeightValue.text = if (child.weight != null) "${child.weight} kg" else "--"
        binding.tvHeightValue.text = if (child.height != null) "${child.height} cm" else "--"
        binding.tvBloodTypeValue.text = child.blood_type ?: "--"
    }

    private fun calculateAge(dateOfBirth: String?): String {
        if (dateOfBirth.isNullOrEmpty()) return "Age not recorded"
        return try {
            val birthDate = dateFormat.parse(dateOfBirth) ?: return "Age not recorded"
            val diff = Date().time - birthDate.time
            val years = (diff / (1000.0 * 60 * 60 * 24 * 365.25)).toInt()
            val months = ((diff % (1000.0 * 60 * 60 * 24 * 365.25)) / (1000.0 * 60 * 60 * 24 * 30.44)).toInt()
            when {
                years > 0 -> "$years year${if (years > 1) "s" else ""} old"
                months > 0 -> "$months month${if (months > 1) "s" else ""} old"
                else -> {
                    val days = (diff / (1000.0 * 60 * 60 * 24)).toInt()
                    "$days day${if (days > 1) "s" else ""} old"
                }
            }
        } catch (e: Exception) { "Age calculation error" }
    }

    private fun fetchAllergyAlert(childId: Int) {
        // TODO: Fetch allergies from ALLERGIE table
        binding.cardAllergyAlert.visibility = View.GONE
    }

    private fun updateMedicationListFromData(medications: List<UpcomingMedication>) {
        if (medications.isEmpty()) {
            binding.tvMedicationProgress.text = "0/0 done"
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
        binding.tvMedicationProgress.text = "0/${items.size} done"
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
        binding.cardAllergyAlert.visibility = View.VISIBLE
        binding.cardUpcoming.visibility = View.VISIBLE
    }

    private fun hideContent() {
        binding.cardChildDetails.visibility = View.GONE
        binding.cardMedications.visibility = View.GONE
        binding.cardAllergyAlert.visibility = View.GONE
        binding.cardUpcoming.visibility = View.GONE
    }

    private fun showEmptyState() {
        binding.tvChildSelectorText.text = "No Children Registered"
        binding.cardChildDetails.visibility = View.GONE
        binding.cardMedications.visibility = View.GONE
        binding.cardAllergyAlert.visibility = View.GONE
        binding.cardUpcoming.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.ivDropdownIcon.visibility = View.GONE
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
            binding.tvChildSelectorText.text = "Please Login"
            Toast.makeText(requireContext(), "Please log in to view your children", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}