package com.personal.cardvault.ui.screens.expiry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.cardvault.R
import com.personal.cardvault.ui.components.CardVaultBottomBar
import com.personal.cardvault.ui.components.CardVaultIcons
import com.personal.cardvault.ui.components.ExpiryBadgeType
import com.personal.cardvault.ui.components.NavTab
import com.personal.cardvault.ui.components.Preview
import com.personal.cardvault.ui.components.StatusBadge
import com.personal.cardvault.ui.theme.CardVaultTheme
import com.personal.cardvault.ui.theme.tnum
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 事项卡片展示数据模型（纯界面层模型）。
 */
data class ExpiryItemUiModel(
    val id: Int,
    val name: String,
    val category: String = "",
    val startsOn: String? = null,
    val validThrough: String,
    val remainingDays: Int,
    val badgeType: ExpiryBadgeType,
    val isPaused: Boolean = false,
    val cycleText: String = "", // "按月"、"按年"、空为无
)

/**
 * 事项首页分区列表（文档 6.2）。
 *
 * 包含四大分区：已到期、即将到期、进行中、已暂停；
 * 顶部交互语言与「卡片保险箱」保持统一（数量徽标 + 圆形新增按钮），
 * 按「周期性续期事项」与「一次性实物事项」分别呈现符合真实直觉的带文字操作胶囊。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryHomeScreen(
    expiredItems: List<ExpiryItemUiModel> = emptyList(),
    dueSoonItems: List<ExpiryItemUiModel> = emptyList(),
    activeItems: List<ExpiryItemUiModel> = emptyList(),
    pausedItems: List<ExpiryItemUiModel> = emptyList(),
    hasNotificationPermission: Boolean = true,
    selectedTab: NavTab = NavTab.EXPIRY,
    onItemClick: (Int) -> Unit = {},
    onRenewClick: (ExpiryItemUiModel) -> Unit = {},
    onPauseClick: (ExpiryItemUiModel) -> Unit = {},
    onResumeClick: (ExpiryItemUiModel) -> Unit = {},
    onDeleteClick: (ExpiryItemUiModel) -> Unit = {},
    onCreateClick: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onTabSelected: (NavTab) -> Unit = {},
) {
    val totalCount = expiredItems.size + dueSoonItems.size + activeItems.size + pausedItems.size
    val isEmpty = totalCount == 0

    val allCategories = remember(expiredItems, dueSoonItems, activeItems, pausedItems) {
        (expiredItems + dueSoonItems + activeItems + pausedItems)
            .map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    var selectedCategory by remember(allCategories) {
        mutableStateOf<String?>(null)
    }
    val activeCategoryFilter = selectedCategory?.takeIf { it in allCategories }

    fun List<ExpiryItemUiModel>.filterCategory(): List<ExpiryItemUiModel> =
        if (activeCategoryFilter == null) this else filter { it.category.trim() == activeCategoryFilter }

    val visibleExpired = remember(expiredItems, activeCategoryFilter) { expiredItems.filterCategory() }
    val visibleDueSoon = remember(dueSoonItems, activeCategoryFilter) { dueSoonItems.filterCategory() }
    val visibleActive = remember(activeItems, activeCategoryFilter) { activeItems.filterCategory() }
    val visiblePaused = remember(pausedItems, activeCategoryFilter) { pausedItems.filterCategory() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.personal.cardvault.ui.components.ChronoTopBarLogo()
                        if (totalCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .height(22.dp)
                                    .widthIn(min = 24.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 7.dp),
                                ) {
                                    Text(
                                        text = "$totalCount",
                                        style = MaterialTheme.typography.labelSmall.tnum.copy(
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (!isEmpty) {
                        Surface(
                            onClick = onCreateClick,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = CardVaultIcons.Add,
                                    contentDescription = stringResource(R.string.create_item),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            CardVaultBottomBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 权限缺失提示条
            if (!hasNotificationPermission) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "未开启通知权限，无法接收到期提醒",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRequestNotificationPermission) {
                            Text(stringResource(R.string.settings_btn_go_settings))
                        }
                    }
                }
            }

            if (isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = CardVaultIcons.Calendar,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.empty_expiry_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onCreateClick) {
                            Icon(imageVector = CardVaultIcons.Add, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.create_item))
                        }
                    }
                }
            } else {
                // 分类快速筛选胶囊（仅当存在至少 2 个分类时显示，保持简洁）
                if (allCategories.size >= 2) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            CategoryFilterPill(
                                label = "全部",
                                selected = activeCategoryFilter == null,
                                onClick = { selectedCategory = null },
                            )
                        }
                        items(allCategories) { cat ->
                            CategoryFilterPill(
                                label = cat,
                                selected = activeCategoryFilter == cat,
                                onClick = {
                                    selectedCategory = if (activeCategoryFilter == cat) null else cat
                                },
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(2.dp)) }

                    if (visibleExpired.isNotEmpty()) {
                        item {
                            PartitionHeader(title = stringResource(R.string.partition_expired), count = visibleExpired.size)
                        }
                        items(visibleExpired, key = { it.id }) { item ->
                            ExpiryItemCard(
                                item = item,
                                onClick = { onItemClick(item.id) },
                                onRenew = { onRenewClick(item) },
                                onPauseResume = { onPauseClick(item) },
                                onDelete = { onDeleteClick(item) },
                            )
                        }
                    }

                    if (visibleDueSoon.isNotEmpty()) {
                        item {
                            PartitionHeader(title = stringResource(R.string.partition_due_soon), count = visibleDueSoon.size)
                        }
                        items(visibleDueSoon, key = { it.id }) { item ->
                            ExpiryItemCard(
                                item = item,
                                onClick = { onItemClick(item.id) },
                                onRenew = { onRenewClick(item) },
                                onPauseResume = { onPauseClick(item) },
                                onDelete = { onDeleteClick(item) },
                            )
                        }
                    }

                    if (visibleActive.isNotEmpty()) {
                        item {
                            PartitionHeader(title = stringResource(R.string.partition_active), count = visibleActive.size)
                        }
                        items(visibleActive, key = { it.id }) { item ->
                            ExpiryItemCard(
                                item = item,
                                onClick = { onItemClick(item.id) },
                                onRenew = { onRenewClick(item) },
                                onPauseResume = { onPauseClick(item) },
                                onDelete = { onDeleteClick(item) },
                            )
                        }
                    }

                    if (visiblePaused.isNotEmpty()) {
                        item {
                            PartitionHeader(title = stringResource(R.string.partition_paused), count = visiblePaused.size)
                        }
                        items(visiblePaused, key = { it.id }) { item ->
                            ExpiryItemCard(
                                item = item,
                                onClick = { onItemClick(item.id) },
                                onRenew = { onRenewClick(item) },
                                onPauseResume = { onResumeClick(item) },
                                onDelete = { onDeleteClick(item) },
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(28.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.dp else 0.75.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PartitionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelLarge.tnum,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpiryItemCard(
    item: ExpiryItemUiModel,
    onClick: () -> Unit,
    onRenew: () -> Unit,
    onPauseResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecurring = item.cycleText.isNotEmpty()
    val elapsedProgress = remember(item.startsOn, item.validThrough, item.remainingDays, item.isPaused) {
        computeElapsedLifespanRatio(item.startsOn, item.validThrough)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 第一行：名称 + 类别 + 状态徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.category.isNotEmpty()) {
                        Text(
                            text = " · ${item.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                StatusBadge(type = item.badgeType, remainingDays = item.remainingDays)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 第二行：起止日期 + 周期/已过百分比
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dateText = if (item.startsOn != null) {
                    "${item.startsOn} ~ ${item.validThrough}"
                } else {
                    "到期日：${item.validThrough}"
                }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall.tnum,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRecurring) {
                        Text(
                            text = item.cycleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!item.isPaused && elapsedProgress != null) {
                        val pct = (elapsedProgress * 100f).toInt().coerceIn(0, 100)
                        Text(
                            text = if (item.remainingDays < 0) "已过期" else "已过 $pct%",
                            style = MaterialTheme.typography.labelSmall.tnum,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 真实小时级已过进度条（越临近到期进度越满，带语义色区分）
            if (!item.isPaused && elapsedProgress != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    val barColor = when {
                        item.badgeType == ExpiryBadgeType.EXPIRED -> MaterialTheme.colorScheme.error
                        item.remainingDays <= 2 -> Color(0xFFD97706)
                        item.badgeType == ExpiryBadgeType.DUE_SOON -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
                    }
                    val visualFraction = if (item.remainingDays < 0) 1f else elapsedProgress.coerceIn(0.03f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(visualFraction)
                            .clip(CircleShape)
                            .background(barColor),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 极简文字+图标操作行（去掉笨重灰药丸与多余双横线，清爽透气）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.isPaused) {
                    CardInlineAction(
                        icon = CardVaultIcons.Play,
                        label = "恢复提醒",
                        emphasized = true,
                        onClick = onPauseResume,
                    )
                    CardInlineAction(
                        icon = CardVaultIcons.Delete,
                        label = "删除",
                        onClick = onDelete,
                    )
                } else {
                    if (isRecurring) {
                        val renewLabel = when (item.cycleText) {
                            "按月" -> "续期 +1月"
                            "按年" -> "续期 +1年"
                            else -> "续期"
                        }
                        CardInlineAction(
                            icon = CardVaultIcons.Refresh,
                            label = renewLabel,
                            emphasized = true,
                            onClick = onRenew,
                        )
                    }
                    CardInlineAction(
                        icon = CardVaultIcons.Pause,
                        label = "暂停提醒",
                        onClick = onPauseResume,
                    )
                    CardInlineAction(
                        icon = CardVaultIcons.Delete,
                        label = if (isRecurring) "删除" else "已用完",
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardInlineAction(
    icon: ImageVector,
    label: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(14.dp),
            tint = contentColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

/**
 * 精确到分钟的已过有效期比例（0.0 = 刚开始，1.0 = 已到期）。
 * 解决按整天计算导致新建当天所有事项全部显示 100% 满条的问题。
 */
