package com.personal.cardvault.expiry

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expectations come from docs/dev-plan.md sections 6.3/6.4 and checklist U5–U10. */
class RenewalTest {
    // The next six tests reproduce every row of the section 6.4 example table.
    @Test
    fun u5_table_commonYearJanuaryEndClampsToFebruaryEnd() {
        assertRenewal("2027-02-28", "2027-01-31", Cycle.MONTHLY, 31, "2027-01-31")
    }

    @Test
    fun u5_table_commonYearFebruaryEndReturnsToMarchAnchor() {
        assertRenewal("2027-03-31", "2027-02-28", Cycle.MONTHLY, 31, "2027-02-28")
    }

    @Test
    fun u6_table_leapDayClampsToNextFebruaryEnd() {
        assertRenewal("2029-02-28", "2028-02-29", Cycle.YEARLY, 29, "2028-02-29")
    }

    @Test
    fun u6_table_yearlyRenewalReturnsToLeapDayAnchor() {
        assertRenewal("2032-02-29", "2031-02-28", Cycle.YEARLY, 29, "2031-02-28")
    }

    @Test
    fun u7_table_overdueMonthsAdvanceToFirstCycleAfterToday() {
        assertRenewal("2026-12-15", "2026-08-15", Cycle.MONTHLY, 15, "2026-11-18")
    }

    @Test
    fun u8_table_earlyMonthlyRenewalStillAdvancesOneCycle() {
        assertRenewal("2027-01-01", "2026-12-01", Cycle.MONTHLY, 1, "2026-11-18")
    }

    @Test
    fun u5_monthlyRenewalsKeepAnchorAcrossCommonAndLeapFebruary() {
        val sequences = listOf(
            listOf("2027-01-31", "2027-02-28", "2027-03-31"),
            listOf("2028-01-31", "2028-02-29", "2028-03-31"),
        )

        for (sequence in sequences) {
            var current = date(sequence.first())
            for (expected in sequence.drop(1)) {
                val renewed = renewedValidThrough(current, Cycle.MONTHLY, 31, current)
                assertEquals(date(expected), renewed)
                current = requireNotNull(renewed)
            }
        }
    }

    @Test
    fun u6_yearlyRenewalsKeepLeapDayAnchorUntilNextLeapYear() {
        var current = date("2028-02-29")

        for (expected in listOf("2029-02-28", "2030-02-28", "2031-02-28", "2032-02-29")) {
            val renewed = renewedValidThrough(current, Cycle.YEARLY, 29, current)
            assertEquals(date(expected), renewed)
            current = requireNotNull(renewed)
        }
    }

    @Test
    fun u7_overdueMonthlyRenewalSkipsCycleLandingExactlyOnToday() {
        assertRenewal("2026-12-15", "2026-08-15", Cycle.MONTHLY, 15, "2026-11-15")
    }

    @Test
    fun u7_overdueMonthlyRenewalStopsAtFirstFutureCycle() {
        assertRenewal("2026-11-15", "2026-08-15", Cycle.MONTHLY, 15, "2026-11-14")
    }

    @Test
    fun u7_overdueMonthlyRenewalRestoresAnchorAfterPassingFebruary() {
        assertRenewal("2027-03-31", "2027-01-31", Cycle.MONTHLY, 31, "2027-02-28")
    }

    @Test
    fun u7_overdueYearlyRenewalStopsAtFirstFutureCycle() {
        assertRenewal("2031-02-28", "2028-02-29", Cycle.YEARLY, 29, "2031-02-27")
    }

    @Test
    fun u7_overdueYearlyRenewalSkipsTodayAndRestoresLeapDay() {
        assertRenewal("2032-02-29", "2028-02-29", Cycle.YEARLY, 29, "2031-02-28")
    }

    @Test
    fun u8_dueTodayMonthlyRenewalAdvancesExactlyOneCycle() {
        assertRenewal("2026-12-15", "2026-11-15", Cycle.MONTHLY, 15, "2026-11-15")
    }

    @Test
    fun u8_earlyYearlyRenewalAdvancesExactlyOneCycleFromCurrentExpiry() {
        assertRenewal("2028-12-01", "2027-12-01", Cycle.YEARLY, 1, "2026-11-18")
    }

    @Test
    fun u8_dueTodayYearlyRenewalAdvancesExactlyOneCycle() {
        assertRenewal("2027-11-18", "2026-11-18", Cycle.YEARLY, 18, "2026-11-18")
    }

    @Test
    fun u9_missingMonthlyAnchorUsesCurrentExpiryDay() {
        assertRenewal("2027-02-28", "2027-01-31", Cycle.MONTHLY, null, "2027-01-31")
    }

