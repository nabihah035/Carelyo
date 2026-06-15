package com.example.carelyo.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.session.SessionManager
import com.example.carelyo.databinding.FragmentHomeBinding
import com.example.carelyo.ui.health.HealthRecordsActivity
import com.example.carelyo.ui.medical.DoctorSummaryActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels()
    private lateinit var sessionManager: SessionManager
    private var selectedChild: Child? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ── Development preview dummy data ────────────────────────────────────────
    // These are shown immediately on launch so the layout is visible while
    // waiting for real Supabase data. Matches the IDs in your dummy SQL inserts.
    private val dummyChildren = listOf(
        Child(ChildID = 1, Parent_ID = 5, full_name = "Aisyah Hana",  date_of_birth = "2021-03-15", gender = "Female", blood_type = "A+"),
        Child(ChildID = 2, Parent_ID = 5, full_name = "Adam Rayyan",  date_of_birth = "2019-11-02", gender = "Male",   blood_type = "O+")
    )
    private val dummyVaccinations = listOf(
        UpcomingVaccination("MMR (Pending)", null, 2)
    )
    private val dummyMedications = listOf(
        UpcomingMedication("Paracetamol Syrup", "5ml", "2026-06-05T08:00:00+08:00", 1),
        UpcomingMedication("Vitamin C Gummies", "1 gummy", "2026-06-05T09:00:00+08:00", 2)
    )
    private val dummyAppointments = listOf(
        UpcomingAppointment("Sunway Medical Clinic", "2026-06-10", "10:00:00", 1),
        UpcomingAppointment("Klinik Kanak-Kanak PJ", "2026-06-15", "14:30:00", 2)
    )
    // ─────────────────────────────────────────────────────────────────────────

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

        // Show dummy data immediately — layout is never blank during development
        loadDummyDataForPreview()

        setupClickListeners()
        observeViewModel()
        loadDashboardData()
    }

    // Populate every UI section with local dummy data so the layout previews correctly
    private fun loadDummyDataForPreview() {
        renderChildList(dummyChildren)
        selectChild(dummyChildren[0], isPreview = true)
        updateVaccinationDueSection(dummyVaccinations)
        updateMedicationReminderSection(dummyMedications)
        updateClinicAppointmentSection(dummyAppointments)
    }

    private fun setupClickListeners() {
        binding.btnHealthRecords.setOnClickListener {
            selectedChild?.let { child ->
                startActivity(
                    Intent(requireContext(), HealthRecordsActivity::class.java)
                        .putExtra("CHILD_ID", child.ChildID)
                        .putExtra("CHILD_NAME", child.full_name)
                )
            } ?: Toast.makeText(requireContext(), "Please select a child first", Toast.LENGTH_SHORT).show()
        }

        binding.btnDoctorSummary.setOnClickListener {
            selectedChild?.let { child ->
                startActivity(
                    Intent(requireContext(), DoctorSummaryActivity::class.java)
                        .putExtra("CHILD_ID", child.ChildID)
                        .putExtra("CHILD_NAME", child.full_name)
                )
            } ?: Toast.makeText(requireContext(), "Please select a child first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        // BUG FIX #1 (visible result): this observer now fires because ChildRecordAgent
        // correctly broadcasts INFORM_CHILD_PROFILES_READY to DashboardViewModelAgent.
        viewModel.childrenList.observe(viewLifecycleOwner) { children ->
            if (!children.isNullOrEmpty()) {
                renderChildList(children)
                // Only replace selected child if it was still a dummy (ChildID matches dummy)
                val isDummySelected = selectedChild?.Parent_ID == 5 &&
                        dummyChildren.any { it.ChildID == selectedChild?.ChildID }
                if (isDummySelected || selectedChild == null) {
                    selectChild(children[0])
                }
            } else {
                // Real data returned empty — show a clear "no children" state
                binding.tvChildSelectorText.text = "No Children Registered"
                binding.cardChildDetails.visibility = View.GONE
                binding.cardQuickActions.visibility = View.GONE
                binding.cardUpcoming.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Only show the spinner if dummy data is NOT already showing
            if (isLoading && selectedChild == null) {
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.upcomingVaccinations.observe(viewLifecycleOwner) { vaccines ->
            updateVaccinationDueSection(vaccines)
        }

        viewModel.upcomingMedications.observe(viewLifecycleOwner) { medications ->
            updateMedicationReminderSection(medications)
        }

        viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
            updateClinicAppointmentSection(appointments)
        }
    }

    // ── Child selector ────────────────────────────────────────────────────────

    private fun renderChildList(children: List<Child>) {
        binding.cardChildDetails.visibility = View.VISIBLE
        binding.cardQuickActions.visibility = View.VISIBLE
        binding.cardUpcoming.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE

        val childNames = children.map { it.full_name ?: "Unnamed Child" }
        binding.tvChildSelectorText.text = childNames.firstOrNull() ?: "Select Child"
        binding.ivDropdownIcon.visibility = if (children.size > 1) View.VISIBLE else View.GONE

        binding.childSelectorContainer.setOnClickListener {
            if (children.size > 1) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Select Child")
                    .setItems(childNames.toTypedArray()) { _, which -> selectChild(children[which]) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun selectChild(child: Child, isPreview: Boolean = false) {
        selectedChild = child
        binding.tvChildSelectorText.text = child.full_name ?: "Unnamed Child"
        displayChildDetails(child)
        if (!isPreview) {
            viewModel.fetchUpcomingDataForChild(child.ChildID)
        }
    }

    // ── Child details card ────────────────────────────────────────────────────

    private fun displayChildDetails(child: Child) {
        binding.tvChildName.text = child.full_name ?: "Unnamed Child"
        binding.tvChildAge.text = calculateAge(child.date_of_birth)
        binding.tvBloodTypeValue.text = child.blood_type ?: "Not specified"
        binding.tvWeightValue.text = "Not recorded"
        binding.tvHeightValue.text = "Not recorded"

        viewModel.fetchChildGrowthData(child.ChildID) { weight, height ->
            activity?.runOnUiThread {
                binding.tvWeightValue.text = if (weight > 0) "$weight kg" else "Not recorded"
                binding.tvHeightValue.text = if (height > 0) "$height cm" else "Not recorded"
            }
        }
    }

    private fun calculateAge(dateOfBirth: String?): String {
        if (dateOfBirth.isNullOrEmpty()) return "Age not recorded"
        return try {
            val birthDate = dateFormat.parse(dateOfBirth) ?: return "Age not recorded"
            val diff = Date().time - birthDate.time
            val years  = floor(diff / (1000.0 * 60 * 60 * 24 * 365.25)).toInt()
            val months = floor((diff % (1000.0 * 60 * 60 * 24 * 365.25)) / (1000.0 * 60 * 60 * 24 * 30.44)).toInt()
            when {
                years  > 0 -> "$years year${if (years > 1) "s" else ""} old"
                months > 0 -> "$months month${if (months > 1) "s" else ""} old"
                else -> {
                    val days = floor(diff / (1000.0 * 60 * 60 * 24)).toInt()
                    "$days day${if (days > 1) "s" else ""} old"
                }
            }
        } catch (e: Exception) { "Age calculation error" }
    }

    // ── Upcoming section renderers ────────────────────────────────────────────

    private fun updateVaccinationDueSection(vaccines: List<UpcomingVaccination>) {
        if (vaccines.isEmpty()) {
            binding.tvVaccinationDue.text = "No upcoming vaccinations"
            binding.tvVaccinationCount.visibility = View.GONE
            return
        }
        val first = vaccines.first()
        val dateStr = if (first.dueDate != null) " — Due: ${formatDate(first.dueDate)}" else ""
        binding.tvVaccinationDue.text = "${first.vaccineName}$dateStr"
        if (vaccines.size > 1) {
            binding.tvVaccinationCount.visibility = View.VISIBLE
            binding.tvVaccinationCount.text = "+${vaccines.size - 1} more"
        } else {
            binding.tvVaccinationCount.visibility = View.GONE
        }
    }

    private fun updateMedicationReminderSection(medications: List<UpcomingMedication>) {
        if (medications.isEmpty()) {
            binding.tvMedicationReminder.text = "No active medications"
            binding.tvMedicationCount.visibility = View.GONE
            return
        }
        val first = medications.first()
        binding.tvMedicationReminder.text =
            "${first.medicationName} — ${first.dosage ?: ""} at ${formatTime(first.scheduledTime)}"
        if (medications.size > 1) {
            binding.tvMedicationCount.visibility = View.VISIBLE
            binding.tvMedicationCount.text = "+${medications.size - 1} more"
        } else {
            binding.tvMedicationCount.visibility = View.GONE
        }
    }

    private fun updateClinicAppointmentSection(appointments: List<UpcomingAppointment>) {
        if (appointments.isEmpty()) {
            binding.tvClinicAppointment.text = "No upcoming appointments"
            binding.tvAppointmentCount.visibility = View.GONE
            return
        }
        val first = appointments.first()
        binding.tvClinicAppointment.text =
            "${first.clinicName ?: "Clinic"} — ${formatDate(first.appointmentDate)} at ${formatTime(first.appointmentTime)}"
        if (appointments.size > 1) {
            binding.tvAppointmentCount.visibility = View.VISIBLE
            binding.tvAppointmentCount.text = "+${appointments.size - 1} more"
        } else {
            binding.tvAppointmentCount.visibility = View.GONE
        }
    }

    // ── Date / time helpers ───────────────────────────────────────────────────

    private fun formatDate(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "Date not set"
        return try {
            val input  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val output = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            output.format(input.parse(dateString) ?: return dateString)
        } catch (e: Exception) { dateString }
    }

    // Fix: try ISO-8601 first, then fall back to plain time, then return as-is.
    private fun formatTime(timeString: String?): String {
        if (timeString.isNullOrEmpty()) return "Time not set"
        // Try ISO-8601 TIMESTAMPTZ (returned by Supabase for TIMESTAMPTZ columns)
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
            } catch (_: Exception) { /* try next */ }
        }
        // Fall back: plain time string "HH:mm:ss" (for appointment_time which is TIME type)
        return try {
            val input  = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val output = SimpleDateFormat("h:mm a",   Locale.getDefault())
            output.format(input.parse(timeString) ?: return timeString)
        } catch (e: Exception) { timeString }
    }

    private fun loadDashboardData() {
        val currentUser = sessionManager.getUserSession()
        if (currentUser != null) {
            viewModel.loadDashboardData()
        }
        // No session = dummy data stays visible (dev/preview mode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ── Shared data classes ───────────────────────────────────────────────────────

data class UpcomingVaccination(
    val vaccineName: String,
    val dueDate: String?,
    val childId: Int
)

data class UpcomingMedication(
    val medicationName: String,
    val dosage: String?,
    val scheduledTime: String?,
    val childId: Int
)

data class UpcomingAppointment(
    val clinicName: String?,
    val appointmentDate: String?,
    val appointmentTime: String?,
    val childId: Int
)