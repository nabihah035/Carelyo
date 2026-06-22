package com.example.carelyo.ui.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.carelyo.databinding.ActivityRegisterBinding
import com.example.carelyo.ui.dashboard.DashboardActivity
import com.example.carelyo.utils.PasswordValidator

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPasswordChecklistListener()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupPasswordChecklistListener() {
        // Run the checker on every keystroke
        binding.etPassword.doAfterTextChanged { text ->
            val password = text?.toString() ?: ""
            val email = binding.etEmail.text.toString().trim()
            val name = binding.etFullName.text.toString().trim()

            val checklist = PasswordValidator.checkRequirements(password, email, name)

            // Update UI indicators dynamically
            updateRuleIndicator(binding.tvRuleLength, checklist.hasMinLength, "✓ At least 8 characters", "○ At least 8 characters")
            updateRuleIndicator(binding.tvRuleUppercase, checklist.hasUppercase, "✓ One uppercase letter (A-Z)", "○ One uppercase letter (A-Z)")
            updateRuleIndicator(binding.tvRuleLowercase, checklist.hasLowercase, "✓ One lowercase letter (a-z)", "○ One lowercase letter (a-z)")
            updateRuleIndicator(binding.tvRuleDigit, checklist.hasDigit, "✓ One numerical digit (0-9)", "○ One numerical digit (0-9)")
            updateRuleIndicator(binding.tvRuleSpecial, checklist.hasSpecialChar, "✓ One special character (!@#\$%)", "○ One special character (!@#\$)")
            updateRuleIndicator(binding.tvRuleObvious, checklist.isNotObvious, "✓ Safe from obvious names/words", "○ Avoid names, email handles, or 'password'")
        }
    }

    private fun updateRuleIndicator(textView: TextView, isFulfilled: Boolean, activeText: String, inactiveText: String) {
        if (isFulfilled) {
            textView.text = activeText
            textView.setTextColor(Color.parseColor("#2E7D32")) // Clean success green
        } else {
            textView.text = inactiveText
            textView.setTextColor(Color.parseColor("#C62828")) // Warning dark red
        }
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhoneNumber.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "All input parameters are explicitly mandatory.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match. Please re-enter.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Final safety assertion using the same engine logic checks
            val checklist = PasswordValidator.checkRequirements(password, email, name)
            if (!checklist.isValid) {
                Toast.makeText(this, "Please fulfill all strong password criteria shown in the checklist.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            viewModel.register(email, password, name, phone, role = "Parent")
        }

        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is AuthState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Account verified and registered successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
                is AuthState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Registration Failure: ${state.message}", Toast.LENGTH_LONG).show()
                }
                else -> {
                    // Handle other states (OtpSent, OtpVerified, PasswordResetSuccess) if necessary
                    // For RegisterActivity, these might not be relevant yet
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }
}