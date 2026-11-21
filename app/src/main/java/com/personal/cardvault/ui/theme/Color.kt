package com.personal.cardvault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---- 黑白灰中性配色（动态取色关闭时使用） ----
// 深色模式下背景为纯黑（0xFF000000）
// 错误色同样使用中性灰，不带红色；危险操作通过文字与确认框区分（文档 10.1）

val PureBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF000000)
val DarkSurfaceContainerLowest = Color(0xFF0A0A0A)
val DarkSurfaceContainerLow = Color(0xFF121212)
val DarkSurfaceContainer = Color(0xFF171717)
val DarkSurfaceContainerHigh = Color(0xFF1F1F1F)
val DarkSurfaceContainerHighest = Color(0xFF292929)
val DarkSurfaceVariant = Color(0xFF1A1A1A)
val DarkOnBackground = Color(0xFFF5F5F5)
val DarkOnSurface = Color(0xFFF5F5F5)
val DarkOnSurfaceVariant = Color(0xFFA3A3A3)
val DarkPrimary = Color(0xFFFFFFFF)
val DarkOnPrimary = Color(0xFF000000)
val DarkPrimaryContainer = Color(0xFF262626)
val DarkOnPrimaryContainer = Color(0xFFFFFFFF)
val DarkSecondary = Color(0xFFD4D4D4)
val DarkOnSecondary = Color(0xFF000000)
val DarkSecondaryContainer = Color(0xFF1F1F1F)
val DarkOnSecondaryContainer = Color(0xFFE5E5E5)
val DarkOutline = Color(0xFF737373)
val DarkOutlineVariant = Color(0xFF262626)
val DarkError = Color(0xFFE5E5E5)
val DarkOnError = Color(0xFF000000)
val DarkErrorContainer = Color(0xFF262626)
val DarkOnErrorContainer = Color(0xFFF5F5F5)

val MonochromeDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = PureBlack,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

// 浅色暖米白纸张编辑部配色（借鉴 shader.se Archival Paper 质感）
val PureWhite = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFF4F1EA)
val LightSurface = Color(0xFFF4F1EA)
val LightSurfaceContainerLowest = Color(0xFFFAF8F4)
val LightSurfaceContainerLow = Color(0xFFFAF8F4)
val LightSurfaceContainer = Color(0xFFECE8DF)
val LightSurfaceContainerHigh = Color(0xFFE5E0D5)
val LightSurfaceContainerHighest = Color(0xFFD8D2C5)
val LightSurfaceVariant = Color(0xFFEBE7DE)
val LightOnBackground = Color(0xFF171614)
val LightOnSurface = Color(0xFF171614)
val LightOnSurfaceVariant = Color(0xFF57534E)
val LightPrimary = Color(0xFF171614)
val LightOnPrimary = Color(0xFFFAF8F4)
val LightPrimaryContainer = Color(0xFFE5E0D5)
val LightOnPrimaryContainer = Color(0xFF171614)
val LightSecondary = Color(0xFF44403C)
val LightOnSecondary = Color(0xFFFAF8F4)
val LightSecondaryContainer = Color(0xFFE8E4DA)
val LightOnSecondaryContainer = Color(0xFF1C1917)
val LightOutline = Color(0xFF78716C)
val LightOutlineVariant = Color(0xFFDCD6C9)
val LightError = Color(0xFF1C1917)
val LightOnError = Color(0xFFFAF8F4)
val LightErrorContainer = Color(0xFFE5E0D5)
val LightOnErrorContainer = Color(0xFF1C1917)

val MonochromeLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)