private fun computeElapsedLifespanRatio(
    startsOnIso: String?,
    validThroughIso: String,
): Float? {
    if (startsOnIso.isNullOrBlank()) return null
    val start = runCatching { LocalDate.parse(startsOnIso) }.getOrNull() ?: return null
    val end = runCatching { LocalDate.parse(validThroughIso) }.getOrNull() ?: return null
    val startMoment = start.atStartOfDay()
    val endMoment = end.atTime(23, 59, 59)
    val totalMinutes = ChronoUnit.MINUTES.between(startMoment, endMoment)
    if (totalMinutes <= 0L) return null
    val elapsedMinutes = ChronoUnit.MINUTES.between(startMoment, LocalDateTime.now())
    return (elapsedMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
}

// ---- Previews ----

@Preview(name = "首页 - 假数据预览", showBackground = true)
@Composable
fun PreviewExpiryHomeScreenNormal() {
    CardVaultTheme(dynamicColor = false) {
        ExpiryHomeScreen(
            expiredItems = listOf(
                ExpiryItemUiModel(
                    id = 1,
                    name = "牛奶",
                    category = "食品饮料",
                    startsOn = "2026-11-01",
                    validThrough = "2026-11-15",
                    remainingDays = -3,
                    badgeType = ExpiryBadgeType.EXPIRED,
                    cycleText = "",
                ),
            ),
            dueSoonItems = listOf(
                ExpiryItemUiModel(
                    id = 2,
                    name = "云服务器续费",
                    category = "会员订阅",
                    startsOn = "2025-11-18",
                    validThrough = "2026-11-18",
                    remainingDays = 0,
                    badgeType = ExpiryBadgeType.DUE_TODAY,
                    cycleText = "按年",
                ),
                ExpiryItemUiModel(
                    id = 3,
                    name = "蔬菜叶",
                    category = "食品饮料",
                    startsOn = "2026-11-21",
                    validThrough = "2026-11-22",
                    remainingDays = 1,
                    badgeType = ExpiryBadgeType.DUE_SOON,
                    cycleText = "",
                ),
            ),
            activeItems = listOf(
                ExpiryItemUiModel(
                    id = 4,
                    name = "护照",
                    category = "证件护照",
                    startsOn = "2020-05-10",
                    validThrough = "2030-05-09",
                    remainingDays = 1267,
                    badgeType = ExpiryBadgeType.ACTIVE,
                    cycleText = "",
                ),
            ),
            pausedItems = listOf(
                ExpiryItemUiModel(
                    id = 5,
                    name = "眼药水",
                    category = "药品保健",
                    startsOn = "2026-11-21",
                    validThrough = "2026-12-21",
                    remainingDays = 30,
                    badgeType = ExpiryBadgeType.PAUSED,
                    isPaused = true,
                    cycleText = "按月",
                ),
            ),
            hasNotificationPermission = true,
        )
    }
}

@Preview(name = "首页 - 空数据预览", showBackground = true)
@Composable
fun PreviewExpiryHomeScreenEmpty() {
    CardVaultTheme(dynamicColor = false) {
        ExpiryHomeScreen(
            expiredItems = emptyList(),
            dueSoonItems = emptyList(),
            activeItems = emptyList(),
            pausedItems = emptyList(),
            hasNotificationPermission = false,
        )
    }
}
