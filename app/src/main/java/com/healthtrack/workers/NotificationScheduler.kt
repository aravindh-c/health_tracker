package com.healthtrack.workers

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class NotificationSlot(
    val id: String,
    val hour: Int,
    val minute: Int,
    val messageKey: String
)

object NotificationScheduler {

    private val SLOTS = listOf(
        NotificationSlot("morning",  7,  30, "morning_motivation"),
        NotificationSlot("lunch",   12,  45, "meal_reminders"),
        NotificationSlot("snack",   16,  30, "snack_discipline"),
        NotificationSlot("evening", 20,  30, "evening_reflection")
    )

    /** Schedule all 4 daily notification slots. Safe to call multiple times (KEEP policy). */
    fun scheduleAll(context: Context) {
        SLOTS.forEach { scheduleSlot(context, it) }
    }

    fun scheduleSlot(context: Context, slot: NotificationSlot) {
        val delay = delayUntilNext(slot.hour, slot.minute)

        val data = Data.Builder()
            .putString("slot_id", slot.id)
            .putString("slot_key", slot.messageKey)
            .putInt("hour", slot.hour)
            .putInt("minute", slot.minute)
            .build()

        val request = OneTimeWorkRequestBuilder<MealReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("notification_${slot.id}")
            .build()

        // KEEP = only enqueue if not already queued; avoids duplicate on every app launch
        WorkManager.getInstance(context).enqueueUniqueWork(
            "notification_${slot.id}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Reschedule a slot for next day — called by MealReminderWorker after firing */
    fun rescheduleForNextDay(context: Context, slotId: String, slotKey: String, hour: Int, minute: Int) {
        val data = Data.Builder()
            .putString("slot_id", slotId)
            .putString("slot_key", slotKey)
            .putInt("hour", hour)
            .putInt("minute", minute)
            .build()

        val request = OneTimeWorkRequestBuilder<MealReminderWorker>()
            .setInitialDelay(delayNextDay(hour, minute), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("notification_$slotId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "notification_$slotId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Cancel all scheduled notification workers */
    fun cancelAll(context: Context) {
        SLOTS.forEach {
            WorkManager.getInstance(context).cancelUniqueWork("notification_${it.id}")
        }
    }

    private fun delayUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If already past today's time, schedule for tomorrow
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    private fun delayNextDay(hour: Int, minute: Int): Long {
        val target = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return target.timeInMillis - System.currentTimeMillis()
    }
}
