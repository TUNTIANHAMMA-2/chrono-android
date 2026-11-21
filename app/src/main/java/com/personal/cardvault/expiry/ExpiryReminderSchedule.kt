package com.personal.cardvault.expiry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personal.cardvault.data.expiryDatabase
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val EXPIRY_REMINDER_WORK_NAME = "expiry_reminder_daily"
private const val REMINDER_INTERVAL_HOURS = 24L
private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

/** Returns the next local 09:00; exactly 09:00 belongs to the following day. */
fun nextExpiryReminderTime(now: LocalDateTime): LocalDateTime =
    if (now.toLocalTime().isBefore(REMINDER_TIME)) {
        now.toLocalDate().atTime(REMINDER_TIME)
    } else {
        now.toLocalDate().plusDays(1).atTime(REMINDER_TIME)
    }

/** Replaces the one periodic request so its next execution remains targeted at local 09:00. */
fun scheduleExpiryReminder(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
    val nextRun = nextExpiryReminderTime(now.toLocalDateTime()).atZone(now.zone)
    val request = PeriodicWorkRequestBuilder<ExpiryReminderWorker>(
        REMINDER_INTERVAL_HOURS,
        TimeUnit.HOURS,
    )
        .setNextScheduleTimeOverride(nextRun.toInstant().toEpochMilli())
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        EXPIRY_REMINDER_WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        request,
    )
}

/** Runs the normal full check, then retargets the periodic work after every execution. */
class ExpiryReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = try {
        val database = expiryDatabase(applicationContext)
        runExpiryReminderCheck(applicationContext, database)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    } finally {
        scheduleExpiryReminder(applicationContext)
    }
}

/** Starts the required foreground full check and resets the daily schedule. */
suspend fun runExpiryReminderStartup(context: Context) {
    try {
        runExpiryReminderCheck(context, expiryDatabase(context))
    } finally {
        scheduleExpiryReminder(context)
    }
}

internal fun expiryReminderWorkName(): String = EXPIRY_REMINDER_WORK_NAME
