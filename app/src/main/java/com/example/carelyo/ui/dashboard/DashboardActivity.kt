package com.example.carelyo.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.carelyo.R
import com.example.carelyo.databinding.ActivityDashboardBinding
import com.example.carelyo.ui.aihelp.HelpActivity
import com.example.carelyo.ui.reminder.ReminderActivity

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Jetpack Navigation Routing Architecture
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigationView.setupWithNavController(navController)

        // Setup UI Action Observers and Click Listeners
        setupAiHelpButton()
        setupNotificationBellButton()
        observeViewModel()

        // Dynamically manage visibility of bottom navigation based on keyboard state
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (isKeyboardVisible) {
                binding.bottomNavigationView.visibility = View.GONE
            } else {
                binding.bottomNavigationView.visibility = View.VISIBLE
            }
            insets
        }

        // Kick off pipelines to retrieve background information from Supabase
        viewModel.loadDashboardData()
    }

    private fun setupAiHelpButton() {
        binding.fabAiHelp.setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Set up click handler to direct the parent to ReminderActivity
     */
    private fun setupNotificationBellButton() {
        binding.notificationBadgeContainer.setOnClickListener {
            val intent = Intent(this, ReminderActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Observes live changes from the Supabase queries
     */
    private fun observeViewModel() {
        viewModel.unreadRemindersCount.observe(this) { count ->
            if (count > 0) {
                binding.tvNotificationBadge.visibility = View.VISIBLE
                binding.tvNotificationBadge.text = count.toString()
            } else {
                binding.tvNotificationBadge.visibility = View.GONE
            }
        }
    }
}