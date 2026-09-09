package com.example.carelyo.data.entity

/**
 * Fixed Database Enums matching the Supabase PostgreSQL database check constraints.
 */

// 1. Allergy
enum class AllergyType(val value: String) {
    FOOD("Food"),
    MEDICATION("Medication"),
    ENVIRONMENTAL("Environmental")
}

enum class AllergySeverity(val value: String) {
    MILD("Mild"),
    MODERATE("Moderate"),
    SEVERE("Severe")
}

// 2. Child Vaccine Status (CHILD_VACCINE table)
enum class VaccineStatusEnum(val value: String) {
    SCHEDULED("Scheduled"),
    DUE("Due"),
    COMPLETED("Completed"),
    OVERDUE("Overdue"),
    SKIPPED("Skipped")
}

// 3. Appointment Status (APPOINTMENT table)
enum class AppointmentStatus(val value: String) {
    SCHEDULED("Scheduled"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    NO_SHOW("No-Show")
}

// 4. Child Status (CHILD table)
enum class ChildStatus(val value: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive")
}

// 5. Clinic Staff Role (CLINIC_STAFF table)
enum class ClinicStaffRole(val value: String) {
    RECEPTIONIST("Receptionist"),
    DOCTOR("Doctor"),
    NURSE("Nurse")
}

// 6. Reminder Table (REMINDER table)
enum class ReminderNotiStatus(val value: String) {
    READ("Read"),
    UNREAD("Unread")
}

// 7. User Table (USER table)
enum class UserRole(val value: String) {
    PARENT("parent"),
    STAFF("staff")
}
