package com.personal.cardvault.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CardFlipGestureTest {

    @Test
    fun horizontalConeWithin25DegreesSwitchesCardAndBeyondFlipsCard() {
        // 0°, ±15°, ±24.9° -> SWITCH_CARD
        for (deg in listOf(0.0, 15.0, -15.0, 24.9, -24.9)) {
            val rad = Math.toRadians(deg)
            assertEquals(
                "Angle $deg° should switch card",
                CardGestureMode.SWITCH_CARD,
                classifyCardDragGesture(cos(rad).toFloat() * 100f, sin(rad).toFloat() * 100f),
            )
            // Leftward swipe as well
            assertEquals(
                "Leftward angle $deg° should switch card",
                CardGestureMode.SWITCH_CARD,
                classifyCardDragGesture(-cos(rad).toFloat() * 100f, sin(rad).toFloat() * 100f),
            )
        }

        // ±26°, ±35°, ±45°, ±90° -> FLIP_CARD
        for (deg in listOf(26.0, -26.0, 35.0, -35.0, 45.0, -45.0, 90.0, -90.0)) {
            val rad = Math.toRadians(deg)
            assertEquals(
                "Angle $deg° should flip card",
                CardGestureMode.FLIP_CARD,
                classifyCardDragGesture(cos(rad).toFloat() * 100f, sin(rad).toFloat() * 100f),
            )
        }
    }

    @Test
    fun dampedSnapLandsOnNearestFrontOrBackAcrossFull360() {
        assertEquals(0f, computeNextFlipSnapAngle(30f, 0f), 0.01f)
        assertEquals(180f, computeNextFlipSnapAngle(110f, 0f), 0.01f)
        assertEquals(360f, computeNextFlipSnapAngle(300f, 0f), 0.01f)
        assertEquals(-180f, computeNextFlipSnapAngle(-120f, 0f), 0.01f)
        assertEquals(-360f, computeNextFlipSnapAngle(-310f, 0f), 0.01f)
        // High positive fling inertia pushes 45° over to 180°
        assertEquals(180f, computeNextFlipSnapAngle(45f, 800f), 0.01f)
    }
}
