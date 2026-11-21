package com.personal.cardvault.ui.screens.settings

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.cardvault.ui.components.CardVaultIcons
import com.personal.cardvault.ui.theme.AppThemePreset
import com.personal.cardvault.ui.theme.ThemeDarkMode
import com.personal.cardvault.ui.theme.resolveCardVaultColorScheme
import com.personal.cardvault.ui.theme.resolveDarkTheme
import com.personal.cardvault.ui.theme.tnum

private val QuickSeedSwatches: List<Int> = listOf(
    0xFFD63E5C.toInt(), // 玫瑰红
    0xFFE26D3D.toInt(), // 珊瑚橘
    0xFFD9941A.toInt(), // 琥珀金
    0xFF5B8C2A.toInt(), // 苔藓绿
    0xFF188140.toInt(), // 翡翠绿
    0xFF00838F.toInt(), // 湖水青
    0xFF0284C7.toInt(), // 晴空蓝
    0xFF3B82F6.toInt(), // 钴蓝
    0xFF4F46E5.toInt(), // 靛蓝
    0xFF7C3AED.toInt(), // 紫罗兰
    0xFFC026D3.toInt(), // 兰花紫
    0xFF64748B.toInt(), // 岩灰蓝
)

/**
 * Mihon / Tachiyomi 风格的莫奈外观与主题配置面板：
 * 1. 深浅色三段切换（跟随系统 / 浅色 / 深色）
 * 2. 横向滑动迷你手机界面预览卡片（系统壁纸莫奈 + 多套精选预设 + 自定义种子）
 * 3. 选择「自定义」时展开的种子色色板 + 色相/饱和度滑块
 * 4. 纯黑深色模式（AMOLED）开关
 */
@Composable
fun ThemeAppearancePanel(
    selectedPreset: AppThemePreset,
    darkMode: ThemeDarkMode,
    amoledDarkEnabled: Boolean,
    customSeedArgb: Int,
    onPresetSelected: (AppThemePreset) -> Unit,
    onDarkModeSelected: (ThemeDarkMode) -> Unit,
    onAmoledDarkChange: (Boolean) -> Unit,
    onCustomSeedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveDark = resolveDarkTheme(darkMode)
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            // 1. 深浅色模式分段选择
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "深色模式",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(10.dp))
                DarkModeSegmentedControl(
                    selectedMode = darkMode,
                    onModeSelected = onDarkModeSelected,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. 主题配色横向预览条（Mihon 风格迷你手机卡片）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "莫奈配色方案",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = selectedPreset.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(AppThemePreset.entries, key = { it.name }) { preset ->
                    val previewScheme = remember(preset, effectiveDark, amoledDarkEnabled, customSeedArgb) {
                        resolveCardVaultColorScheme(
                            context = context,
                            themePreset = preset,
                            darkTheme = effectiveDark,
                            amoledDark = amoledDarkEnabled,
                            customSeedArgb = customSeedArgb,
                        )
                    }
                    ThemeMiniPreviewCard(
                        label = preset.label,
                        scheme = previewScheme,
                        selected = preset == selectedPreset,
                        isCustom = preset == AppThemePreset.CUSTOM_SEED,
                        onClick = { onPresetSelected(preset) },
                    )
                }
            }

            // 3. 自定义种子色面板（仅当选中「自定义」时平滑展开）
            AnimatedVisibility(
                visible = selectedPreset == AppThemePreset.CUSTOM_SEED,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                CustomSeedColorEditor(
                    seedArgb = customSeedArgb,
                    onSeedChange = onCustomSeedChange,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )

            // 4. AMOLED 纯黑深色模式开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onAmoledDarkChange(!amoledDarkEnabled) }
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "纯黑深色模式",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "深色模式下使用纯黑（AMOLED）背景，更省电护眼",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = amoledDarkEnabled,
                    onCheckedChange = onAmoledDarkChange,
                )
            }
        }
    }
}

