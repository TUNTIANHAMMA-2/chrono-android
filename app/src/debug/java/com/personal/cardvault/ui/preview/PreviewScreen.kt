package com.personal.cardvault.ui.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.cardvault.ui.components.CardVaultIcons
import com.personal.cardvault.ui.components.ExpiryBadgeType
import com.personal.cardvault.ui.screens.backup.BackupExportScreen
import com.personal.cardvault.ui.screens.backup.BackupRestoreScreen
import com.personal.cardvault.ui.screens.backup.RestoreStage
import com.personal.cardvault.ui.screens.expiry.ExpiryDeleteConfirmScreen
import com.personal.cardvault.ui.screens.expiry.ExpiryEditScreen
import com.personal.cardvault.ui.screens.expiry.ExpiryHomeScreen
import com.personal.cardvault.ui.screens.expiry.ExpiryItemUiModel
import com.personal.cardvault.ui.screens.settings.SettingsScreen
import com.personal.cardvault.ui.screens.vault.CardDetailScreen
import com.personal.cardvault.ui.screens.vault.CardEditScreen
import com.personal.cardvault.ui.screens.vault.CardItemUiModel
import com.personal.cardvault.ui.screens.vault.VaultScreen
import com.personal.cardvault.ui.screens.vault.VaultUiState
import com.personal.cardvault.ui.theme.CardVaultTheme

enum class PreviewTarget(val title: String, val group: String) {
    EXPIRY_HOME_NORMAL("事项首页 (正常分区列表)", "事项"),
    EXPIRY_HOME_EMPTY("事项首页 (空数据引导)", "事项"),
    EXPIRY_HOME_NO_PERMISSION("事项首页 (未授通知权限)", "事项"),
    EXPIRY_EDIT_CREATE("新建事项", "事项"),
    EXPIRY_EDIT_MODIFY("编辑事项 (带异常日期警告)", "事项"),
    EXPIRY_DELETE_EXISTS("删除事项确认页 (正常存在)", "事项"),
    EXPIRY_DELETE_NOT_FOUND("删除事项确认页 (事项已不存在)", "事项"),

    VAULT_NOT_CREATED("保险箱 (还没建库)", "保险箱"),
    VAULT_LOCKED("保险箱 (未解锁状态)", "保险箱"),
    VAULT_UNLOCKED_WITH_CARDS("保险箱 (已解锁 - 卡片列表)", "保险箱"),
    VAULT_UNLOCKED_EMPTY("保险箱 (已解锁 - 空列表)", "保险箱"),
    VAULT_BROKEN("保险箱 (打不开状态)", "保险箱"),
    CARD_DETAIL_MASKED("卡片详情 (默认遮挡状态)", "保险箱"),
    CARD_DETAIL_REVEALED("卡片详情 (明文展开状态)", "保险箱"),
    CARD_EDIT_CREATE("录入新卡片", "保险箱"),
    CARD_EDIT_MODIFY("修改卡片", "保险箱"),

    BACKUP_EXPORT_READY("导出备份 (就绪状态)", "备份与恢复"),
    BACKUP_EXPORT_BROKEN("导出备份 (保险箱打不开分支)", "备份与恢复"),
    BACKUP_RESTORE_INPUT("从备份恢复 (输入口令与报错)", "备份与恢复"),
    BACKUP_RESTORE_CONFIRM("从备份恢复 (覆盖数据二次确认)", "备份与恢复"),

    SETTINGS_NORMAL("设置页 (完整项与版本号)", "设置")
}

