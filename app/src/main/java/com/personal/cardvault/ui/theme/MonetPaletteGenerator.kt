package com.personal.cardvault.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 纯 Kotlin OKLCH 感知色域莫奈（Material 3 Tonal Palette）生成器。
 *
 * 通过 OKLab / OKLCH 感知亮度与色度空间，从任意单个种子色（Seed Color）派生出
 * 符合 Material 3 对比度与层次规范的完整浅色 / 深色 / AMOLED 纯黑 ColorScheme，
 * 无需依赖系统壁纸，也不引入额外第三方库。
 */
object MonetPaletteGenerator {

    private data class Oklch(val l: Float, val c: Float, val h: Float)

    fun generateColorScheme(
        seedColor: Color,
        darkTheme: Boolean,
        amoledDark: Boolean = false,
        tertiaryHueShiftDeg: Float = 60f,
    ): ColorScheme {
        val seed = srgbToOklch(seedColor)
        val primaryHue = seed.h
        // 保证种子色即使偏灰也能产生有辨识度的主色，同时防止超饱和刺眼
        val baseChroma = seed.c.coerceIn(0.06f, 0.22f)
        val secondaryChroma = (baseChroma * 0.34f).coerceIn(0.02f, 0.075f)
        val tertiaryHue = (primaryHue + tertiaryHueShiftDeg).mod(360f)
        val tertiaryChroma = (baseChroma * 0.65f).coerceIn(0.045f, 0.14f)
        val neutralChroma = (baseChroma * 0.10f).coerceIn(0.006f, 0.022f)
        val neutralVariantChroma = (baseChroma * 0.20f).coerceIn(0.014f, 0.042f)

        return if (darkTheme) {
            buildDarkScheme(
                primaryHue = primaryHue,
                primaryChroma = baseChroma,
                secondaryChroma = secondaryChroma,
                tertiaryHue = tertiaryHue,
                tertiaryChroma = tertiaryChroma,
                neutralChroma = neutralChroma,
                neutralVariantChroma = neutralVariantChroma,
                amoledDark = amoledDark,
            )
        } else {
            buildLightScheme(
                primaryHue = primaryHue,
                primaryChroma = baseChroma,
                secondaryChroma = secondaryChroma,
                tertiaryHue = tertiaryHue,
                tertiaryChroma = tertiaryChroma,
                neutralChroma = neutralChroma,
                neutralVariantChroma = neutralVariantChroma,
            )
        }
    }

    private fun buildLightScheme(
        primaryHue: Float,
        primaryChroma: Float,
        secondaryChroma: Float,
        tertiaryHue: Float,
        tertiaryChroma: Float,
        neutralChroma: Float,
        neutralVariantChroma: Float,
    ): ColorScheme {
        val primary = toneColor(0.49f, primaryChroma, primaryHue)
        val onPrimary = Color.White
        val primaryContainer = toneColor(0.91f, primaryChroma * 0.55f, primaryHue)
        val onPrimaryContainer = toneColor(0.24f, primaryChroma * 0.85f, primaryHue)

        val secondary = toneColor(0.49f, secondaryChroma, primaryHue)
        val onSecondary = Color.White
        val secondaryContainer = toneColor(0.91f, secondaryChroma * 0.85f, primaryHue)
        val onSecondaryContainer = toneColor(0.25f, secondaryChroma * 1.15f, primaryHue)

        val tertiary = toneColor(0.50f, tertiaryChroma, tertiaryHue)
        val onTertiary = Color.White
        val tertiaryContainer = toneColor(0.91f, tertiaryChroma * 0.55f, tertiaryHue)
        val onTertiaryContainer = toneColor(0.25f, tertiaryChroma * 0.85f, tertiaryHue)

        val background = toneColor(0.975f, neutralChroma, primaryHue)
        val onBackground = toneColor(0.20f, neutralChroma * 0.9f, primaryHue)
        val surface = background
        val onSurface = onBackground
        val surfaceVariant = toneColor(0.92f, neutralVariantChroma, primaryHue)
        val onSurfaceVariant = toneColor(0.42f, neutralVariantChroma * 1.1f, primaryHue)

        val surfaceContainerLowest = Color(0xFFFFFFFF)
        val surfaceContainerLow = toneColor(0.962f, neutralChroma * 1.1f, primaryHue)
        val surfaceContainer = toneColor(0.944f, neutralChroma * 1.25f, primaryHue)
        val surfaceContainerHigh = toneColor(0.924f, neutralChroma * 1.35f, primaryHue)
        val surfaceContainerHighest = toneColor(0.902f, neutralChroma * 1.45f, primaryHue)

        val outline = toneColor(0.56f, neutralVariantChroma * 1.0f, primaryHue)
        val outlineVariant = toneColor(0.84f, neutralVariantChroma * 0.95f, primaryHue)

        val inverseSurface = toneColor(0.27f, neutralChroma, primaryHue)
        val inverseOnSurface = toneColor(0.95f, neutralChroma * 0.6f, primaryHue)
        val inversePrimary = toneColor(0.80f, primaryChroma * 0.78f, primaryHue)

        return lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            outline = outline,
            outlineVariant = outlineVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
    }

