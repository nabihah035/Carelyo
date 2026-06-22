package com.example.carelyo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.carelyo.databinding.ActivityResetPasswordBinding
import com.example.carelyo.utils.PasswordValidator
import androidx.core.widget.doAfterTextChanged
import android.widget.TextView

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private val viewModel: AuthViewModel by viewModels()
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra("email") ?: ""

        setupClickListeners()
        setupPasswordValidation()
        observeViewModel()
    }

    private fun setupPasswordValidation() {
        binding.etNewPassword.doAfterTextChanged { text ->
            val password = text?.toString() ?: ""
            val checklist = PasswordValidator.checkRequirements(password, email, "")

            // Update UI indicators
            updateIndicator(binding.tvRuleLength, checklist.hasMinLength,
                "✓ At least 8 characters", "○ At least 8 characters")
            updateIndicator(binding.tvRuleUppercase, checklist.hasUppercase,
                "✓ One uppercase letter (A-Z)", "○ One uppercase letter (A-Z)")
            updateIndicator(binding.tvRuleLowercase, checklist.hasLowercase,
                "✓ One lowercase letter (a-z)", "○ One lowercase letter (a-z)")
            updateIndicator(binding.tvRuleDigit, checklist.hasDigit,
                "✓ One numerical digit (0-9)", "○ One numerical digit (0-9)")
            updateIndicator(binding.tvRuleSpecial, checklist.hasSpecialChar,
                "✓ One special character (!@#\$%)", "○ One special character (!@#\$%)")
            updateIndicator(binding.tvRuleObvious, checklist.isNotObvious,
                "✓ Safe from obvious names/words", "○ Avoid names, email handles, or 'password'")
        }
    }

    private fun updateIndicator(textView: TextView, isFulfilled: Boolean, activeText: String, inactiveText: String) {
        if (isFulfilled) {
            textView.text = activeText
            textView.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
        } else {
            textView.text = inactiveText
            textView.setTextColor(android.graphics.Color.parseColor("#C62828"))
        }
    }

    private fun setupClickListeners() {
        binding.btnResetPassword.setOnClickListener {
            val newPassword = binding.etNewPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in both password fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate password strength
            val checklist = PasswordValidator.checkRequirements(newPassword, email, "")
            if (!checklist.isValid) {
                Toast.makeText(this, "Please meet all password requirements", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            viewModel.resetPasswordWithOtp(email, newPassword)
        }

        binding.tvBackToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnBack.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnResetPassword.isEnabled = false
                }
                is AuthState.PasswordResetSuccess -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnResetPassword.isEnabled = true

                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                is AuthState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnResetPassword.isEnabled = true
                    Toast.makeText(this, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }
}