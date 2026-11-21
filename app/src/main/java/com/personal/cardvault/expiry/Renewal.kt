package com.personal.cardvault.expiry

import java.time.LocalDate
import java.time.YearMonth

/** The recurrence selected for an expiry item. */
enum class Cycle {
    NONE,
    MONTHLY,
    YEARLY,
}

/**
 * Returns the new expiry date for a one-tap renewal, or null when the item has no cycle.
 *
 * The caller supplies [today], so this calculation does not read the system clock. A missing
 * anchor uses the current expiry day. At least one recurrence is always added.
 */
fun renewedValidThrough(
    validThrough: LocalDate,
    cycle: Cycle,
    cycleAnchorDay: Int?,
    today: LocalDate,
): LocalDate? {
    if (cycle == Cycle.NONE) return null

    val anchorDay = cycleAnchorDay ?: validThrough.dayOfMonth
    var next = validThrough
    do {
        next = when (cycle) {
            Cycle.MONTHLY -> next.plusMonths(1)
            Cycle.YEARLY -> next.plusYears(1)
            Cycle.NONE -> error("A cycle is required for one-tap renewal")
        }
        next = next.withDayOfMonth(minOf(anchorDay, YearMonth.from(next).lengthOfMonth()))
    } while (!next.isAfter(today))

    return next
}

/**
 * Determines the anchor to store when an edit is saved.
 *
 * A new item has no previous date or cycle. In that case, and whenever either value changes,
 * the new expiry day becomes the anchor. Otherwise the existing anchor is retained.
 */
fun cycleAnchorDayForSave(
    previousValidThrough: LocalDate?,
    previousCycle: Cycle?,
    previousCycleAnchorDay: Int?,
    validThrough: LocalDate,
    cycle: Cycle,
): Int? = if (
    previousValidThrough == null ||
    previousCycle == null ||
    previousValidThrough != validThrough ||
    previousCycle != cycle
) {
    validThrough.dayOfMonth
} else {
    previousCycleAnchorDay
}
