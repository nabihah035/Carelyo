package com.example.carelyo.utils

object PasswordValidator {

    // Added for real-time tracking
    data class PasswordChecklist(
        val hasMinLength: Boolean = false,
        val hasUppercase: Boolean = false,
        val hasLowercase: Boolean = false,
        val hasDigit: Boolean = false,
        val hasSpecialChar: Boolean = false,
        val isNotObvious: Boolean = false
    ) {
        val isValid: Boolean
            get() = hasMinLength && hasUppercase && hasLowercase && hasDigit && hasSpecialChar && isNotObvious
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    // Real-time calculation function
    fun checkRequirements(password: String, email: String, fullName: String?): PasswordChecklist {
        val specialCharacters = "!@#\$%^&*()_+-=[]{}|;':\",./<>?"

        val lowerPassword = password.lowercase()
        val usernamePart = email.substringBefore("@").lowercase()
        val nameParts = fullName?.lowercase()?.split(" ") ?: emptyList()

        // Check against obvious words or names
        var isSafe = !lowerPassword.contains("password") && !lowerPassword.contains("123456")
        if (usernamePart.isNotEmpty() && lowerPassword.contains(usernamePart)) isSafe = false
        for (part in nameParts) {
            if (part.length > 2 && lowerPassword.contains(part)) {
                isSafe = false
            }
        }

        return PasswordChecklist(
            hasMinLength = password.length >= 8,
            hasUppercase = password.any { it.isUpperCase() },
            hasLowercase = password.any { it.isLowerCase() },
            hasDigit = password.any { it.isDigit() },
            hasSpecialChar = password.any { specialCharacters.contains(it) },
            isNotObvious = isSafe && password.isNotEmpty()
        )
    }

    fun validate(password: String, email: String, fullName: String?): ValidationResult {
        val checks = checkRequirements(password, email, fullName)
        return when {
            !checks.hasMinLength -> ValidationResult.Invalid("Password must be at least 8 characters long.")
            !checks.hasUppercase -> ValidationResult.Invalid("Password must contain at least one uppercase letter (A-Z).")
            !checks.hasLowercase -> ValidationResult.Invalid("Password must contain at least one lowercase letter (a-z).")
            !checks.hasDigit -> ValidationResult.Invalid("Password must contain at least one numerical digit (0-9).")
            !checks.hasSpecialChar -> ValidationResult.Invalid("Password must contain at least one special character.")
            !checks.isNotObvious -> ValidationResult.Invalid("Password contains obvious terms like your name, email handle, or basic sequences.")
            else -> ValidationResult.Valid
        }
    }
}