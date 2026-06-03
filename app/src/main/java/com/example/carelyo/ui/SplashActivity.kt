package com.example.carelyo.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.carelyo.databinding.ActivitySplashBinding
import com.example.carelyo.ui.dashboard.DashboardActivity
import com.example.carelyo.data.session.SessionManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FIX: Instantiate directly via standard constructor matching class definition
        sessionManager = SessionManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            if (sessionManager.isLoggedIn()) {
                startActivity(Intent(this, DashboardActivity::class.java))
            } else {
                // App Flow: Redirects directly to the explicit Auth Selection screen
                startActivity(Intent(this, WelcomeActivity::class.java))
            }
            finish()
        }, 2000)
    }
}