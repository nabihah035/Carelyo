package com.example.carelyo.ui.health

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.data.entity.Allergie
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.MedicalHistory
import com.example.carelyo.databinding.DialogAddNewAllergyBinding
import com.example.carelyo.databinding.DialogAddNewRecordBinding
import com.example.carelyo.databinding.DialogMedicalHistoryDetailsBinding
import com.example.carelyo.databinding.FragmentHealthRecordsBinding
import com.example.carelyo.ui.health.adapters.AllergyAdapter
import com.example.carelyo.ui.health.adapters.MedicalHistoryAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class HealthRecordsFragment : Fragment() {

    private var _binding: FragmentHealthRecordsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthRecordsViewModel by viewModels()
    private lateinit var allergyAdapter: AllergyAdapter
    private lateinit var medicalHistoryAdapter: MedicalHistoryAdapter

    // Track selected buttons
    private var selectedTypeButton: MaterialButton? = null
    private var selectedSeverityButton: MaterialButton? = null
    private var selectedRecordTypeButton: MaterialButton? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        observeViewModel()
        setupAddButtons()

        viewModel.loadHealthData()

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadHealthData()
        }
    }

    private fun setupRecyclerViews() {
        allergyAdapter = AllergyAdapter(
            items = emptyList(),
            onDeleteClick = { allergy ->
                showDeleteConfirmation(allergy)
            },
            onItemClick = { /* Do nothing - disabled */ }
        )
        binding.rvAllergies.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = allergyAdapter
        }

        medicalHistoryAdapter = MedicalHistoryAdapter(
            items = emptyList(),
            onItemClick = { history ->
                showMedicalHistoryDetails(history)
            }
        )
        binding.rvMedicalHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = medicalHistoryAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (!isLoading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.filteredAllergies.observe(viewLifecycleOwner) { allergies ->
            val children = viewModel.childrenList.value ?: emptyList()
            allergyAdapter.updateItems(allergies)

            binding.rvAllergies.post {
                for (i in 0 until binding.rvAllergies.childCount) {
                    val childView = binding.rvAllergies.getChildAt(i)
                    val viewHolder = binding.rvAllergies.getChildViewHolder(childView)
                    if (viewHolder is AllergyAdapter.AllergyViewHolder) {
                        val position = viewHolder.bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION && position < allergies.size) {
                            val allergy = allergies[position]
                            val childName = children.find { it.ChildID == allergy.ChildID }?.full_name
                            viewHolder.itemView.tag = childName
                            viewHolder.bind(allergy)
                        }
                    }
                }
            }

            updateEmptyState()
        }

        viewModel.filteredMedicalHistory.observe(viewLifecycleOwner) { history ->
            val children = viewModel.childrenList.value ?: emptyList()
            medicalHistoryAdapter.updateItems(history)

            binding.rvMedicalHistory.post {
                for (i in 0 until binding.rvMedicalHistory.childCount) {
                    val childView = binding.rvMedicalHistory.getChildAt(i)
                    val viewHolder = binding.rvMedicalHistory.getChildViewHolder(childView)
                    if (viewHolder is MedicalHistoryAdapter.MedicalHistoryViewHolder) {
                        val position = viewHolder.bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION && position < history.size) {
                            val record = history[position]
                            val childName = children.find { it.ChildID == record.ChildID }?.full_name
                            viewHolder.itemView.tag = childName
                            viewHolder.bind(record)
                        }
                    }
                }
            }

            updateEmptyState()
        }

        viewModel.childrenList.observe(viewLifecycleOwner) { children ->
            val currentAllergies = viewModel.filteredAllergies.value ?: emptyList()
            val currentHistory = viewModel.filteredMedicalHistory.value ?: emptyList()

            allergyAdapter.updateItems(currentAllergies)
            medicalHistoryAdapter.updateItems(currentHistory)
        }
    }

    private fun setupAddButtons() {
        binding.tvAddAllergy.setOnClickListener {
            showAddAllergyDialog()
        }

        binding.btnAddNewRecord.setOnClickListener {
            showAddMedicalRecordDialog()
        }
    }

    private fun setupTypeButtonSelection(button: MaterialButton, buttons: List<MaterialButton>) {
        button.setOnClickListener {
            buttons.forEach { btn ->
                btn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_unselected)
                btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_unselected))
            }
            button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_selected)
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_selected))
            selectedTypeButton = button
        }
    }

    private fun setupSeverityButtonSelection(button: MaterialButton, buttons: List<MaterialButton>) {
        button.setOnClickListener {
            buttons.forEach { btn ->
                btn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_unselected)
                btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_unselected))
            }
            button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_selected)
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_selected))
            selectedSeverityButton = button
        }
    }

    private fun setupRecordTypeButtonSelection(button: MaterialButton, buttons: List<MaterialButton>) {
        button.setOnClickListener {
            buttons.forEach { btn ->
                btn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_unselected)
                btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_unselected))
            }
            button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_selected)
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_selected))
            selectedRecordTypeButton = button
        }
    }

    private fun showAddAllergyDialog() {
        val dialog = Dialog(requireContext())
        val dialogBinding = DialogAddNewAllergyBinding.inflate(
            LayoutInflater.from(requireContext())
        )
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(true)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(android.view.Gravity.BOTTOM)
        }

        val children = viewModel.childrenList.value ?: emptyList()
        val childNames = children.map { it.full_name ?: "Unnamed Child" }

        if (children.isEmpty()) {
            Toast.makeText(requireContext(), "No children available. Please add a child first.", Toast.LENGTH_LONG).show()
            dialog.dismiss()
            return
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, childNames)
        dialogBinding.actvChooseChild.setAdapter(adapter)
        dialogBinding.actvChooseChild.setOnItemClickListener { _, _, position, _ ->
            dialogBinding.actvChooseChild.tag = children[position]
        }
        if (children.isNotEmpty()) {
            dialogBinding.actvChooseChild.setText(childNames[0], false)
            dialogBinding.actvChooseChild.tag = children[0]
        }

        // Setup type buttons
        val typeButtons = listOf(
            dialogBinding.btnTypeFood,
            dialogBinding.btnTypeMedication,
            dialogBinding.btnTypeEnvironmental
        )
        dialogBinding.btnTypeFood.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_selected)
        dialogBinding.btnTypeFood.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_selected))
        selectedTypeButton = dialogBinding.btnTypeFood

        typeButtons.forEach { button ->
            setupTypeButtonSelection(button, typeButtons)
        }

        // Setup severity buttons
        val severityButtons = listOf(
            dialogBinding.btnSeverityMild,
            dialogBinding.btnSeverityModerate,
            dialogBinding.btnSeveritySevere
        )
        dialogBinding.btnSeverityMild.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_selected)
        dialogBinding.btnSeverityMild.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_selected))
        selectedSeverityButton = dialogBinding.btnSeverityMild

        severityButtons.forEach { button ->
            setupSeverityButtonSelection(button, severityButtons)
        }

        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnAddAllergy.setOnClickListener {
            val selectedChild = dialogBinding.actvChooseChild.tag as? Child
            val allergyName = dialogBinding.etAllergenName.text.toString().trim()

            val allergyType = when (selectedTypeButton?.id) {
                dialogBinding.btnTypeFood.id -> "Food"
                dialogBinding.btnTypeMedication.id -> "Medication"
                dialogBinding.btnTypeEnvironmental.id -> "Environmental"
                else -> "Other"
            }

            val severity = when (selectedSeverityButton?.id) {
                dialogBinding.btnSeverityMild.id -> "Mild"
                dialogBinding.btnSeverityModerate.id -> "Moderate"
                dialogBinding.btnSeveritySevere.id -> "Severe"
                else -> "Unknown"
            }

            val notes = dialogBinding.etReactionDescription.text.toString().trim()

            if (selectedChild == null) {
                Toast.makeText(requireContext(), "Please select a child", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (allergyName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter allergy name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialogBinding.btnAddAllergy.isEnabled = false
            dialogBinding.btnAddAllergy.text = "Adding..."

            viewModel.addAllergy(
                childId = selectedChild.ChildID,
                allergyName = allergyName,
                allergyType = allergyType,
                severity = severity,
                notes = notes
            ) { success ->
                dialogBinding.btnAddAllergy.isEnabled = true
                dialogBinding.btnAddAllergy.text = "✓ Add Allergy"

                if (success) {
                    Toast.makeText(requireContext(), "Allergy added successfully!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Failed to add allergy. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showAddMedicalRecordDialog() {
        val dialog = Dialog(requireContext())
        val dialogBinding = DialogAddNewRecordBinding.inflate(
            LayoutInflater.from(requireContext())
        )
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(true)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(android.view.Gravity.BOTTOM)
        }

        val children = viewModel.childrenList.value ?: emptyList()
        val childNames = children.map { it.full_name ?: "Unnamed Child" }

        if (children.isEmpty()) {
            Toast.makeText(requireContext(), "No children available. Please add a child first.", Toast.LENGTH_LONG).show()
            dialog.dismiss()
            return
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, childNames)
        dialogBinding.actvChooseChild.setAdapter(adapter)
        dialogBinding.actvChooseChild.setOnItemClickListener { _, _, position, _ ->
            dialogBinding.actvChooseChild.tag = children[position]
        }
        if (children.isNotEmpty()) {
            dialogBinding.actvChooseChild.setText(childNames[0], false)
            dialogBinding.actvChooseChild.tag = children[0]
        }

        // Setup record type buttons
        val recordTypeButtons = listOf(
            dialogBinding.btnConsultation,
            dialogBinding.btnCheckup,
            dialogBinding.btnVaccination,
            dialogBinding.btnEmergency
        )
        dialogBinding.btnConsultation.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_selected)
        dialogBinding.btnConsultation.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_selected))
        selectedRecordTypeButton = dialogBinding.btnConsultation

        recordTypeButtons.forEach { button ->
            setupRecordTypeButtonSelection(button, recordTypeButtons)
        }

        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveRecord.setOnClickListener {
            val selectedChild = dialogBinding.actvChooseChild.tag as? Child
            val doctorName = dialogBinding.etDoctorName.text.toString().trim()
            val clinicHospital = dialogBinding.etClinicHospital.text.toString().trim()
            val diagnosis = dialogBinding.etDiagnosis.text.toString().trim()
            val notes = dialogBinding.etNotesPrescriptions.text.toString().trim()

            val recordType = when (selectedRecordTypeButton?.id) {
                dialogBinding.btnConsultation.id -> "Consultation"
                dialogBinding.btnCheckup.id -> "Checkup"
                dialogBinding.btnVaccination.id -> "Vaccination"
                dialogBinding.btnEmergency.id -> "Emergency"
                else -> "Consultation"
            }

            if (selectedChild == null) {
                Toast.makeText(requireContext(), "Please select a child", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (doctorName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter doctor name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (diagnosis.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter diagnosis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialogBinding.btnSaveRecord.isEnabled = false
            dialogBinding.btnSaveRecord.text = "Saving..."

            viewModel.addMedicalRecord(
                childId = selectedChild.ChildID,
                doctorName = doctorName,
                clinicName = clinicHospital,
                diagnosis = diagnosis,
                notes = notes,
                recordType = recordType
            ) { success ->
                dialogBinding.btnSaveRecord.isEnabled = true
                dialogBinding.btnSaveRecord.text = "✓ Save Record"

                if (success) {
                    Toast.makeText(requireContext(), "Medical record added successfully!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Failed to add medical record. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmation(allergy: Allergie) {
        val dialogView = layoutInflater.inflate(R.layout.warning_allergy, null)
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.8f)

        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btnCancelDeleteAllergy)
        val btnConfirm = dialogView.findViewById<android.view.View>(R.id.btnConfirmDeleteAllergy)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            viewModel.deleteAllergy(allergy.AllergieID) { success ->
                if (success) {
                    Toast.makeText(requireContext(), "Allergy deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to delete allergy", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showMedicalHistoryDetails(history: MedicalHistory) {
        try {
            val childName = viewModel.getChildName(history.ChildID) ?: "Unknown Child"
            val doctorVisit = viewModel.getDoctorVisitForHistory(history)

            val conditionName = history.condition_name ?: "Medical Record"
            val diagnosisDate = history.diagnosis_date ?: "Date not recorded"
            val treatment = history.treatment ?: "Not specified"
            val notes = history.notes ?: "No additional notes available."

            val doctorName = doctorVisit?.doctor_name ?: "Not recorded"
            val clinicName = doctorVisit?.clinic_name ?: "Not recorded"

            val bottomSheetDialog = BottomSheetDialog(requireContext())
            val sheetBinding = DialogMedicalHistoryDetailsBinding.inflate(
                LayoutInflater.from(requireContext())
            )
            bottomSheetDialog.setContentView(sheetBinding.root)

            sheetBinding.tvDetailsTitle.text = treatment
            sheetBinding.tvDetailsDate.text = diagnosisDate
            sheetBinding.tvDetailsDoctor.text = doctorName
            sheetBinding.tvDetailsClinic.text = clinicName
            sheetBinding.tvDetailsDiagnosis.text = conditionName
            sheetBinding.tvDetailsNotes.text = notes

            sheetBinding.ivCloseDetails.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error showing details: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState() {
        val allergiesEmpty = (viewModel.filteredAllergies.value ?: emptyList()).isEmpty()
        val historyEmpty = (viewModel.filteredMedicalHistory.value ?: emptyList()).isEmpty()

        binding.tvAllergiesEmpty.visibility = if (allergiesEmpty) View.VISIBLE else View.GONE
        binding.tvHistoryEmpty.visibility = if (historyEmpty) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}