    @Test
    fun u9_missingAnchorKeepsOriginalExpiryDayThroughoutOverdueCalculation() {
        assertRenewal("2027-03-31", "2027-01-31", Cycle.MONTHLY, null, "2027-02-28")
    }

    @Test
    fun u9_missingYearlyAnchorUsesCurrentExpiryDay() {
        assertRenewal("2029-02-28", "2028-02-29", Cycle.YEARLY, null, "2028-02-29")
    }

    @Test
    fun u9_missingYearlyAnchorSurvivesOverdueNonLeapYears() {
        assertRenewal("2032-02-29", "2028-02-29", Cycle.YEARLY, null, "2031-02-28")
    }

    @Test
    fun u9_missingAnchorUsesClampedCurrentDayRatherThanInferringAnOlderAnchor() {
        assertRenewal("2027-03-28", "2027-02-28", Cycle.MONTHLY, null, "2027-02-28")
    }

    @Test
    fun noCycleHasNoOneTapRenewal() {
        assertNull(
            renewedValidThrough(date("2026-08-15"), Cycle.NONE, null, date("2026-11-18")),
        )
    }

    @Test
    fun u10_newItemUsesExpiryDayForEveryCycleChoice() {
        for (cycle in Cycle.entries) {
            val anchor = cycleAnchorDayForSave(
                previousValidThrough = null,
                previousCycle = null,
                previousCycleAnchorDay = null,
                validThrough = date("2027-01-31"),
                cycle = cycle,
            )

            assertEquals("New item with $cycle", 31, anchor)
        }
    }

    @Test
    fun u10_changingOnlyExpiryDateReplacesAnchor() {
        val anchor = cycleAnchorDayForSave(
            previousValidThrough = date("2027-01-31"),
            previousCycle = Cycle.MONTHLY,
            previousCycleAnchorDay = 31,
            validThrough = date("2027-01-15"),
            cycle = Cycle.MONTHLY,
        )

        assertEquals(15, anchor)
        assertRenewal("2027-02-15", "2027-01-15", Cycle.MONTHLY, anchor, "2027-01-15")
    }

    @Test
    fun u10_dateChangeWithSameDayOfMonthStillResetsAnchor() {
        val anchor = cycleAnchorDayForSave(
            previousValidThrough = date("2027-02-28"),
            previousCycle = Cycle.MONTHLY,
            previousCycleAnchorDay = 31,
            validThrough = date("2027-03-28"),
            cycle = Cycle.MONTHLY,
        )

        assertEquals(28, anchor)
    }

    @Test
    fun u10_changingOnlyCycleReplacesAnchorIncludingNoCycleTransitions() {
        for (previousCycle in Cycle.entries) {
            for (cycle in Cycle.entries.filter { it != previousCycle }) {
                val anchor = cycleAnchorDayForSave(
                    previousValidThrough = date("2027-02-28"),
                    previousCycle = previousCycle,
                    previousCycleAnchorDay = 31,
                    validThrough = date("2027-02-28"),
                    cycle = cycle,
                )

                assertEquals("Cycle changed from $previousCycle to $cycle", 28, anchor)
            }
        }
    }

    @Test
    fun u10_changingBothExpiryAndCycleUsesNewExpiryDay() {
        val anchor = cycleAnchorDayForSave(
            previousValidThrough = date("2027-01-31"),
            previousCycle = Cycle.MONTHLY,
            previousCycleAnchorDay = 31,
            validThrough = date("2027-02-15"),
            cycle = Cycle.YEARLY,
        )

        assertEquals(15, anchor)
    }

    @Test
    fun u10_unchangedExpiryAndCycleKeepExistingAnchorIncludingNull() {
        for (cycle in Cycle.entries) {
            for (previousAnchor in listOf(31, null)) {
                val anchor = cycleAnchorDayForSave(
                    previousValidThrough = date("2027-02-28"),
                    previousCycle = cycle,
                    previousCycleAnchorDay = previousAnchor,
                    validThrough = date("2027-02-28"),
                    cycle = cycle,
                )

                assertEquals("Unchanged $cycle with anchor $previousAnchor", previousAnchor, anchor)
            }
        }
    }

    private fun assertRenewal(
        expected: String,
        current: String,
        cycle: Cycle,
        anchor: Int?,
        today: String,
    ) {
        assertEquals(
            "Renew $current with $cycle, anchor $anchor, today $today",
            date(expected),
            renewedValidThrough(date(current), cycle, anchor, date(today)),
        )
    }

    private fun date(value: String): LocalDate = LocalDate.parse(value)
}
