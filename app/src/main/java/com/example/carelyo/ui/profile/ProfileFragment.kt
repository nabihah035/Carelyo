package com.example.carelyo.ui.profile

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carelyo.R
import com.example.carelyo.agent.infra.CarelyoMessage
import com.example.carelyo.agent.infra.CarelyoMessageBroker
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.session.SessionManager
import com.example.carelyo.databinding.DialogAddChildBinding
import com.example.carelyo.databinding.DialogHelpSupportBinding
import com.example.carelyo.databinding.FragmentProfileBinding
import com.example.carelyo.ui.auth.LoginActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: SessionManager
    private lateinit var childrenAdapter: ChildrenAdapter
    private var helpSupportDialog: android.app.AlertDialog? = null
    private var addChildDialog: android.app.AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())

        // Setup UI with user data
        setupUserData()

        // Setup children RecyclerView
        setupChildrenRecyclerView()

        // Setup click listeners
        setupClickListeners()

        // Register for messages from ChildRecordAgent
        CarelyoMessageBroker.registerAgent(object : com.example.carelyo.agent.infra.CarelyoAgent {
            override val agentName: String = "ProfileFragment"

            override fun processIncomingMessage(message: CarelyoMessage) {
                when (message.messageType) {
                    "INFORM_CHILD_PROFILES_READY" -> {
                        @Suppress("UNCHECKED_CAST")
                        val children = message.content["children"] as? List<Child>
                        if (children != null) {
                            requireActivity().runOnUiThread {
                                childrenAdapter.submitList(children)
                                updateEmptyState(children.isEmpty())
                            }
                        }
                    }
                    "INFORM_CHILD_ADD_SUCCESS" -> {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Child added successfully!", Toast.LENGTH_SHORT).show()
                            // Refresh children list
                            requestChildrenData()
                        }
                    }
                    "CHILD_FETCH_ERROR" -> {
                        val reason = message.content["reason"] as? String ?: "Unknown error"
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Error loading children: $reason", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })

        // Request children data
        requestChildrenData()
    }

    private fun setupUserData() {
        val user = sessionManager.getUserSession()

        binding.tvProfileName.text = user?.full_name ?: "User Name"
        binding.tvProfileEmail.text = user?.email ?: "user@example.com"
        binding.tvProfilePhone.text = user?.phone_number ?: "Not provided"
    }

    private fun setupChildrenRecyclerView() {
        childrenAdapter = ChildrenAdapter { child ->
            // Navigate to child details screen
            Toast.makeText(requireContext(), "Selected: ${child.full_name}", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to child details screen
        }

        binding.rvChildren.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChildren.adapter = childrenAdapter

        // Initially show empty state
        updateEmptyState(true)
    }

    private fun requestChildrenData() {
        val user = sessionManager.getUserSession()
        if (user != null && user.UserID > 0) {
            // Send message to ChildRecordAgent to fetch children
            CarelyoMessageBroker.passMessage(
                CarelyoMessage(
                    sender = "ProfileFragment",
                    receiver = "ChildRecordAgent",
                    messageType = "REQUEST_CHILD_DATA",
                    content = mapOf("parentId" to user.UserID)
                )
            )
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        // You can show/hide an empty state view here if you have one
        if (isEmpty) {
            // Optionally show a message
            // binding.tvEmptyState.visibility = View.VISIBLE
            // binding.rvChildren.visibility = View.GONE
        } else {
            // binding.tvEmptyState.visibility = View.GONE
            // binding.rvChildren.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        // Add child click
        binding.tvAddChild.setOnClickListener {
            showAddChildDialog()
        }

        // Help & Support card
        binding.cardHelpSupport.setOnClickListener {
            showHelpSupportDialog()
        }

        // Logout button
        binding.btnLogOut.setOnClickListener {
            performLogout()
        }
    }

    private fun showAddChildDialog() {
        val user = sessionManager.getUserSession()
        if (user == null) {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        // Dismiss any existing dialog
        addChildDialog?.dismiss()

        // Inflate the dialog layout using view binding
        val dialogBinding = DialogAddChildBinding.inflate(LayoutInflater.from(requireContext()))

        // Set up date picker for DOB
        dialogBinding.etDob.setOnClickListener {
            showDatePickerDialog(dialogBinding)
        }

        addChildDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Child")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { dialog, _ ->
                // Get input values
                val name = dialogBinding.etChildName.text?.toString()?.trim()
                val dob = dialogBinding.etDob.text?.toString()?.trim()
                val gender = dialogBinding.etGender.text?.toString()?.trim()
                val bloodType = dialogBinding.etBloodType.text?.toString()?.trim()
                val weight = dialogBinding.etWeight.text?.toString()?.trim()
                val height = dialogBinding.etHeight.text?.toString()?.trim()

                if (name.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Please enter child's name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Create Child object matching the database schema
                val child = Child(
                    ChildID = 0, // Will be generated by database
                    Parent_ID = user.UserID,
                    full_name = name,
                    date_of_birth = if (!dob.isNullOrEmpty()) dob else null,
                    gender = if (!gender.isNullOrEmpty()) gender else null,
                    blood_type = if (!bloodType.isNullOrEmpty()) bloodType else null,
                    weight = if (!weight.isNullOrEmpty()) weight.toDoubleOrNull() else null,
                    height = if (!height.isNullOrEmpty()) height.toDoubleOrNull() else null,
                    profile_photo_url = null,
                    created_at = null,
                    updated_at = null
                )

                // Send to ChildRecordAgent
                CarelyoMessageBroker.passMessage(
                    CarelyoMessage(
                        sender = "ProfileFragment",
                        receiver = "ChildRecordAgent",
                        messageType = "REQUEST_ADD_CHILD",
                        content = mapOf("child" to child)
                    )
                )

                Toast.makeText(requireContext(), "Adding child...", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show() as AlertDialog?
    }

    private fun showDatePickerDialog(binding: DialogAddChildBinding) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                    selectedYear, selectedMonth + 1, selectedDay)
                binding.etDob.setText(formattedDate)
            },
            year, month, day
        ).show()
    }

    private fun showHelpSupportDialog() {
        // Dismiss any existing dialog
        helpSupportDialog?.dismiss()

        // Inflate the dialog layout using view binding
        val dialogBinding = DialogHelpSupportBinding.inflate(LayoutInflater.from(requireContext()))

        helpSupportDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .show() as AlertDialog?

        // Set up close button
        dialogBinding.btnCloseDialog.setOnClickListener {
            helpSupportDialog?.dismiss()
        }

        // Make dialog background transparent to show the card design
        helpSupportDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun performLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log Out") { _, _ ->
                // Clear session
                sessionManager.clearSession()

                // Navigate to Login
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        helpSupportDialog?.dismiss()
        helpSupportDialog = null
        addChildDialog?.dismiss()
        addChildDialog = null
    }
}