package com.example.carelyo.ui.profile

import androidx.appcompat.app.AlertDialog
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import io.github.jan.supabase.postgrest.postgrest
import com.example.carelyo.api.supabase.SupabaseClient

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: SessionManager
    private lateinit var childrenAdapter: ChildrenAdapter
    private var helpSupportDialog: androidx.appcompat.app.AlertDialog? = null
    private var addChildDialog: androidx.appcompat.app.AlertDialog? = null
    private lateinit var childRecordAgent: com.example.carelyo.agent.core.ChildRecordAgent

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
        childRecordAgent = com.example.carelyo.agent.core.ChildRecordAgent(viewLifecycleOwner.lifecycleScope)

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
                    "INFORM_CHILD_ADD_SUCCESS" -> {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Child added successfully!", Toast.LENGTH_SHORT).show()
                            requestChildrenData()
                        }
                    }
                    "INFORM_CHILD_ADD_FAILED" -> {
                        requireActivity().runOnUiThread {
                            val error = message.content["error"] as? String ?: "Unknown error"
                            Toast.makeText(requireContext(), "Failed to add child: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                    "CHILD_FETCH_ERROR" -> {
                        requireActivity().runOnUiThread {
                            val reason = message.content["reason"] as? String ?: "Unknown error"
                            Toast.makeText(requireContext(), "Error: $reason", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })

        // Request children data
        requestChildrenData()

        binding.swipeRefreshLayout.setOnRefreshListener {
            requestChildrenData()
        }
    }

    private fun setupUserData() {
        val user = sessionManager.getUserSession()

        binding.tvProfileName.text = user?.full_name ?: "User Name"
        binding.tvProfileEmail.text = user?.email ?: "user@example.com"
        binding.tvProfilePhone.text = user?.phone_number ?: "Not provided"
    }

    private fun setupChildrenRecyclerView() {
        childrenAdapter = ChildrenAdapter(
            onChildClick = { child ->
                Toast.makeText(requireContext(), "Selected: ${child.full_name}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { child ->
                showDeleteChildDialog(child)
            }
        )

        binding.rvChildren.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChildren.adapter = childrenAdapter
        updateEmptyState(true)
    }

    private fun requestChildrenData() {
        val user = sessionManager.getUserSession()
        if (user != null && user.UserID > 0) {
            binding.swipeRefreshLayout.isRefreshing = true
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val result = SupabaseClient.client.postgrest["CHILD"].select {
                        filter { eq("parent_id", user.UserID) }
                    }
                    val children = result.decodeList<Child>().filter { it.status != "Inactive" }

                    requireActivity().runOnUiThread {
                        childrenAdapter.submitList(children)
                        updateEmptyState(children.isEmpty())
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Error loading children: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                }
            }
        } else {
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        // You can show/hide an empty state view here if you have one
    }

    private fun setupClickListeners() {
        binding.tvAddChild.setOnClickListener {
            showAddChildDialog()
        }

        binding.cardHelpSupport.setOnClickListener {
            showHelpSupportDialog()
        }

        binding.cardNotifications.setOnClickListener {
            showNotificationDialog()
        }

        binding.btnLogOut.setOnClickListener {
            performLogout()
        }
    }

    private var notificationDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showNotificationDialog() {
        notificationDialog?.dismiss()

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_notification, null)
        val switchNotifications = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAllowNotification)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)

        val prefs = requireContext().getSharedPreferences("carelyo_prefs", android.content.Context.MODE_PRIVATE)
        val isNotificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        switchNotifications?.isChecked = isNotificationsEnabled

        notificationDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .show() as AlertDialog?

        notificationDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel?.setOnClickListener {
            notificationDialog?.dismiss()
        }

        switchNotifications?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()

            val user = sessionManager.getUserSession()
            if (user != null) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        SupabaseClient.client.postgrest["USER"].update(
                            {
                                set("notification_permission", isChecked)
                            }
                        ) {
                            filter { eq("userid", user.UserID) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun showAddChildDialog() {
        val user = sessionManager.getUserSession()
        if (user == null) {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        addChildDialog?.dismiss()

        val dialogBinding = DialogAddChildBinding.inflate(LayoutInflater.from(requireContext()))

        // Date picker
        dialogBinding.etDob.setOnClickListener {
            showDatePickerDialog(dialogBinding)
        }

        // Gender selection
        var selectedGender: String? = null
        dialogBinding.rgGender.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbMale -> selectedGender = "Male"
                R.id.rbFemale -> selectedGender = "Female"
            }
        }

        // Blood type selection
        var selectedBloodType: String? = null

        fun selectBloodType(textView: android.widget.TextView, bloodType: String?) {
            val bloodPills = listOf(
                dialogBinding.tvBloodAplus,
                dialogBinding.tvBloodAminus,
                dialogBinding.tvBloodBplus,
                dialogBinding.tvBloodBminus,
                dialogBinding.tvBloodOplus,
                dialogBinding.tvBloodOminus,
                dialogBinding.tvBloodABplus,
                dialogBinding.tvBloodABminus,
                dialogBinding.tvBloodNA
            )

            bloodPills.forEach { pill ->
                pill.setBackgroundResource(R.drawable.bg_blood_pill)
                pill.setTextColor(resources.getColor(R.color.blood_text_default, null))
            }

            if (bloodType != null && textView != null) {
                textView.setBackgroundResource(R.drawable.bg_blood_pill_selected)
                textView.setTextColor(resources.getColor(R.color.white, null))
                selectedBloodType = bloodType
            } else {
                selectedBloodType = null
            }
        }

        dialogBinding.tvBloodAplus.setOnClickListener {
            selectBloodType(it as android.widget.TextView, "A+")
        }
        dialogBinding.tvBloodAminus.setOnClickListener {
            selectBloodType(it as android.widget.TextView, "A-")
        }
        dialogBinding.tvBloodBplus.setOnClickListener {
            selectBloodType(it as android.widget.TextView, "B+")
        }
        dialogBinding.tvBloodBminus.setOnClickListener {
            selectBloodType(it as android.widget.TextView, "B-")
        }
        dialogBinding.tvBloodOplus.setOnClickListener {
            selectBloodType(it as android.widget.TextView, "O+")
        }
        dialogBinding.tvBloodOminus.setOnClickListener {
            selectBloodType(it as android.widget.TextView, "O-")
        }
        dialogBinding.tvBloodABplus.setOnClickListener {
            selectBloodType(it as android.widget.TextView, "AB+")
        }
        dialogBinding.tvBloodABminus.setOnClickListener {
            selectBloodType(it as android.widget.TextView, "AB-")
        }
        dialogBinding.tvBloodNA.setOnClickListener {
            selectBloodType(it as android.widget.TextView, "N/A")
        }

        // Close button
        dialogBinding.btnClose.setOnClickListener {
            addChildDialog?.dismiss()
        }

        // Save button
        dialogBinding.btnSaveChild.setOnClickListener {
            val name = dialogBinding.etChildName.text?.toString()?.trim()
            val dob = dialogBinding.etDob.text?.toString()?.trim()
            val gender = selectedGender
            val bloodType = selectedBloodType
            val weight = dialogBinding.etWeight.text?.toString()?.trim()
            val height = dialogBinding.etHeight.text?.toString()?.trim()

            if (name.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Please enter child's name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (gender == null) {
                Toast.makeText(requireContext(), "Please select gender", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            println("ProfileFragment: Adding child for Parent_ID: ${user.UserID}")
            println("ProfileFragment: Child name: $name")

            val child = Child(
                ChildID = 0,
                Parent_ID = user.UserID,
                full_name = name,
                date_of_birth = if (!dob.isNullOrEmpty()) dob else null,
                gender = gender,
                blood_type = bloodType,
                weight = if (!weight.isNullOrEmpty()) weight else null,
                height = if (!height.isNullOrEmpty()) height else null,
                created_at = null,
                updated_at = null,
                status = "Active"
            )

            CarelyoMessageBroker.passMessage(
                CarelyoMessage(
                    sender = "ProfileFragment",
                    receiver = "ChildRecordAgent",
                    messageType = "REQUEST_ADD_CHILD",
                    content = mapOf("child" to child)
                )
            )

            Toast.makeText(requireContext(), "Adding child...", Toast.LENGTH_SHORT).show()
            addChildDialog?.dismiss()
        }

        addChildDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
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
        helpSupportDialog?.dismiss()

        val dialogBinding = DialogHelpSupportBinding.inflate(LayoutInflater.from(requireContext()))

        helpSupportDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .show() as AlertDialog?

        dialogBinding.btnCloseDialog.setOnClickListener {
            helpSupportDialog?.dismiss()
        }

        helpSupportDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun performLogout() {
        val dialogView = layoutInflater.inflate(R.layout.warning_logout, null)
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.8f)

        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelLogout)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmLogout)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            sessionManager.clearSession()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }

        dialog.show()
    }

    private fun showDeleteChildDialog(child: Child) {
        val dialogView = layoutInflater.inflate(R.layout.warning_child, null)
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.8f)

        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelDeleteChild)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmDeleteChild)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(), "Removing child...", Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    SupabaseClient.client.postgrest["CHILD"].update(
                        {
                            set("status", "Inactive")
                        }
                    ) {
                        filter { eq("childid", child.ChildID) }
                    }
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Child removed successfully!", Toast.LENGTH_SHORT).show()
                        requestChildrenData()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to remove child: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        CarelyoMessageBroker.unregisterAgent("ProfileFragment")
        CarelyoMessageBroker.unregisterAgent("ChildRecordAgent")
        _binding = null
        helpSupportDialog?.dismiss()
        helpSupportDialog = null
        addChildDialog?.dismiss()
        addChildDialog = null
    }
}