package com.example.carelyo.ui.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.carelyo.data.session.SessionManager
import com.example.carelyo.databinding.ActivityLoginBinding
import com.example.carelyo.ui.dashboard.DashboardActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔹 PLACE IT HERE: Initialize the notification pipeline immediately on app startup
        createReminderNotificationChannel()

        sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                viewModel.login(email)
            } else {
                Toast.makeText(this, "Please enter your registered email address", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        observeViewModel()
    }

    // 🔹 ADD THE LOGIC DOWN HERE AS A HELPER METHOD INSIDE THE CLASS
    private fun createReminderNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "carelyo_reminders"
            val channelName = "Carelyo Care Alerts"
            val descriptionText = "Handles critical medication, appointment, and vaccine reminders."
            val importance = NotificationManager.IMPORTANCE_HIGH // Makes notifications pop up on screen

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = descriptionText
            }

            // Register the channel with the Android system operating environment
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is AuthState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
                is AuthState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Authentication Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}