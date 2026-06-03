package com.example.carelyo.ui.dashboard

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.carelyo.R
import com.example.carelyo.databinding.ActivityDashboardBinding

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

        // Kick off multi-agent pipelines to retrieve background information from Supabase
        viewModel.loadDashboardData()

        // Handle runtime permissions for Android 13 (API 33) and above
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {

            // Request the permission from the user
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        } else {
            // Permission was already granted in a previous session, run sync immediately
            viewModel.uploadFcmToken()
        }
    }

    // 🔹 Add this override method to intercept the exact moment they click "Allow"
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                println("[DashboardActivity]: Notification permission granted by user.")
            } else {
                println("[DashboardActivity]: Notification permission denied. Syncing token anyway for fallback processing.")
            }

            // Trigger the token upload asynchronously now that the dialog is dismissed!
            viewModel.uploadFcmToken()
        }
    }
}