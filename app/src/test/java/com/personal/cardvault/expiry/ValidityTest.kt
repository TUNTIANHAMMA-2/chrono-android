package com.personal.cardvault.expiry

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Expectations come from docs/dev-plan.md sections 5.2, 6.1 and checklist U1/U2. */
class ValidityTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun u1_futureStartIsNotStarted() {
        val validity = Validity(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 30))

        assertEquals(ValidityStatus.NOT_STARTED, validityStatus(validity, today))
    }

    @Test
    fun u1_startDateItselfIsValid() {
        val validity = Validity(today, LocalDate.of(2026, 11, 30))

        assertEquals(ValidityStatus.VALID, validityStatus(validity, today))
    }

    @Test
    fun u1_betweenStartAndEndIsValid() {
        val validity = Validity(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))

        assertEquals(ValidityStatus.VALID, validityStatus(validity, today))
    }

    @Test
    fun u1_withoutStartDateFutureEndIsValid() {
        val validity = Validity(null, LocalDate.of(2026, 11, 30))

        assertEquals(ValidityStatus.VALID, validityStatus(validity, today))
    }

    @Test
    fun u1_endDateItselfIsDueTodayAndNextDayIsExpired() {
        val validity = Validity(null, today)

        assertEquals(ValidityStatus.DUE_TODAY, validityStatus(validity, today))
        assertEquals(
            ValidityStatus.EXPIRED,
            validityStatus(validity, LocalDate.of(2026, 11, 19)),
        )
    }

    @Test
    fun u1_pastEndTakesPriorityOverFutureStart() {
        val validity = Validity(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 17))

        assertEquals(ValidityStatus.EXPIRED, validityStatus(validity, today))
    }

    @Test
    fun u1_todayEndTakesPriorityOverFutureStart() {
        val validity = Validity(LocalDate.of(2026, 11, 30), today)

        assertEquals(ValidityStatus.DUE_TODAY, validityStatus(validity, today))
    }

    @Test
    fun u1_reversedDatesWithFutureEndAreNotStarted() {
        val validity = Validity(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 19))

        assertEquals(ValidityStatus.NOT_STARTED, validityStatus(validity, today))
    }

    @Test
    fun u2_futureEndHasPositiveRemainingDays() {
        val validity = Validity(null, LocalDate.of(2026, 11, 21))

        assertEquals(3L, remainingDays(validity, today))
    }

    @Test
    fun u2_todayEndHasZeroRemainingDays() {
        val validity = Validity(null, today)

        assertEquals(0L, remainingDays(validity, today))
    }

    @Test
    fun u2_pastEndHasNegativeRemainingDaysEvenWithFutureStart() {
        val validity = Validity(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 15))

        assertEquals(-3L, remainingDays(validity, today))
    }

    @Test
    fun u2_remainingDaysCountAcrossYearBoundaryAndIgnoreStartDate() {
        val validity = Validity(LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 2))

        assertEquals(3L, remainingDays(validity, LocalDate.of(2026, 12, 30)))
    }

    @Test
    fun u2_remainingDaysIncludeLeapDay() {
        val validity = Validity(null, LocalDate.of(2028, 3, 1))

        assertEquals(2L, remainingDays(validity, LocalDate.of(2028, 2, 28)))
    }
}
