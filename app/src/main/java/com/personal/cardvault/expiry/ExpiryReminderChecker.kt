package com.personal.cardvault.expiry

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.personal.cardvault.data.AppDatabase
import com.personal.cardvault.data.AppSettings
import com.personal.cardvault.data.ExpiryItemEntity
import java.time.LocalDate

/** The one notification type, if any, that section 7.2 permits for an item. */
enum class ExpiryReminder {
    DUE,
    EARLY,
    NONE,
}

/**
 * Applies the ordered rules in section 7.2. Paused items deliberately do not participate.
 * This function has no clock or database dependency so callers and tests supply all inputs.
 */
fun expiryReminderFor(
    item: ExpiryItemEntity,
    today: LocalDate,
    earlyReminderEnabled: Boolean,
): ExpiryReminder = when {
    item.isPaused -> ExpiryReminder.NONE
    item.validity.validThrough <= today && item.dueNotifiedFor != item.validity.validThrough ->
        ExpiryReminder.DUE
    item.validity.validThrough <= today -> ExpiryReminder.NONE
    earlyReminderEnabled &&
        today >= item.validity.validThrough.minusDays(EARLY_REMINDER_DAYS) &&
        item.earlyNotifiedFor != item.validity.validThrough -> ExpiryReminder.EARLY
    else -> ExpiryReminder.NONE
}

const val EARLY_REMINDER_DAYS = 3L

/**
 * Section 7.7 entry one: remove the old notification, then apply the normal section 7.2
 * rules to the item's current data.
 */
suspend fun rerunExpiryReminderForItem(
    context: Context,
    database: AppDatabase,
    id: Int,
    today: LocalDate = LocalDate.now(),
) {
    NotificationManagerCompat.from(context).cancel(id)
    val item = database.expiryItemDao().findById(id) ?: return
    runExpiryReminderCheckForItem(context, database, item, today)
}

/**
 * Section 7.7 entry two. Unlike the regular check, this deliberately ignores notification
 * markers when the conditional update found that the notification was stale.
 */
suspend fun handleExpiryReminderCurrentState(
    context: Context,
    database: AppDatabase,
    id: Int,
    today: LocalDate = LocalDate.now(),
) {
    val notifications = NotificationManagerCompat.from(context)
    val item = database.expiryItemDao().findById(id)
    if (item == null || item.isPaused) {
        notifications.cancel(id)
        return
    }

    val reminder = expiryReminderForCurrentState(
        item,
        today,
        AppSettings(context).earlyReminderEnabled,
    )
    if (reminder == ExpiryReminder.NONE) {
        notifications.cancel(id)
        return
    }
    if (!notifications.areNotificationsEnabled()) return

    postExpiryNotification(context, item, reminder)
    when (reminder) {
        ExpiryReminder.DUE -> database.expiryItemDao().markDueNotified(id, item.validity.validThrough)
        ExpiryReminder.EARLY -> database.expiryItemDao().markEarlyNotified(id, item.validity.validThrough)
        ExpiryReminder.NONE -> Unit
    }
}

private fun expiryReminderForCurrentState(
    item: ExpiryItemEntity,
    today: LocalDate,
    earlyReminderEnabled: Boolean,
): ExpiryReminder = when {
    item.validity.validThrough <= today -> ExpiryReminder.DUE
    earlyReminderEnabled && today >= item.validity.validThrough.minusDays(EARLY_REMINDER_DAYS) ->
        ExpiryReminder.EARLY
    else -> ExpiryReminder.NONE
}

private suspend fun runExpiryReminderCheckForItem(
    context: Context,
    database: AppDatabase,
    item: ExpiryItemEntity,
    today: LocalDate,
) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

    when (expiryReminderFor(item, today, AppSettings(context).earlyReminderEnabled)) {
        ExpiryReminder.DUE -> {
            postExpiryNotification(context, item, ExpiryReminder.DUE)
            database.expiryItemDao().markDueNotified(item.id, item.validity.validThrough)
        }
        ExpiryReminder.EARLY -> {
            postExpiryNotification(context, item, ExpiryReminder.EARLY)
            database.expiryItemDao().markEarlyNotified(item.id, item.validity.validThrough)
        }
        ExpiryReminder.NONE -> Unit
    }
}

/**
 * Performs a full check and posts the notification before persisting its marker. The optional
 * callback keeps the checking operation usable by later scheduling code without duplicating the
 * notification construction.
 */
suspend fun runExpiryReminderCheck(
    context: Context,
    database: AppDatabase,
    today: LocalDate = LocalDate.now(),
    notify: (ExpiryItemEntity, ExpiryReminder) -> Unit = { item, reminder ->
        postExpiryNotification(context, item, reminder)
    },
) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

    val earlyReminderEnabled = AppSettings(context).earlyReminderEnabled
    val dao = database.expiryItemDao()
    dao.getAll().forEach { item ->
        when (expiryReminderFor(item, today, earlyReminderEnabled)) {
            ExpiryReminder.DUE -> {
                notify(item, ExpiryReminder.DUE)
                dao.markDueNotified(item.id, item.validity.validThrough)
            }
            ExpiryReminder.EARLY -> {
                notify(item, ExpiryReminder.EARLY)
                dao.markEarlyNotified(item.id, item.validity.validThrough)
            }
            ExpiryReminder.NONE -> Unit
        }
    }
}