    private fun buildDarkScheme(
        primaryHue: Float,
        primaryChroma: Float,
        secondaryChroma: Float,
        tertiaryHue: Float,
        tertiaryChroma: Float,
        neutralChroma: Float,
        neutralVariantChroma: Float,
        amoledDark: Boolean,
    ): ColorScheme {
        val primary = toneColor(0.81f, primaryChroma * 0.78f, primaryHue)
        val onPrimary = toneColor(0.25f, primaryChroma * 0.85f, primaryHue)
        val primaryContainer = toneColor(0.36f, primaryChroma * 0.88f, primaryHue)
        val onPrimaryContainer = toneColor(0.92f, primaryChroma * 0.45f, primaryHue)

        val secondary = toneColor(0.80f, secondaryChroma * 0.95f, primaryHue)
        val onSecondary = toneColor(0.26f, secondaryChroma * 1.1f, primaryHue)
        val secondaryContainer = toneColor(0.35f, secondaryChroma * 1.05f, primaryHue)
        val onSecondaryContainer = toneColor(0.91f, secondaryChroma * 0.8f, primaryHue)

        val tertiary = toneColor(0.82f, tertiaryChroma * 0.78f, tertiaryHue)
        val onTertiary = toneColor(0.26f, tertiaryChroma * 0.85f, tertiaryHue)
        val tertiaryContainer = toneColor(0.37f, tertiaryChroma * 0.85f, tertiaryHue)
        val onTertiaryContainer = toneColor(0.92f, tertiaryChroma * 0.45f, tertiaryHue)

        val background = if (amoledDark) PureBlack else toneColor(0.165f, neutralChroma, primaryHue)
        val onBackground = toneColor(0.91f, neutralChroma * 0.75f, primaryHue)
        val surface = background
        val onSurface = onBackground
        val surfaceVariant = if (amoledDark) {
            toneColor(0.21f, neutralVariantChroma * 0.8f, primaryHue)
        } else {
            toneColor(0.29f, neutralVariantChroma, primaryHue)
        }
        val onSurfaceVariant = toneColor(0.77f, neutralVariantChroma * 0.95f, primaryHue)

        val surfaceContainerLowest = if (amoledDark) PureBlack else toneColor(0.135f, neutralChroma, primaryHue)
        val surfaceContainerLow = if (amoledDark) {
            toneColor(0.145f, neutralChroma * 0.7f, primaryHue)
        } else {
            toneColor(0.195f, neutralChroma * 1.05f, primaryHue)
        }
        val surfaceContainer = if (amoledDark) {
            toneColor(0.175f, neutralChroma * 0.85f, primaryHue)
        } else {
            toneColor(0.22f, neutralChroma * 1.15f, primaryHue)
        }
        val surfaceContainerHigh = if (amoledDark) {
            toneColor(0.215f, neutralChroma * 0.95f, primaryHue)
        } else {
            toneColor(0.255f, neutralChroma * 1.25f, primaryHue)
        }
        val surfaceContainerHighest = if (amoledDark) {
            toneColor(0.26f, neutralChroma * 1.05f, primaryHue)
        } else {
            toneColor(0.295f, neutralChroma * 1.35f, primaryHue)
        }

        val outline = toneColor(0.60f, neutralVariantChroma * 0.95f, primaryHue)
        val outlineVariant = if (amoledDark) {
            toneColor(0.28f, neutralVariantChroma * 0.8f, primaryHue)
        } else {
            toneColor(0.36f, neutralVariantChroma * 0.95f, primaryHue)
        }

        val inverseSurface = toneColor(0.91f, neutralChroma * 0.7f, primaryHue)
        val inverseOnSurface = toneColor(0.24f, neutralChroma, primaryHue)
        val inversePrimary = toneColor(0.49f, primaryChroma, primaryHue)

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            outline = outline,
            outlineVariant = outlineVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
    }

