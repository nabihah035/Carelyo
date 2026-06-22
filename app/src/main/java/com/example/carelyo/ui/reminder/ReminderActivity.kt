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
    }

    private fun loadReminders() {
        val sessionManager = SessionManager(this)
        val user = sessionManager.getUserSession()
        if (user != null) {
            viewModel.loadReminders(user.UserID)
        } else {
            Toast.makeText(this, "Please login to view reminders", Toast.LENGTH_SHORT).show()
            finish()
        }
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