/**
 * 调试源码目录预览页（任务 T0.10、阶段 1 检查点）。
 *
 * 供用户在手机（.proto 调试包）上直接查看和交互验证所有页面的假数据版本，
 * 支持动态取色与黑白灰模式实时切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugPreviewHubScreen() {
    var selectedTarget by remember { mutableStateOf<PreviewTarget?>(null) }
    var useDynamicColor by remember { mutableStateOf(false) }

    CardVaultTheme(dynamicColor = useDynamicColor) {
        if (selectedTarget == null) {
            // 预览目录导航索引
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("UI 假数据预览中心", fontWeight = FontWeight.SemiBold) },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                FilterChip(
                                    selected = useDynamicColor,
                                    onClick = { useDynamicColor = !useDynamicColor },
                                    label = { Text(if (useDynamicColor) "莫奈动态色" else "黑白灰纯色") }
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "提示：点击下方条目可直接在手机上全屏体验对应界面的假数据渲染效果与交互形态。右上角可切换配色方案。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val grouped = PreviewTarget.values().groupBy { it.group }
                    grouped.forEach { (group, targets) ->
                        item {
                            Text(
                                text = "【$group】",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(targets) { target ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTarget = target },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = target.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "›",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        } else {
            // 当前选中的全屏预览页（顶部带快捷返回悬浮胶囊）
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTarget!!) {
                    PreviewTarget.EXPIRY_HOME_NORMAL -> {
                        ExpiryHomeScreen(
                            expiredItems = listOf(
                                ExpiryItemUiModel(1, "鲜牛奶", "食品", "2026-11-01", "2026-11-15", -3, ExpiryBadgeType.EXPIRED),
                                ExpiryItemUiModel(2, "酸奶", "食品", null, "2026-11-17", -1, ExpiryBadgeType.EXPIRED)
                            ),
                            dueSoonItems = listOf(
                                ExpiryItemUiModel(3, "服务器续费", "订阅", "2025-11-18", "2026-11-18", 0, ExpiryBadgeType.DUE_TODAY, cycleText = "按年"),
                                ExpiryItemUiModel(4, "宽带费", "生活", "2026-10-20", "2026-11-20", 2, ExpiryBadgeType.DUE_SOON, cycleText = "按月"),
                                ExpiryItemUiModel(5, "车险", "保险", "2025-11-25", "2026-11-25", 7, ExpiryBadgeType.DUE_SOON, cycleText = "按年")
                            ),
                            activeItems = listOf(
                                ExpiryItemUiModel(6, "护照", "证件", "2020-05-10", "2030-05-09", 1267, ExpiryBadgeType.ACTIVE),
                                ExpiryItemUiModel(7, "驾照", "证件", "2022-08-01", "2028-08-01", 621, ExpiryBadgeType.ACTIVE)
                            ),
                            pausedItems = listOf(
                                ExpiryItemUiModel(8, "流媒体会员", "娱乐", "2026-06-01", "2026-12-01", 13, ExpiryBadgeType.PAUSED, isPaused = true, cycleText = "按月")
                            ),
                            hasNotificationPermission = true
                        )
                    }

                    PreviewTarget.EXPIRY_HOME_EMPTY -> {
                        ExpiryHomeScreen(
                            expiredItems = emptyList(),
                            dueSoonItems = emptyList(),
                            activeItems = emptyList(),
                            pausedItems = emptyList(),
                            hasNotificationPermission = true
                        )
                    }

                    PreviewTarget.EXPIRY_HOME_NO_PERMISSION -> {
                        ExpiryHomeScreen(
                            expiredItems = listOf(
                                ExpiryItemUiModel(1, "鲜牛奶", "食品", null, "2026-11-15", -3, ExpiryBadgeType.EXPIRED)
                            ),
                            hasNotificationPermission = false
                        )
                    }

                    PreviewTarget.EXPIRY_EDIT_CREATE -> {
                        ExpiryEditScreen(
                            name = "",
                            validThrough = "2026-12-01",
                            cycle = "NONE",
                            isNewItem = true,
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.EXPIRY_EDIT_MODIFY -> {
                        ExpiryEditScreen(
                            name = "酸奶",
                            category = "食品",
                            startsOn = "2026-11-25",
                            validThrough = "2026-11-20",
                            cycle = "NONE",
                            notes = "常温保存，冷藏口感更佳",
                            isNewItem = false,
                            showDateWarning = true,
                            onDelete = {},
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.EXPIRY_DELETE_EXISTS -> {
                        ExpiryDeleteConfirmScreen(
                            itemExists = true,
                            itemName = "家庭宽带费",
                            validThrough = "2026-11-20",
                            onDismiss = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.EXPIRY_DELETE_NOT_FOUND -> {
                        ExpiryDeleteConfirmScreen(
                            itemExists = false,
                            onDismiss = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.VAULT_NOT_CREATED -> {
                        VaultScreen(
                            state = VaultUiState.NOT_CREATED,
                            hasSecureScreenLock = true
                        )
                    }

                    PreviewTarget.VAULT_LOCKED -> {
                        VaultScreen(
                            state = VaultUiState.LOCKED
                        )
                    }

                    PreviewTarget.VAULT_UNLOCKED_WITH_CARDS -> {
                        VaultScreen(
                            state = VaultUiState.UNLOCKED,
                            cards = listOf(
                                CardItemUiModel(1, "招商银行工资卡", "招商银行", "•••• 4321"),
                                CardItemUiModel(2, "工行海淘信用卡", "中国工商银行", "•••• 8899"),
                                CardItemUiModel(3, "中行全币种信用卡", "中国银行", "•••• 1024")
                            )
                        )
                    }

                    PreviewTarget.VAULT_UNLOCKED_EMPTY -> {
                        VaultScreen(
                            state = VaultUiState.UNLOCKED,
                            cards = emptyList()
                        )
                    }

                    PreviewTarget.VAULT_BROKEN -> {
                        VaultScreen(
                            state = VaultUiState.BROKEN
                        )
                    }

                    PreviewTarget.CARD_DETAIL_MASKED -> {
                        CardDetailScreen(
                            name = "招商银行运通卡",
                            issuer = "招商银行",
                            number = "371234567890123",
                            expMonth = "08",
                            expYear = "28",
                            cvv = "888",
                            notes = "海淘专用，额度 50,000",
                            isNumberMasked = true,
                            isCvvMasked = true,
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.CARD_DETAIL_REVEALED -> {
                        CardDetailScreen(
                            name = "工行工资卡",
                            issuer = "中国工商银行",
                            number = "6212260200001234",
                            expMonth = "12",
                            expYear = "27",
                            cvv = "123",
                            notes = "",
                            isNumberMasked = false,
                            isCvvMasked = false,
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.CARD_EDIT_CREATE -> {
                        CardEditScreen(
                            name = "",
                            issuer = "",
                            isNewCard = true,
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.CARD_EDIT_MODIFY -> {
                        CardEditScreen(
                            name = "招商银行运通卡",
                            issuer = "招商银行",
                            number = "371234567890123",
                            expMonth = "08",
                            expYear = "28",
                            cvv = "888",
                            notes = "海淘专用",
                            isNewCard = false,
                            onDelete = {},
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.BACKUP_EXPORT_READY -> {
                        BackupExportScreen(
                            selectedFileName = "cardvault-20261118-1200.cvb",
                            password = "mySecretPassword123",
                            confirmPassword = "mySecretPassword123",
                            isVaultBroken = false,
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.BACKUP_EXPORT_BROKEN -> {
                        BackupExportScreen(
                            selectedFileName = null,
                            password = "",
                            confirmPassword = "",
                            isVaultBroken = true,
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.BACKUP_RESTORE_INPUT -> {
                        BackupRestoreScreen(
                            stage = RestoreStage.INPUT,
                            selectedFileName = "cardvault-20261118-1200.cvb",
                            password = "wrongPassword",
                            errorMessage = "口令不对或文件已损坏",
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.BACKUP_RESTORE_CONFIRM -> {
                        BackupRestoreScreen(
                            stage = RestoreStage.CONFIRM,
                            selectedFileName = "cardvault-20261118-1200.cvb",
                            itemCount = 8,
                            cardCount = 3,
                            onBack = { selectedTarget = null }
                        )
                    }

                    PreviewTarget.SETTINGS_NORMAL -> {
                        SettingsScreen(
                            earlyReminderEnabled = true,
                            hideNameOnLockScreen = false,
                            themePreset = if (useDynamicColor) com.personal.cardvault.ui.theme.AppThemePreset.MONET else com.personal.cardvault.ui.theme.AppThemePreset.ARCHIVAL_PAPER,
                            notificationPermissionGranted = true,
                            securityLevel = "StrongBox",
                            appVersion = "1.0 (debug.proto)",
                            onThemePresetChange = { useDynamicColor = (it == com.personal.cardvault.ui.theme.AppThemePreset.MONET) }
                        )
                    }
                }

                // 悬浮返回按钮（覆盖于页面右上角，便于退出预览）
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .clickable { selectedTarget = null }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = CardVaultIcons.ArrowBack,
                            contentDescription = "返回目录",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                        Text(
                            text = "返回目录",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
