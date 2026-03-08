package com.healthtrack.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.healthtrack.utils.NotificationHelper

/**
 * WorkManager worker that fires a scheduled meal reminder notification,
 * then automatically reschedules itself for the same time the next day.
 */
class MealReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val slotId  = inputData.getString("slot_id")  ?: return Result.success()
        val slotKey = inputData.getString("slot_key") ?: return Result.success()
        val hour    = inputData.getInt("hour", 8)
        val minute  = inputData.getInt("minute", 0)

        // Show the notification
        NotificationHelper(applicationContext).showReminder(slotKey)

        // Reschedule for the same time tomorrow
        NotificationScheduler.rescheduleForNextDay(applicationContext, slotId, slotKey, hour, minute)

        return Result.success()
    }
}
