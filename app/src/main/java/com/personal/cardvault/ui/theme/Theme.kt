package com.personal.cardvault.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 深浅色外观模式。
 */
enum class ThemeDarkMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/**
 * Mihon / Tachiyomi 风格的莫奈主题预设。
 *
 * - [MONET]：跟随系统壁纸动态取色（Android 12+）。
 * - [ARCHIVAL_PAPER]：默认暖纸极简（浅色暖米白 / 深色纯黑黑白灰）。
 * - 其余内置预设：基于 OKLCH 莫奈色阶算法实时生成的独立 Material 3 主题，无需更改系统壁纸。
 * - [CUSTOM_SEED]：用户自选任意种子色实时生成的莫奈主题。
 */
enum class AppThemePreset(
    val label: String,
    val seedColor: Color? = null,
    val tertiaryHueShiftDeg: Float = 60f,
) {
    MONET("跟随壁纸"),
    ARCHIVAL_PAPER("暖纸极简"),
    MIDNIGHT_DUSK("午夜暮蓝", Color(0xFF4C5C92), tertiaryHueShiftDeg = 95f),
    GREEN_APPLE("青苹果", Color(0xFF188140), tertiaryHueShiftDeg = 45f),
    LAVENDER("薰衣草", Color(0xFF7B46AF), tertiaryHueShiftDeg = 55f),
    STRAWBERRY("草莓奶昔", Color(0xFFD63E5C), tertiaryHueShiftDeg = 42f),
    TEAL_TURQUOISE("青绿松石", Color(0xFF00796B), tertiaryHueShiftDeg = 50f),
    TAKO("章鱼紫金", Color(0xFF66577E), tertiaryHueShiftDeg = 135f),
    TIDAL_WAVE("潮汐蓝", Color(0xFF00668B), tertiaryHueShiftDeg = 48f),
    TERRACOTTA("琥珀暖橘", Color(0xFFB5522A), tertiaryHueShiftDeg = 42f),
    CUSTOM_SEED("自定义"),
    ;

    companion object {
        const val DEFAULT_CUSTOM_SEED_ARGB: Int = 0xFF3B82F6.toInt()

        fun fromNameOrDefault(name: String?, fallbackDynamic: Boolean = true): AppThemePreset {
            if (!name.isNullOrBlank()) {
                entries.firstOrNull { it.name == name }?.let { return it }
            }
            return if (fallbackDynamic) MONET else ARCHIVAL_PAPER
        }
    }
}

/**
 * 解析指定主题预设在当前深浅色与 AMOLED 纯黑配置下的真实 Material 3 [ColorScheme]。
 * 同时供 [CardVaultTheme] 与设置页 Mihon 风格迷你预览卡片复用。
 */
fun resolveCardVaultColorScheme(
    context: Context,
    themePreset: AppThemePreset,
    darkTheme: Boolean,
    amoledDark: Boolean = false,
    customSeedArgb: Int = AppThemePreset.DEFAULT_CUSTOM_SEED_ARGB,
): ColorScheme {
    return when (themePreset) {
        AppThemePreset.MONET -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                if (darkTheme && amoledDark) MonetPaletteGenerator.applyAmoledPureBlack(base) else base
            } else {
                if (darkTheme) MonochromeDarkColorScheme else MonochromeLightColorScheme
            }
        }
        AppThemePreset.ARCHIVAL_PAPER -> {
            if (darkTheme) MonochromeDarkColorScheme else MonochromeLightColorScheme
        }
        AppThemePreset.CUSTOM_SEED -> {
            MonetPaletteGenerator.generateColorScheme(
                seedColor = Color(customSeedArgb),
                darkTheme = darkTheme,
                amoledDark = amoledDark,
            )
        }
        else -> {
            val seed = themePreset.seedColor ?: Color(AppThemePreset.DEFAULT_CUSTOM_SEED_ARGB)
            MonetPaletteGenerator.generateColorScheme(
                seedColor = seed,
                darkTheme = darkTheme,
                amoledDark = amoledDark,
                tertiaryHueShiftDeg = themePreset.tertiaryHueShiftDeg,
            )
        }
    }
}

@Composable
fun resolveDarkTheme(darkMode: ThemeDarkMode): Boolean = when (darkMode) {
    ThemeDarkMode.SYSTEM -> isSystemInDarkTheme()
    ThemeDarkMode.LIGHT -> false
    ThemeDarkMode.DARK -> true
}

/**
 * 有期（CardVault）应用主题配置。
 */
@Composable
fun CardVaultTheme(
    themePreset: AppThemePreset = AppThemePreset.MONET,
    darkMode: ThemeDarkMode = ThemeDarkMode.SYSTEM,
    amoledDark: Boolean = false,
    customSeedArgb: Int = AppThemePreset.DEFAULT_CUSTOM_SEED_ARGB,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(darkMode)
    val context = LocalContext.current
    val colorScheme = resolveCardVaultColorScheme(
        context = context,
        themePreset = themePreset,
        darkTheme = darkTheme,
        amoledDark = amoledDark,
        customSeedArgb = customSeedArgb,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}

/**
 * 兼容旧版签名（Preview 及测试使用）。
 */
@Composable
fun CardVaultTheme(
    dynamicColor: Boolean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CardVaultTheme(
        themePreset = if (dynamicColor) AppThemePreset.MONET else AppThemePreset.ARCHIVAL_PAPER,
        darkMode = if (darkTheme) ThemeDarkMode.DARK else ThemeDarkMode.LIGHT,
        amoledDark = false,
        content = content,
    )
}
