package com.example.carelyo.utils

import java.text.SimpleDateFormat
import java.util.*

object MedicationSchedulerHelper {

    /**
     * Returns scheduled hours of the day (24-hour format) based on frequency string.
     * 1 time/day -> [8] (08:00)
     * 2 times/day -> [8, 20] (08:00, 20:00)
     * 3 times/day -> [8, 14, 20] (08:00, 14:00, 20:00)
     */
    fun getScheduledHours(frequency: String?): List<Int> {
        val freq = frequency?.lowercase() ?: ""
        return when {
            freq.contains("3") || freq.contains("three") -> listOf(8, 14, 20)
            freq.contains("2") || freq.contains("two") || freq.contains("twice") -> listOf(8, 20)
            else -> listOf(8)
        }
    }

    /**
     * Returns human-readable time strings for frequency (e.g. ["08:00"], ["08:00", "20:00"], ["08:00", "14:00", "20:00"]).
     */
    fun getScheduledTimeStrings(frequency: String?): List<String> {
        return getScheduledHours(frequency).map { hour ->
            String.format(Locale.getDefault(), "%02d:00", hour)
        }
    }

    /**
     * Formats frequency label with its scheduled times.
     * E.g. "1 time/day (08:00)", "2 times/day (08:00, 20:00)", "3 times/day (08:00, 14:00, 20:00)"
     */
    fun formatFrequencyWithTimes(frequency: String?): String {
        val hours = getScheduledHours(frequency)
        val timesStr = hours.joinToString(", ") { String.format(Locale.getDefault(), "%02d:00", it) }
        val count = hours.size
        val freqLabel = if (count == 1) "1 time/day" else "$count times/day"
        return "$freqLabel ($timesStr)"
    }

    /**
     * Generates ISO-8601 timestamp strings for the scheduled times for a medication.
     * Starts from startDate if provided, else today.
     */
    fun generateScheduleTimestamps(startDateStr: String?, frequency: String?): List<String> {
        val hours = getScheduledHours(frequency)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        val baseDate = if (!startDateStr.isNullOrEmpty()) {
            try {
                dateFormatter.parse(startDateStr) ?: Date()
            } catch (_: Exception) {
                Date()
            }
        } else {
            Date()
        }

        return hours.map { hour ->
            val cal = Calendar.getInstance().apply {
                time = baseDate
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            isoFormatter.format(cal.time)
        }
    }

    /**
     * Extracts hour of the day (0-23) from a scheduled_time string (ISO or simple time).
     */
    fun extractHourFromSchedule(scheduledTime: String?): Int? {
        if (scheduledTime.isNullOrEmpty()) return null

        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "HH:mm:ss",
            "HH:mm",
            "hh:mm a",
            "h:mm a"
        )

        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val parsed = sdf.parse(scheduledTime)
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply { time = parsed }
                    return cal.get(Calendar.HOUR_OF_DAY)
                }
            } catch (_: Exception) {}
        }

        // Direct regex fallback for strings like "08:00" or "T08:00"
        val regex = Regex("""(?:T|\s|^)(\d{1,2}):\d{2}""")
        val match = regex.find(scheduledTime)
        if (match != null) {
            val hourStr = match.groupValues[1]
            return hourStr.toIntOrNull()
        }

        return null
    }

    /**
     * Formats an hour integer to standard "08:00" or "20:00" string.
     */
    fun formatHourToString(hour: Int): String {
        return String.format(Locale.getDefault(), "%02d:00", hour)
    }
}
