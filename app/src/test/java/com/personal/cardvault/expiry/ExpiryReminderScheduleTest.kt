package com.personal.cardvault.expiry

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Expectations come from docs/dev-plan.md section 7.6 and checklist U18. */
class ExpiryReminderScheduleTest {
    @Test
    fun u18_beforeNineTargetsNineOnTheSameDay() {
        assertEquals(
            LocalDateTime.of(2026, 11, 19, 9, 0),
            nextExpiryReminderTime(LocalDateTime.of(2026, 11, 19, 8, 59, 59, 999_999_999)),
        )
    }

    @Test
    fun u18_exactlyNineTargetsNineTomorrow() {
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 9, 0),
            nextExpiryReminderTime(LocalDateTime.of(2026, 11, 19, 9, 0)),
        )
    }

    @Test
    fun u18_afterNineTargetsNineTomorrow() {
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 9, 0),
            nextExpiryReminderTime(LocalDateTime.of(2026, 11, 19, 9, 0, 0, 1)),
        )
    }

    @Test
    fun u18_afterNineOnMonthEndTargetsTheFirstOfNextMonth() {
        assertEquals(
            LocalDateTime.of(2026, 12, 1, 9, 0),
            nextExpiryReminderTime(LocalDateTime.of(2026, 11, 30, 23, 59, 59)),
        )
    }

    @Test
    fun u18_exactlyNineOnYearEndTargetsNewYearsDay() {
        assertEquals(
            LocalDateTime.of(2027, 1, 1, 9, 0),
            nextExpiryReminderTime(LocalDateTime.of(2026, 12, 31, 9, 0)),
        )
    }
}
