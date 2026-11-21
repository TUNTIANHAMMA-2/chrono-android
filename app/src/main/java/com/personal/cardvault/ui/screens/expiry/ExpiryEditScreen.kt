package com.personal.cardvault.ui.screens.expiry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.cardvault.R
import com.personal.cardvault.ui.components.CardVaultIcons
import com.personal.cardvault.ui.components.DateWheelPickerSheet
import com.personal.cardvault.ui.components.FormSectionHeader
import com.personal.cardvault.ui.components.GroupedFormCard
import com.personal.cardvault.ui.components.GroupedInputRow
import com.personal.cardvault.ui.components.GroupedInsetDivider
import com.personal.cardvault.ui.components.GroupedNotesRow
import com.personal.cardvault.ui.components.GroupedPickerRow
import com.personal.cardvault.ui.components.Preview
import com.personal.cardvault.ui.components.QuickDatePreset
import com.personal.cardvault.ui.theme.CardVaultTheme
import java.time.LocalDate

private val DefaultItemCategories: List<String> = listOf(
    "食品饮料",
    "药品保健",
    "护肤美妆",
    "证件护照",
    "会员订阅",
    "数码保修",
    "保险合约",
    "日用耗材",
    "其他",
)

private enum class ActiveDatePickerTarget {
    STARTS_ON,
    VALID_THROUGH,
}

