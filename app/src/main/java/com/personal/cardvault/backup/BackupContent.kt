package com.personal.cardvault.backup

import com.personal.cardvault.data.ExpiryItemEntity
import com.personal.cardvault.expiry.Cycle
import com.personal.cardvault.expiry.Validity
import com.personal.cardvault.vault.CardPayload
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** The JSON plaintext encrypted into a backup file. */
@Serializable
data class BackupContent(
    val exportedAt: String,
    val expiryItems: List<BackupExpiryItem>,
    val cards: List<BackupCard>,
)

/** An expiry item as stored in a backup, without its database ID or notification markers. */
@Serializable
data class BackupExpiryItem(
    val name: String,
    val category: String,
    val startsOn: String?,
    val validThrough: String,
    val notes: String,
    val isPaused: Boolean,
    val cycle: String,
    val cycleAnchorDay: Int?,
)

/** A payment-card plaintext payload as stored in a backup. */
@Serializable
data class BackupCard(
    val name: String,
    val issuer: String,
    val number: String,
    val expMonth: String,
    val expYear: String,
    val cvv: String,
    val notes: String,
)

fun ExpiryItemEntity.toBackupExpiryItem(): BackupExpiryItem = BackupExpiryItem(
    name = name,
    category = category,
    startsOn = validity.startsOn?.toString(),
    validThrough = validity.validThrough.toString(),
    notes = notes,
    isPaused = isPaused,
    cycle = cycle.name,
    cycleAnchorDay = cycleAnchorDay,
)

fun BackupExpiryItem.toExpiryItemEntity(): ExpiryItemEntity = ExpiryItemEntity(
    name = name,
    category = category,
    validity = Validity(
        startsOn = startsOn?.let(LocalDate::parse),
        validThrough = LocalDate.parse(validThrough),
    ),
    notes = notes,
    isPaused = isPaused,
    cycle = Cycle.valueOf(cycle),
    cycleAnchorDay = cycleAnchorDay,
    earlyNotifiedFor = null,
    dueNotifiedFor = null,
)

fun CardPayload.toBackupCard(): BackupCard = BackupCard(
    name = name,
    issuer = issuer,
    number = number,
    expMonth = expMonth,
    expYear = expYear,
    cvv = cvv,
    notes = notes,
)

fun BackupCard.toCardPayload(): CardPayload = CardPayload(
    name = name,
    issuer = issuer,
    number = number,
    expMonth = expMonth,
    expYear = expYear,
    cvv = cvv,
    notes = notes,
)
