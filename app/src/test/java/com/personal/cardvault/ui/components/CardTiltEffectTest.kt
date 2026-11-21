package com.personal.cardvault.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class CardTiltEffectTest {

    @Test
    fun normalizeTiltAngles_atBaseline_returnsZero() {
        val state = normalizeTiltAngles(
            rawPitchRad = -0.65f,
            rawRollRad = 0.12f,
            basePitchRad = -0.65f,
            baseRollRad = 0.12f
        )
        assertEquals(0f, state.pitch, 1e-4f)
        assertEquals(0f, state.roll, 1e-4f)
    }

    @Test
    fun normalizeTiltAngles_beyondMax_clampsToUnitRange() {
        val state = normalizeTiltAngles(
            rawPitchRad = 1.2f,
            rawRollRad = -1.2f,
            basePitchRad = 0f,
            baseRollRad = 0f,
            maxAngleRad = 0.44f
        )
        assertEquals(1f, state.pitch, 1e-4f)
        assertEquals(-1f, state.roll, 1e-4f)
    }

    @Test
    fun wrapAngleRad_handlesPiBoundaryContinuously() {
        val nearPlusPi = PI.toFloat() - 0.05f
        val nearMinusPi = -PI.toFloat() + 0.05f
        val delta = wrapAngleRad(nearMinusPi - nearPlusPi)
        assertEquals(0.10f, delta, 1e-3f)
    }
}
