package com.example.carelyo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.carelyo.R
import com.example.carelyo.ui.reminder.ReminderActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CarelyoFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token generated: $token")
        val prefs = getSharedPreferences("carelyo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM packet received from: ${remoteMessage.from}")

        val prefs = getSharedPreferences("carelyo_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications disabled by user. Suppressing push notification.")
            return
        }

        var title: String? = null
        var message: String? = null

        // Check if message contains data payloads (custom key-value pairs from Supabase/FCM)
        if (remoteMessage.data.isNotEmpty()) {
            title = remoteMessage.data["title"]
            message = remoteMessage.data["message"] ?: remoteMessage.data["body"]
        }

        // Fallback to notification payload if not in data payload
        if (title == null && remoteMessage.notification != null) {
            title = remoteMessage.notification?.title
            message = remoteMessage.notification?.body
        }

        val finalTitle = title ?: "Carelyo Alert"
        val finalMessage = message ?: "You have a new health update."

        showNotification(finalTitle, finalMessage)
    }

    private fun showNotification(title: String, messageBody: String) {
        ensureNotificationChannel(this)

        val intent = Intent(this, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "CarelyoFCM"
        const val CHANNEL_ID = "carelyo_reminders"
        const val CHANNEL_NAME = "Carelyo Care Alerts"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (existingChannel == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Handles critical medication, appointment, and vaccine reminders."
                        enableLights(true)
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }
        }
    }
}