    /**
     * 将任意深色 ColorScheme 适配为 AMOLED 纯黑背景。
     */
    fun applyAmoledPureBlack(scheme: ColorScheme): ColorScheme {
        return scheme.copy(
            background = PureBlack,
            surface = PureBlack,
            surfaceContainerLowest = PureBlack,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF242424),
        )
    }

    /**
     * 在保证位于 sRGB 色域内的前提下，按指定 OKLCH 生成 Color（二分收缩色度避免色相偏移）。
     */
    private fun toneColor(l: Float, targetC: Float, hueDeg: Float): Color {
        val hRad = (hueDeg * PI / 180.0)
        val cosH = cos(hRad)
        val sinH = sin(hRad)
        val clampedL = l.coerceIn(0f, 1f).toDouble()

        var lo = 0.0
        var hi = max(0.0, targetC.toDouble())
        if (isInSrgbGamut(clampedL, hi * cosH, hi * sinH)) {
            return oklabToSrgb(clampedL, hi * cosH, hi * sinH)
        }
        repeat(12) {
            val mid = (lo + hi) * 0.5
            if (isInSrgbGamut(clampedL, mid * cosH, mid * sinH)) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return oklabToSrgb(clampedL, lo * cosH, lo * sinH)
    }

    private fun srgbToLinear(v: Float): Double {
        val d = v.toDouble()
        return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }

    private fun linearToSrgb(v: Double): Float {
        val clamped = v.coerceIn(0.0, 1.0)
        val s = if (clamped <= 0.0031308) {
            12.92 * clamped
        } else {
            1.055 * clamped.pow(1.0 / 2.4) - 0.055
        }
        return s.toFloat().coerceIn(0f, 1f)
    }

    private fun srgbToOklch(color: Color): Oklch {
        val r = srgbToLinear(color.red)
        val g = srgbToLinear(color.green)
        val b = srgbToLinear(color.blue)

        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        val l_ = cbrt(l)
        val m_ = cbrt(m)
        val s_ = cbrt(s)

        val L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
        val a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
        val bVal = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_

        val c = sqrt(a * a + bVal * bVal)
        val h = ((atan2(bVal, a) * 180.0 / PI) + 360.0) % 360.0
        return Oklch(L.toFloat(), c.toFloat(), h.toFloat())
    }

    private fun isInSrgbGamut(L: Double, a: Double, b: Double): Boolean {
        val (r, g, bLin) = oklabToLinearSrgb(L, a, b)
        val eps = 1e-4
        return r in -eps..(1.0 + eps) && g in -eps..(1.0 + eps) && bLin in -eps..(1.0 + eps)
    }

    private fun oklabToLinearSrgb(L: Double, a: Double, b: Double): Triple<Double, Double, Double> {
        val l_ = L + 0.3963377774 * a + 0.2158037573 * b
        val m_ = L - 0.1055613458 * a - 0.0638541728 * b
        val s_ = L - 0.0894841775 * a - 1.2914855480 * b

        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        val r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bLin = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return Triple(r, g, bLin)
    }

    private fun oklabToSrgb(L: Double, a: Double, b: Double): Color {
        val (r, g, bLin) = oklabToLinearSrgb(L, a, b)
        return Color(
            red = linearToSrgb(r),
            green = linearToSrgb(g),
            blue = linearToSrgb(bLin),
            alpha = 1f,
        )
    }
}
