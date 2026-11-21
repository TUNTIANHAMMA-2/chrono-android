package com.personal.cardvault.expiry

import android.app.Notification
import android.content.BroadcastReceiver
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import com.personal.cardvault.MainActivity
import com.personal.cardvault.R
import com.personal.cardvault.data.AppDatabase
import com.personal.cardvault.data.AppSettings
import com.personal.cardvault.data.ExpiryItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Actions encoded in notification PendingIntent data URIs. */
enum class ExpiryNotificationAction(val uriValue: String) {
    OPEN("open"),
    RENEW("renew"),
    PAUSE("pause"),
    DELETE("delete"),
    ;

    companion object {
        fun fromUriValue(value: String): ExpiryNotificationAction? =
            entries.firstOrNull { it.uriValue == value }
    }
}

data class ExpiryNotificationTarget(
    val id: Int,
    val validThrough: LocalDate,
    val action: ExpiryNotificationAction,
)

/**
 * The identity of every notification PendingIntent. Extras are deliberately not used because
 * Android does not consider them when deciding whether two PendingIntents are the same.
 */
fun expiryNotificationUri(
    id: Int,
    validThrough: LocalDate,
    action: ExpiryNotificationAction,
): Uri = Uri.Builder()
    .scheme("cardvault")
    .authority("item")
    .appendPath(id.toString())
    .appendPath(validThrough.toString())
    .appendPath(action.uriValue)
    .build()

fun parseExpiryNotificationUri(uri: Uri): ExpiryNotificationTarget? {
    if (uri.scheme != "cardvault" || uri.authority != "item") return null
    val segments = uri.pathSegments
    if (segments.size != 3) return null
    val id = segments[0].toIntOrNull() ?: return null
    val validThrough = runCatching { LocalDate.parse(segments[1]) }.getOrNull() ?: return null
    val action = ExpiryNotificationAction.fromUriValue(segments[2]) ?: return null
    return ExpiryNotificationTarget(id, validThrough, action)
}

/** Handles only the background notification actions; it never starts an Activity. */
class ExpiryNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val target = intent.data?.let(::parseExpiryNotificationUri) ?: return
        if (target.action != ExpiryNotificationAction.RENEW &&
            target.action != ExpiryNotificationAction.PAUSE
        ) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "cardvault.db",
                ).build()
                handleAction(appContext, database, target)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(
        context: Context,
        database: AppDatabase,
        target: ExpiryNotificationTarget,
    ) {
        val dao = database.expiryItemDao()
        val changed = when (target.action) {
            ExpiryNotificationAction.RENEW -> {
                val item = dao.findById(target.id)
                val newValidThrough = item?.takeIf {
                    it.validity.validThrough == target.validThrough && !it.isPaused
                }?.let {
                    renewedValidThrough(
                        it.validity.validThrough,
                        it.cycle,
                        it.cycleAnchorDay,
                        LocalDate.now(),
                    )
                }
                if (newValidThrough == null) 0 else dao.renewIfCurrent(
                    target.id,
                    target.validThrough,
                    newValidThrough,
                )
            }
            ExpiryNotificationAction.PAUSE -> dao.pauseIfCurrent(target.id, target.validThrough)
            else -> return
        }

        if (changed == 1) {
            rerunExpiryReminderForItem(context, database, target.id)
        } else {
            handleExpiryReminderCurrentState(context, database, target.id)
        }
    }
}

/** Posts the one notification allowed for an expiry item, replacing only that item's old one. */
fun postExpiryNotification(context: Context, item: ExpiryItemEntity, reminder: ExpiryReminder) {
    val validThrough = item.validity.validThrough
    val contentIntent = activityPendingIntent(context, item.id, validThrough, ExpiryNotificationAction.OPEN)
    val notificationBuilder = NotificationCompat.Builder(context, EXPIRY_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_expiry_reminder)
        .setContentTitle(item.name)
        .setContentText(expiryNotificationText(validThrough, reminder))
        .setContentIntent(contentIntent)
        .setAutoCancel(false)
        .addAction(renewAction(context, item))
        .addAction(authenticatedBroadcastAction(context, item.id, validThrough, ExpiryNotificationAction.PAUSE, "暂停"))
        .addAction(deleteAction(context, item.id, validThrough))

    if (AppSettings(context).hideNameOnLockScreen) {
        notificationBuilder
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, EXPIRY_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_expiry_reminder)
                    .setContentTitle("有事项到期")
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build(),
            )
    } else {
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    val notification = notificationBuilder.build()
    NotificationManagerCompat.from(context).notify(item.id, notification)
}

fun expiryNotificationText(validThrough: LocalDate, reminder: ExpiryReminder, today: LocalDate = LocalDate.now()): String {
    val date = if (validThrough.year == today.year) {
        validThrough.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
    } else {
        validThrough.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
    }
    return when (reminder) {
        ExpiryReminder.DUE -> "${date}到期"
        ExpiryReminder.EARLY -> "将于${date}到期"
        ExpiryReminder.NONE -> error("A notification cannot be built for NONE")
    }
}

private fun renewAction(context: Context, item: ExpiryItemEntity): NotificationCompat.Action =
    if (item.cycle == Cycle.NONE) {
        NotificationCompat.Action.Builder(
            0,
            "续期",
            activityPendingIntent(context, item.id, item.validity.validThrough, ExpiryNotificationAction.RENEW),
        ).build()
    } else {
        authenticatedBroadcastAction(context, item.id, item.validity.validThrough, ExpiryNotificationAction.RENEW, "续期")
    }

private fun deleteAction(context: Context, id: Int, validThrough: LocalDate): NotificationCompat.Action =
    NotificationCompat.Action.Builder(
        0,
        "删除",
        activityPendingIntent(context, id, validThrough, ExpiryNotificationAction.DELETE),
    ).build()

private fun authenticatedBroadcastAction(
    context: Context,
    id: Int,
    validThrough: LocalDate,
    action: ExpiryNotificationAction,
    title: String,
): NotificationCompat.Action = NotificationCompat.Action.Builder(
    0,
    title,
    broadcastPendingIntent(context, id, validThrough, action),
).setAuthenticationRequired(true).build()

private fun activityPendingIntent(
    context: Context,
    id: Int,
    validThrough: LocalDate,
    action: ExpiryNotificationAction,
): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).setData(expiryNotificationUri(id, validThrough, action)),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun broadcastPendingIntent(
    context: Context,
    id: Int,
    validThrough: LocalDate,
    action: ExpiryNotificationAction,
): PendingIntent = PendingIntent.getBroadcast(
    context,
    0,
    Intent(context, ExpiryNotificationActionReceiver::class.java)
        .setData(expiryNotificationUri(id, validThrough, action)),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)