@Composable
private fun DarkModeSegmentedControl(
    selectedMode: ThemeDarkMode,
    onModeSelected: (ThemeDarkMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ThemeDarkMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                val bgColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
                val textColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(bgColor)
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeMiniPreviewCard(
    label: String,
    scheme: androidx.compose.material3.ColorScheme,
    selected: Boolean,
    isCustom: Boolean,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(14.dp)
    val borderWidth = if (selected) 2.5.dp else 1.dp
    val borderColor = if (selected) scheme.primary else MaterialTheme.colorScheme.outlineVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(92.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .height(132.dp)
                .clip(cardShape)
                .background(scheme.background)
                .border(borderWidth, borderColor, cardShape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 顶部栏缩略
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(scheme.onSurface.copy(alpha = 0.78f)),
                    )
                    if (isCustom && !selected) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color(0xFFFF5252),
                                            Color(0xFFFFD740),
                                            Color(0xFF69F0AE),
                                            Color(0xFF40C4FF),
                                            Color(0xFFE040FB),
                                            Color(0xFFFF5252),
                                        ),
                                    ),
                                ),
                        )
                    }
                }

                // 中间卡片区缩略
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 主卡片
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.surfaceContainerHigh)
                            .padding(6.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(scheme.primary),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(scheme.tertiary),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(scheme.onSurfaceVariant.copy(alpha = 0.45f)),
                            )
                        }
                    }

                    // 次级卡片 + 主色迷你按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.primaryContainer),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(scheme.primary),
                        )
                    }
                }

                // 底部导航栏缩略
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.surfaceContainer)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(scheme.secondaryContainer),
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(scheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(scheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                }
            }

            // 选中对勾角标
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CardVaultIcons.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CustomSeedColorEditor(
    seedArgb: Int,
    onSeedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = remember(seedArgb) {
        FloatArray(3).apply { AndroidColor.colorToHSV(seedArgb, this) }
    }
    val currentHue = hsv[0]
    val currentSat = hsv[1].coerceIn(0.15f, 1f)
    val currentVal = hsv[2].coerceIn(0.55f, 0.92f)
    val seedColor = Color(seedArgb)
    val hexText = remember(seedArgb) {
        String.format("#%06X", seedArgb and 0xFFFFFF)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(seedColor)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text(
                        text = "自定义种子色",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = hexText,
                    style = MaterialTheme.typography.labelMedium.tnum,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 12 枚快捷色块
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickSeedSwatches.chunked(6).forEach { rowSwatches ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        rowSwatches.forEach { argb ->
                            val isSelected = (argb and 0xFFFFFF) == (seedArgb and 0xFFFFFF)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            Color.Black.copy(alpha = 0.12f)
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable { onSeedChange(argb) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = CardVaultIcons.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 色相滑块
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "色相",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${currentHue.toInt()}°",
                        style = MaterialTheme.typography.labelSmall.tnum,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFE53935),
                                    Color(0xFFFDD835),
                                    Color(0xFF43A047),
                                    Color(0xFF00ACC1),
                                    Color(0xFF1E88E5),
                                    Color(0xFF8E24AA),
                                    Color(0xFFE53935),
                                ),
                            ),
                        ),
                )
                Slider(
                    value = currentHue,
                    onValueChange = { newHue ->
                        val nextArgb = AndroidColor.HSVToColor(floatArrayOf(newHue, currentSat, currentVal))
                        onSeedChange(nextArgb)
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = seedColor,
                        activeTrackColor = seedColor,
                    ),
                )
            }

            // 饱和度滑块
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "饱和度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${(currentSat * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.tnum,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val lowSatColor = Color(AndroidColor.HSVToColor(floatArrayOf(currentHue, 0.15f, currentVal)))
                val highSatColor = Color(AndroidColor.HSVToColor(floatArrayOf(currentHue, 1f, currentVal)))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(lowSatColor, highSatColor))),
                )
                Slider(
                    value = currentSat,
                    onValueChange = { newSat ->
                        val nextArgb = AndroidColor.HSVToColor(floatArrayOf(currentHue, newSat, currentVal))
                        onSeedChange(nextArgb)
                    },
                    valueRange = 0.15f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = seedColor,
                        activeTrackColor = seedColor,
                    ),
                )
            }
        }
    }
}
