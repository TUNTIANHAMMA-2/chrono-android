package com.personal.cardvault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cardvault.ui.theme.tnum
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import kotlin.math.abs

/**
 * 原生 Android Material 3（Material You）分组表单与半屏滚轮选择器组件库。
 * - 全部色彩严格使用 MaterialTheme.colorScheme（完美适配 11 套莫奈/自定义/AMOLED 主题）
 * - 字段采用原生 Material 3 上标小标签 + 左对齐主内容排布，自然顺手、读写清晰
 */

@Composable
fun FormSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
fun GroupedFormCard(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun GroupedInsetDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        thickness = 0.75.dp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
fun GroupedInputRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    tabularNumbers: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val baseStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ).let { if (tabularNumbers) it.tnum else it }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    textStyle = baseStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
fun GroupedPickerRow(
    label: String,
    valueText: String,
    placeholder: String,
    onClick: () -> Unit,
    tabularNumbers: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val isBlank = valueText.isBlank()
            val textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isBlank) FontWeight.Normal else FontWeight.Medium,
            ).let { if (tabularNumbers) it.tnum else it }
            Text(
                text = if (isBlank) placeholder else valueText,
                style = textStyle,
                color = if (isBlank) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "▾",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GroupedNotesRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 128.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 底部半屏双列滚轮选择器（有效月 01–12 | 有效年），供卡片有效期使用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthYearWheelPickerSheet(
    initialMonth: String,
    initialYear: String,
    onDismiss: () -> Unit,
    onConfirm: (month: String, year: String) -> Unit,
) {
    val months = remember { (1..12).map { it.toString().padStart(2, '0') } }
    val years = remember {
        val baseYear = Year.now().value % 100
        (0..15).map { ((baseYear + it) % 100).toString().padStart(2, '0') }
    }
    var selectedMonth by remember {
        mutableStateOf(
            months.firstOrNull { it == initialMonth.padStart(2, '0') }
                ?: LocalDate.now().monthValue.toString().padStart(2, '0'),
        )
    }
    var selectedYear by remember {
        mutableStateOf(
            years.firstOrNull { it == initialYear.takeLast(2).padStart(2, '0') }
                ?: years.getOrElse(3) { years.first() },
        )
    }

    WheelPickerBottomSheet(
        title = "选择有效期",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(selectedMonth, selectedYear) },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelColumn(
                items = months,
                selectedItem = selectedMonth,
                onSelectedChange = { selectedMonth = it },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "/",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            WheelColumn(
                items = years,
                selectedItem = selectedYear,
                onSelectedChange = { selectedYear = it },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

data class QuickDatePreset(
    val label: String,
    val date: LocalDate?,
)

/**
 * 底部半屏三列日期滚轮选择器（年 / 月 / 日），支持快捷日期胶囊，供事项开始日期与到期日期使用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateWheelPickerSheet(
    title: String,
    initialDateIso: String,
    defaultDate: LocalDate = LocalDate.now(),
    quickPresets: List<QuickDatePreset> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (isoDate: String) -> Unit,
) {
    val parsedInitial = remember(initialDateIso, defaultDate) {
        runCatching { LocalDate.parse(initialDateIso) }.getOrDefault(defaultDate)
    }
    val currentYear = remember { Year.now().value }
    val years = remember(currentYear) {
        ((currentYear - 10)..(currentYear + 25)).map { it.toString() }
    }
    val months = remember { (1..12).map { it.toString().padStart(2, '0') } }

    var selectedYear by remember {
        mutableStateOf(
            parsedInitial.year.coerceIn(currentYear - 10, currentYear + 25).toString(),
        )
    }
    var selectedMonth by remember {
        mutableStateOf(parsedInitial.monthValue.toString().padStart(2, '0'))
    }
    val maxDayInMonth = remember(selectedYear, selectedMonth) {
        YearMonth.of(selectedYear.toInt(), selectedMonth.toInt()).lengthOfMonth()
    }
    val days = remember(maxDayInMonth) {
        (1..maxDayInMonth).map { it.toString().padStart(2, '0') }
    }
    var selectedDay by remember {
        mutableStateOf(parsedInitial.dayOfMonth.coerceAtMost(maxDayInMonth).toString().padStart(2, '0'))
    }

    // 切换月份/年份导致最大天数缩小时自动夹紧日期（如 31 -> 28/30）
    LaunchedEffect(maxDayInMonth) {
        val currentDayInt = selectedDay.toIntOrNull() ?: 1
        if (currentDayInt > maxDayInMonth) {
            selectedDay = maxDayInMonth.toString().padStart(2, '0')
        }
    }

    WheelPickerBottomSheet(
        title = title,
        onDismiss = onDismiss,
        onConfirm = {
            val y = selectedYear.toInt()
            val m = selectedMonth.toInt()
            val d = (selectedDay.toIntOrNull() ?: 1).coerceIn(1, YearMonth.of(y, m).lengthOfMonth())
            onConfirm(LocalDate.of(y, m, d).toString())
        },
        quickActions = if (quickPresets.isNotEmpty()) {
            {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(quickPresets) { preset ->
                        Surface(
                            onClick = {
                                if (preset.date == null) {
                                    onConfirm("")
                                } else {
                                    val clampedYear = preset.date.year.coerceIn(currentYear - 10, currentYear + 25)
                                    selectedYear = clampedYear.toString()
                                    selectedMonth = preset.date.monthValue.toString().padStart(2, '0')
                                    selectedDay = preset.date.dayOfMonth.toString().padStart(2, '0')
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelColumn(
                items = years,
                selectedItem = selectedYear,
                onSelectedChange = { selectedYear = it },
                suffix = "年",
                modifier = Modifier.weight(1.25f),
            )
            WheelColumn(
                items = months,
                selectedItem = selectedMonth,
                onSelectedChange = { selectedMonth = it },
                suffix = "月",
                modifier = Modifier.weight(1f),
            )
            WheelColumn(
                items = days,
                selectedItem = selectedDay,
                onSelectedChange = { selectedDay = it },
                suffix = "日",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WheelPickerBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    quickActions: (@Composable () -> Unit)? = null,
    wheelContent: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onConfirm) {
                    Text("确定", fontWeight = FontWeight.SemiBold)
                }
            }

            if (quickActions != null) {
                Spacer(modifier = Modifier.height(6.dp))
                quickActions()
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {}
                wheelContent()
            }
        }
    }
}

@Composable
fun WheelColumn(
    items: List<String>,
    selectedItem: String,
    onSelectedChange: (String) -> Unit,
    suffix: String = "",
    modifier: Modifier = Modifier,
) {
    val itemHeight = 44.dp
    val initialIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centerIndex by remember(items) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > 40) 1 else 0
            idx.coerceIn(0, (items.lastIndex).coerceAtLeast(0))
        }
    }

    LaunchedEffect(centerIndex, items) {
        items.getOrNull(centerIndex)?.let(onSelectedChange)
    }

    // 当快捷按钮从外部改变选中项时，平滑滚动到对应位置
    LaunchedEffect(selectedItem, items) {
        val targetIndex = items.indexOf(selectedItem)
        if (targetIndex >= 0 && targetIndex != centerIndex && !listState.isScrollInProgress) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = itemHeight * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * 5),
    ) {
        items(items.size) { index ->
            val distance = abs(index - centerIndex)
            val isSelected = distance == 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .alpha(
                        when (distance) {
                            0 -> 1f
                            1 -> 0.52f
                            else -> 0.26f
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (suffix.isEmpty()) items[index] else "${items[index]}$suffix",
                    style = if (isSelected) {
                        MaterialTheme.typography.titleLarge.tnum
                    } else {
                        MaterialTheme.typography.titleMedium.tnum
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
