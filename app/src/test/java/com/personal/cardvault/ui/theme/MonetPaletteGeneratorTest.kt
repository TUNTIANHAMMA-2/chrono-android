package com.personal.cardvault.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetPaletteGeneratorTest {

    @Test
    fun lightAndDarkSchemesMaintainExpectedLuminancePolarity() {
        val seeds = listOf(
            Color(0xFF4C5C92),
            Color(0xFF188140),
            Color(0xFF7B46AF),
            Color(0xFFD63E5C),
            Color(0xFFFDD835),
            Color(0xFF00796B),
            Color(0xFF808080),
        )
        for (seed in seeds) {
            val light = MonetPaletteGenerator.generateColorScheme(seed, darkTheme = false)
            assertTrue("Light background should be bright for $seed", light.background.luminance() > 0.85f)
            assertTrue("Light onBackground should be dark for $seed", light.onBackground.luminance() < 0.12f)
            assertTrue("Light primary should be darker than white onPrimary for $seed", light.primary.luminance() < 0.35f)

            val dark = MonetPaletteGenerator.generateColorScheme(seed, darkTheme = true, amoledDark = false)
            assertTrue("Dark background should be dark for $seed", dark.background.luminance() < 0.06f)
            assertTrue("Dark onBackground should be bright for $seed", dark.onBackground.luminance() > 0.70f)
            assertTrue("Dark primary should be bright for $seed", dark.primary.luminance() > 0.35f)

            val amoled = MonetPaletteGenerator.generateColorScheme(seed, darkTheme = true, amoledDark = true)
            assertEquals("AMOLED background must be pure black", PureBlack, amoled.background)
            assertEquals("AMOLED surface must be pure black", PureBlack, amoled.surface)
        }
    }

    @Test
    fun presetLookupFallsBackCleanly() {
        assertEquals(AppThemePreset.MONET, AppThemePreset.fromNameOrDefault(null, fallbackDynamic = true))
        assertEquals(AppThemePreset.ARCHIVAL_PAPER, AppThemePreset.fromNameOrDefault(null, fallbackDynamic = false))
        assertEquals(AppThemePreset.MIDNIGHT_DUSK, AppThemePreset.fromNameOrDefault("MIDNIGHT_DUSK", fallbackDynamic = false))
        assertEquals(AppThemePreset.CUSTOM_SEED, AppThemePreset.fromNameOrDefault("CUSTOM_SEED", fallbackDynamic = true))
    }
}
