package com.personal.cardvault.data

import com.personal.cardvault.expiry.Cycle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expectations: dev-plan.md sections 5, 5.1 and T2.3. */
class AppDatabaseConvertersTest {
    private val converters = AppDatabaseConverters()

    @Test
    fun datesUseYearMonthDayTextWithZeroPadding() {
        val examples = listOf(
            LocalDate.of(2026, 1, 9) to "2026-01-09",
            LocalDate.of(2028, 2, 29) to "2028-02-29",
            LocalDate.of(2026, 11, 18) to "2026-11-18",
        )
        for ((date, text) in examples) {
            assertEquals(text, converters.localDateToText(date))
            assertEquals(date, converters.textToLocalDate(text))
        }
    }

    @Test
    fun optionalDatesRemainNullInBothDirections() {
        assertNull(converters.localDateToText(null))
        assertNull(converters.textToLocalDate(null))
    }

    @Test
    fun documentedCyclesRoundTripAsText() {
        val examples = listOf(
            Cycle.NONE to "NONE",
            Cycle.MONTHLY to "MONTHLY",
            Cycle.YEARLY to "YEARLY",
        )
        for ((cycle, text) in examples) {
            assertEquals(text, converters.cycleToText(cycle))
            assertEquals(cycle, converters.textToCycle(text))
        }
    }
}