/**
 * 事项新建与编辑页（极简纯分组表单：顶栏文字保存、零圆角药丸堆砌、统一半屏选择器）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryEditScreen(
    name: String = "",
    category: String = "",
    startsOn: String = "",
    validThrough: String = "",
    cycle: String = "NONE", // "NONE", "MONTHLY", "YEARLY"
    notes: String = "",
    isNewItem: Boolean = true,
    showDateWarning: Boolean = false,
    onNameChange: (String) -> Unit = {},
    onCategoryChange: (String) -> Unit = {},
    onStartsOnChange: (String) -> Unit = {},
    onValidThroughChange: (String) -> Unit = {},
    onCycleChange: (String) -> Unit = {},
    onNotesChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    onBack: () -> Unit = {},
) {
    val canSave = name.isNotBlank() && validThrough.isNotBlank()
    var showCategorySheet by remember { mutableStateOf(false) }
    var showCycleSheet by remember { mutableStateOf(false) }
    var activeDatePicker by remember { mutableStateOf<ActiveDatePickerTarget?>(null) }

    val today = remember { LocalDate.now() }
    val tomorrow = remember(today) { today.plusDays(1) }
    val cycleDisplay = when (cycle) {
        "MONTHLY" -> "按月重复"
        "YEARLY" -> "按年重复"
        else -> "不重复"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNewItem) {
                            stringResource(R.string.title_create_item)
                        } else {
                            stringResource(R.string.title_edit_item)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = CardVaultIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!isNewItem && onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = CardVaultIcons.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(
                        onClick = onSave,
                        enabled = canSave,
                    ) {
                        Text(
                            text = stringResource(R.string.action_save),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showDateWarning) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = CardVaultIcons.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.hint_date_order_anomaly),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // 分组 1：基本信息
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormSectionHeader("基本信息")
                GroupedFormCard {
                    GroupedInputRow(
                        label = "名称 *",
                        value = name,
                        placeholder = "例如：鲜牛奶、护照、AppleCare",
                        onValueChange = onNameChange,
                    )
                    GroupedInsetDivider()
                    GroupedPickerRow(
                        label = "类别",
                        valueText = category,
                        placeholder = "请选择类别",
                        onClick = { showCategorySheet = true },
                        tabularNumbers = false,
                    )
                }
            }

            // 分组 2：日期与周期（全统一分组选择行，无零碎药丸按钮）
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormSectionHeader("日期与周期")
                GroupedFormCard {
                    GroupedPickerRow(
                        label = "开始日期",
                        valueText = formatDateWithRelativeHint(startsOn, today, tomorrow),
                        placeholder = "未设置（点击选择）",
                        onClick = { activeDatePicker = ActiveDatePickerTarget.STARTS_ON },
                    )
                    GroupedInsetDivider()
                    GroupedPickerRow(
                        label = "到期日期 *",
                        valueText = formatDateWithRelativeHint(validThrough, today, tomorrow),
                        placeholder = "请选择到期日期",
                        onClick = { activeDatePicker = ActiveDatePickerTarget.VALID_THROUGH },
                    )
                    GroupedInsetDivider()
                    GroupedPickerRow(
                        label = "重复周期",
                        valueText = cycleDisplay,
                        placeholder = "不重复",
                        onClick = { showCycleSheet = true },
                        tabularNumbers = false,
                    )
                }
            }

            // 分组 3：备注
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormSectionHeader("其他")
                GroupedFormCard {
                    GroupedNotesRow(
                        label = stringResource(R.string.label_item_notes),
                        value = notes,
                        placeholder = "存放位置、续费方式、购买渠道等（选填）",
                        onValueChange = onNotesChange,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showCategorySheet) {
        CategoryPickerSheet(
            currentCategory = category,
            onDismiss = { showCategorySheet = false },
            onConfirm = { picked ->
                onCategoryChange(picked)
                showCategorySheet = false
            },
        )
    }

    if (showCycleSheet) {
        CyclePickerSheet(
            currentCycle = cycle,
            onDismiss = { showCycleSheet = false },
            onSelect = { selected ->
                onCycleChange(selected)
                showCycleSheet = false
            },
        )
    }

    when (activeDatePicker) {
        ActiveDatePickerTarget.STARTS_ON -> {
            DateWheelPickerSheet(
                title = "选择开始日期",
                initialDateIso = startsOn,
                defaultDate = today,
                quickPresets = listOf(
                    QuickDatePreset("今天", today),
                    QuickDatePreset("昨天", today.minusDays(1)),
                    QuickDatePreset("清空", null),
                ),
                onDismiss = { activeDatePicker = null },
                onConfirm = { iso ->
                    onStartsOnChange(iso)
                    activeDatePicker = null
                },
            )
        }
        ActiveDatePickerTarget.VALID_THROUGH -> {
            val baseDate = runCatching { LocalDate.parse(startsOn) }.getOrDefault(today)
            DateWheelPickerSheet(
                title = "选择到期日期",
                initialDateIso = validThrough,
                defaultDate = tomorrow,
                quickPresets = listOf(
                    QuickDatePreset("明天", today.plusDays(1)),
                    QuickDatePreset("+7天", baseDate.plusDays(7)),
                    QuickDatePreset("+1个月", baseDate.plusMonths(1)),
                    QuickDatePreset("+3个月", baseDate.plusMonths(3)),
                    QuickDatePreset("+1年", baseDate.plusYears(1)),
                ),
                onDismiss = { activeDatePicker = null },
                onConfirm = { iso ->
                    onValidThroughChange(iso)
                    activeDatePicker = null
                },
            )
        }
        null -> Unit
    }
}

private fun formatDateWithRelativeHint(
    isoDate: String,
    today: LocalDate,
    tomorrow: LocalDate,
): String {
    if (isoDate.isBlank()) return ""
    val parsed = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
    return when (parsed) {
        today -> "$isoDate（今天）"
        tomorrow -> "$isoDate（明天）"
        else -> isoDate
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CyclePickerSheet(
    currentCycle: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        Triple("NONE", "不重复", "适用于食品、药品、保修等一次性实物"),
        Triple("MONTHLY", "按月重复", "适用于月度订阅、宽带、滤芯等"),
        Triple("YEARLY", "按年重复", "适用于年费会员、保险、年检等"),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "重复周期",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            GroupedFormCard {
                options.forEachIndexed { idx, (key, title, desc) ->
                    val selected = currentCycle == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(key) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = CardVaultIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (idx < options.lastIndex) {
                        GroupedInsetDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerSheet(
    currentCategory: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var customDraft by remember(currentCategory) {
        mutableStateOf(
            if (currentCategory in DefaultItemCategories) "" else currentCategory,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = "选择类别",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { onConfirm(customDraft.trim()) }) {
                    Text(if (customDraft.isBlank()) "清空" else "确定", fontWeight = FontWeight.SemiBold)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DefaultItemCategories.forEach { preset ->
                    val selected = currentCategory == preset
                    Surface(
                        onClick = { onConfirm(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
                        },
                        border = if (selected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                        )
                    }
                }
            }

            GroupedFormCard {
                GroupedInputRow(
                    label = "自定义",
                    value = customDraft,
                    placeholder = "输入其他类别后点右上角确定",
                    onValueChange = { customDraft = it },
                )
            }
        }
    }
}

// ---- Previews ----

@Preview(name = "编辑页 - 新建事项", showBackground = true)
@Composable
fun PreviewExpiryEditScreenCreate() {
    CardVaultTheme(dynamicColor = false) {
        ExpiryEditScreen(
            name = "",
            startsOn = LocalDate.now().toString(),
            validThrough = LocalDate.now().plusDays(1).toString(),
            isNewItem = true,
        )
    }
}

@Preview(name = "编辑页 - 修改事项及日期警告", showBackground = true)
@Composable
fun PreviewExpiryEditScreenModifyWithWarning() {
    CardVaultTheme(dynamicColor = false) {
        ExpiryEditScreen(
            name = "酸奶",
            category = "食品饮料",
            startsOn = "2026-11-25",
            validThrough = "2026-11-20",
            cycle = "NONE",
            notes = "常温保存",
            isNewItem = false,
            showDateWarning = true,
            onDelete = {},
        )
    }
}
