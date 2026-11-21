package com.personal.cardvault.expiry

import androidx.room.ColumnInfo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Validity(
    @ColumnInfo(name = "starts_on") val startsOn: LocalDate?,
    @ColumnInfo(name = "valid_through") val validThrough: LocalDate,
)

enum class ValidityStatus {
    EXPIRED,
    DUE_TODAY,
    NOT_STARTED,
    VALID,
}

/** The end date is inclusive. The caller supplies the local calendar date. */
fun validityStatus(validity: Validity, today: LocalDate): ValidityStatus = when {
    validity.validThrough.isBefore(today) -> ValidityStatus.EXPIRED
    validity.validThrough == today -> ValidityStatus.DUE_TODAY
    validity.startsOn != null && today.isBefore(validity.startsOn) -> ValidityStatus.NOT_STARTED
    else -> ValidityStatus.VALID
}

/** Negative values indicate days past the inclusive end date. */
fun remainingDays(validity: Validity, today: LocalDate): Long =
    ChronoUnit.DAYS.between(today, validity.validThrough)
