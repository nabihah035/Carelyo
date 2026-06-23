package com.example.carelyo.ui.reminder

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carelyo.R
import com.example.carelyo.databinding.ActivityReminderBinding
import com.example.carelyo.data.entity.Reminder
import com.example.carelyo.data.session.SessionManager

class ReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderBinding
    private lateinit var viewModel: ReminderViewModel
    private lateinit var adapter: ReminderAdapter
    private lateinit var medicationAdapter: MedicationReminderAdapter
    private var childList: List<com.example.carelyo.data.entity.Child> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ReminderViewModel::class.java]

        // Setup RecyclerView
        setupRecyclerView()

        // Setup click listeners
        setupClickListeners()

        // Observe ViewModel
        observeViewModel()

        // Load reminders
        loadReminders()

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadReminders()
        }
    }

    private fun setupRecyclerView() {
        adapter = ReminderAdapter(
            onDismissClick = { reminder ->
                showDeleteWarningDialog(reminder)
            },
            onItemClick = { reminder ->
                // Optional: Handle item click to view details
            },
            onMarkAsReadClick = { reminder ->
                viewModel.markAsRead(reminder)
            }
        )
        binding.rvReminders.apply {
            layoutManager = LinearLayoutManager(this@ReminderActivity)
            adapter = this@ReminderActivity.adapter
        }

        medicationAdapter = MedicationReminderAdapter(
            onDeleteClick = { med ->
                viewModel.deleteMedication(med)
            },
            onToggleActive = { med, isActive ->
                viewModel.toggleMedicationActive(med, isActive)
            }
        )
        binding.rvMedicationReminders.apply {
            layoutManager = LinearLayoutManager(this@ReminderActivity)
            adapter = medicationAdapter
        }
    }

    private fun setupClickListeners() {
        // Close button
        binding.btnClose.setOnClickListener {
            finish()
        }

        // Mark all as read button
        binding.btnMarkAllRead.setOnClickListener {
            viewModel.markAllAsRead()
        }

        // Add Medication button
        binding.btnAddMedication.setOnClickListener {
            showAddMedicationDialog()
        }
    }

    private fun showDeleteWarningDialog(reminder: Reminder) {
        val dialogView = layoutInflater.inflate(R.layout.warning_delete, null)
        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        // Optional blurred background effect logic can go here (or simple dim)
        dialog.window?.setDimAmount(0.8f)

        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<android.view.View>(R.id.btnConfirmDelete)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            viewModel.deleteReminder(reminder)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun observeViewModel() {
        // Observe reminders list
        viewModel.reminders.observe(this) { reminders ->
            adapter.submitList(reminders)
            updateEmptyState(reminders.isEmpty())
        }

        // Observe unread count
        viewModel.unreadCount.observe(this) { count ->
            updateBadgeCount(count)
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            if (!isLoading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        // Observe operation results
        viewModel.operationResult.observe(this) { result ->
            when (result) {
                is ReminderOperationResult.Success -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                is ReminderOperationResult.Error -> {
                    Toast.makeText(this, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        // Observe medications
        viewModel.medications.observe(this) { medications ->
            medicationAdapter.submitList(medications)
        }
        
        // Observe children
        viewModel.children.observe(this) { children ->
            childList = children
        }
    }

    private fun loadReminders() {
        val sessionManager = SessionManager(this)
        val user = sessionManager.getUserSession()
        if (user != null) {
            viewModel.loadReminders(user.UserID)
            viewModel.loadMedicationsAndChildren(user.UserID)
        } else {
            Toast.makeText(this, "Please login to view reminders", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showAddMedicationDialog() {
        if (childList.isEmpty()) {
            Toast.makeText(this, "No children registered yet", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_medication, null)
        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val btnCloseDialog = dialogView.findViewById<android.widget.ImageButton>(R.id.btnCloseDialog)
        val actvChildSelect = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.actvChildSelect)
        val etMedName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMedName)
        val etDosage = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDosage)
        val actvFrequency = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.actvFrequency)
        val etStartDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etStartDate)
        val etEndDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEndDate)
        val btnSaveMedication = dialogView.findViewById<android.widget.Button>(R.id.btnSaveMedication)

        // Setup Child Dropdown
        val childNames = childList.map { it.full_name ?: "Unknown" }
        val childAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, childNames)
        actvChildSelect.setAdapter(childAdapter)

        // Setup Frequency Dropdown
        val frequencies = listOf("1 time a day", "2 times a day", "3 times a day")
        val freqAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, frequencies)
        actvFrequency.setAdapter(freqAdapter)

        val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val calendar = java.util.Calendar.getInstance()

        // Setup Start Date Picker
        etStartDate.setOnClickListener {
            android.app.DatePickerDialog(this, { _, year, month, day ->
                calendar.set(year, month, day)
                etStartDate.setText(dateFormatter.format(calendar.time))
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }

        // Setup End Date Picker
        etEndDate.setOnClickListener {
            android.app.DatePickerDialog(this, { _, year, month, day ->
                calendar.set(year, month, day)
                etEndDate.setText(dateFormatter.format(calendar.time))
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }

        btnCloseDialog.setOnClickListener { dialog.dismiss() }

        btnSaveMedication.setOnClickListener {
            val childIdx = childNames.indexOf(actvChildSelect.text.toString())
            if (childIdx == -1) {
                Toast.makeText(this, "Please select a child", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val selectedChildId = childList[childIdx].ChildID
            val medName = etMedName.text.toString().trim()
            val dosage = etDosage.text.toString().trim()
            val frequencyStr = actvFrequency.text.toString()
            val startDate = etStartDate.text.toString().trim()
            val endDate = etEndDate.text.toString().trim()

            if (medName.isEmpty() || frequencyStr.isEmpty()) {
                Toast.makeText(this, "Medication name and frequency are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val medInsert = com.example.carelyo.data.entity.MedicationInsert(
                ChildID = selectedChildId,
                medication_name = medName,
                dosage = dosage,
                frequency = frequencyStr,
                start_date = startDate.takeIf { it.isNotEmpty() },
                end_date = endDate.takeIf { it.isNotEmpty() },
                is_active = true
            )

            // Calculate auto-spread times
            val times = mutableListOf<String>()
            val todayStr = dateFormatter.format(java.util.Date())
            // e.g. "2026-06-05T08:00:00+08:00"
            val timeFormatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:00XXX", java.util.Locale.getDefault())
            
            val numTimes = when (frequencyStr) {
                "3 times a day" -> 3
                "2 times a day" -> 2
                else -> 1
            }
            
            for (i in 0 until numTimes) {
                val cal = java.util.Calendar.getInstance()
                if (startDate.isNotEmpty()) {
                    cal.time = dateFormatter.parse(startDate) ?: java.util.Date()
                }
                when (numTimes) {
                    3 -> cal.set(java.util.Calendar.HOUR_OF_DAY, 8 + (i * 6)) // 8 AM, 2 PM, 8 PM
                    2 -> cal.set(java.util.Calendar.HOUR_OF_DAY, 8 + (i * 12)) // 8 AM, 8 PM
                    else -> cal.set(java.util.Calendar.HOUR_OF_DAY, 8) // 8 AM
                }
                times.add(timeFormatter.format(cal.time))
            }

            viewModel.addMedication(medInsert, times)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateBadgeCount(count: Int) {
        if (count > 0) {
            binding.tvBadgeCount.visibility = View.VISIBLE
            binding.tvBadgeCount.text = count.toString()
        } else {
            binding.tvBadgeCount.visibility = View.GONE
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.rvReminders.visibility = View.GONE
            // You can add an empty state TextView if needed
        } else {
            binding.rvReminders.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any resources if needed
    }
}