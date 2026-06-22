package com.example.carelyo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.carelyo.databinding.ActivityVerifyOtpBinding

class VerifyOtpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyOtpBinding
    private val viewModel: AuthViewModel by viewModels()
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra("email") ?: ""
        binding.tvEmail.text = "We sent a 6-digit code to:\n$email"

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            if (otp.length == 6) {
                viewModel.verifyOtp(email, otp)
            } else {
                Toast.makeText(this, "Please enter the 6-digit OTP", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResendOtp.setOnClickListener {
            viewModel.sendPasswordResetOtp(email)
            Toast.makeText(this, "New OTP sent to your email", Toast.LENGTH_SHORT).show()
        }

        binding.tvBackToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnBack.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnVerify.isEnabled = false
                }
                is AuthState.OtpVerified -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnVerify.isEnabled = true

                    Toast.makeText(this, "OTP verified! Set your new password.", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, ResetPasswordActivity::class.java)
                    intent.putExtra("email", state.email)
                    startActivity(intent)
                    finish()
                }
                is AuthState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnVerify.isEnabled = true
                    Toast.makeText(this, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }
}