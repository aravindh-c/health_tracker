package com.healthtrack.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthtrack.R
import com.healthtrack.ui.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "health_track_reminders"
        const val CHANNEL_NAME = "Meal Reminders"

        // Personalization per user (family app — fixed for now)
        private val personalization = mapOf(
            "aravindh" to mapOf("spouse_name" to "Deepa", "child_name" to "Aadhiran"),
            "deepa" to mapOf("spouse_name" to "Aravindh", "child_name" to "Aadhiran")
        )
    }

    private val gson = Gson()

    init {
        createChannel()
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily meal reminders and motivation"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // Called by MealReminderWorker to show a scheduled slot notification
    fun showReminder(slotKey: String) {
        val securePrefs = SecurePrefs(context)
        val userId = securePrefs.lastActiveUserId
        val profileManager = UserProfileManager(context)
        val name = profileManager.getProfile(userId)?.display_name ?: userId.replaceFirstChar { it.uppercase() }

        val messages = loadMessages()
        val rawMessages = messages[slotKey] ?: messages["morning_motivation"] ?: return
        val rawMessage = rawMessages.random()
        val message = substitute(rawMessage, name, userId)

        val title = when (slotKey) {
            "morning_motivation" -> "Good Morning!"
            "meal_reminders" -> "Meal Reminder"
            "snack_discipline" -> "Snack Time"
            "evening_reflection" -> "Evening Check-in"
            else -> "Health Track"
        }

        showNotification(title, message, slotKey.hashCode())
    }

    private fun showNotification(title: String, message: String, id: Int) {
        // On Android 13+ check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun substitute(template: String, name: String, userId: String): String {
        val extras = personalization[userId] ?: emptyMap()
        return template
            .replace("{name}", name)
            .replace("{spouse_name}", extras["spouse_name"] ?: "your partner")
            .replace("{child_name}", extras["child_name"] ?: "your child")
    }

    private fun loadMessages(): Map<String, List<String>> {
        return try {
            val json = context.assets.open("notifications.json").bufferedReader().readText()
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
