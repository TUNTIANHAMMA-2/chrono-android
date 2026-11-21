package com.personal.cardvault.expiry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

const val EXPIRY_NOTIFICATION_CHANNEL_ID = "expiry_reminders"

/** Creates the single notification channel specified for expiry reminders. */
fun createExpiryNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        EXPIRY_NOTIFICATION_CHANNEL_ID,
        "到期提醒",
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
