package com.example.carelyo.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.carelyo.R
import com.example.carelyo.data.session.SessionManager
import com.example.carelyo.databinding.ActivityDashboardBinding
import com.example.carelyo.service.NotificationSyncManager
import com.example.carelyo.ui.aihelp.HelpActivity
import com.example.carelyo.ui.reminder.ReminderActivity
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var prefs: SharedPreferences
    private lateinit var sessionManager: SessionManager

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        prefs.edit().putBoolean("notifications_enabled", isGranted).apply()
        if (isGranted) {
            Log.d("DashboardActivity", "POST_NOTIFICATIONS permission granted")
        } else {
            Log.d("DashboardActivity", "POST_NOTIFICATIONS permission denied")
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "notifications_enabled" || key == "unread_count") {
            updateBadgeVisibility()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        prefs = getSharedPreferences("carelyo_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Ensure notification channel is initialized
        NotificationSyncManager.ensureChannel(this)

        // Request POST_NOTIFICATIONS permission for Android 13+ (API 33+)
        checkNotificationPermission()

        // Subscribe to user-specific FCM topic
        val currentUser = sessionManager.getUserSession()
        if (currentUser?.notification_permission != null) {
            prefs.edit().putBoolean("notifications_enabled", currentUser.notification_permission).apply()
        }
        if (currentUser != null && currentUser.UserID > 0) {
            FirebaseMessaging.getInstance().subscribeToTopic("user_${currentUser.UserID}")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("DashboardActivity", "Subscribed to FCM topic: user_${currentUser.UserID}")
                    }
                }

            // Sync notifications from Supabase
            lifecycleScope.launch(Dispatchers.IO) {
                NotificationSyncManager.syncNotifications(this@DashboardActivity, currentUser.UserID)
            }
        }

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

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(permission)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the unread count and sync notifications when returning to the dashboard
        val currentUser = sessionManager.getUserSession()
        if (currentUser != null && currentUser.UserID > 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                NotificationSyncManager.syncNotifications(this@DashboardActivity, currentUser.UserID)
            }
        }
        viewModel.loadDashboardData()
        updateBadgeVisibility()
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

    fun updateBadgeVisibility() {
        runOnUiThread {
            val isEnabled = prefs.getBoolean("notifications_enabled", true)
            val count = prefs.getInt("unread_count", viewModel.unreadRemindersCount.value ?: 0)

            if (!isEnabled) {
                binding.tvNotificationBadge.visibility = View.GONE
            } else {
                binding.tvNotificationBadge.visibility = View.VISIBLE
                val params = binding.tvNotificationBadge.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                if (count > 0) {
                    binding.tvNotificationBadge.text = if (count > 99) "99+" else count.toString()
                    val size = (18 * resources.displayMetrics.density).toInt()
                    params?.width = size
                    params?.height = size
                } else {
                    // Display clean red dot
                    binding.tvNotificationBadge.text = ""
                    val dotSize = (10 * resources.displayMetrics.density).toInt()
                    params?.width = dotSize
                    params?.height = dotSize
                }
                if (params != null) {
                    binding.tvNotificationBadge.layoutParams = params
                }
            }
        }
    }

    /**
     * Observes live changes from the Supabase queries
     */
    private fun observeViewModel() {
        viewModel.unreadRemindersCount.observe(this) { count ->
            // Save to SharedPreferences for persistence
            prefs.edit().putInt("unread_count", count ?: 0).apply()
            updateBadgeVisibility()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}