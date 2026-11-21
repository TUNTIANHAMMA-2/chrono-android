package com.personal.cardvault.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.personal.cardvault.ui.components.Preview
import androidx.compose.ui.unit.dp
import com.personal.cardvault.R
import com.personal.cardvault.ui.components.CardVaultBottomBar
import com.personal.cardvault.ui.components.CardVaultIcons
import com.personal.cardvault.ui.components.NavTab
import com.personal.cardvault.ui.theme.AppThemePreset
import com.personal.cardvault.ui.theme.CardVaultTheme
import com.personal.cardvault.ui.theme.ThemeDarkMode
import com.personal.cardvault.ui.theme.tnum

/**
 * 设置页（文档 10.3，任务 T3.7、T4.10、T6.1、T6.3）。
 *
 * 严格按照文档 10.3 表格从上到下排列：
 * 1. 提前 3 天提醒：开关
 * 2. 锁屏隐藏事项名称：开关，默认关
 * 3. 动态取色：开关
 * 4. 通知权限状态：显示已允许或未允许；未允许时给“去设置”按钮
 * 5. 休眠说明：一句说明加“打开应用信息”按钮（见 7.6）
 * 6. 导出备份：进入 9.3 流程
 * 7. 从备份恢复：进入 9.4 流程
 * 8. 密钥保护级别：见 8.9（StrongBox / TEE / 软件 / 保险箱尚未建立）
 * 9. 版本号
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    earlyReminderEnabled: Boolean = true,
    hideNameOnLockScreen: Boolean = false,
    screenshotProtectionEnabled: Boolean = true,
    themePreset: AppThemePreset = AppThemePreset.MONET,
    themeDarkMode: ThemeDarkMode = ThemeDarkMode.SYSTEM,
    amoledDarkEnabled: Boolean = false,
    customSeedArgb: Int = AppThemePreset.DEFAULT_CUSTOM_SEED_ARGB,
    notificationPermissionGranted: Boolean = true,
    securityLevel: String = "StrongBox", // StrongBox / TEE / 软件 / 保险箱尚未建立
    appVersion: String = "1.0",
    cardFaceServerUrl: String = "",
    selectedTab: NavTab = NavTab.SETTINGS,
    onEarlyReminderChange: (Boolean) -> Unit = {},
    onHideNameOnLockScreenChange: (Boolean) -> Unit = {},
    onScreenshotProtectionChange: (Boolean) -> Unit = {},
    onThemePresetChange: (AppThemePreset) -> Unit = {},
    onThemeDarkModeChange: (ThemeDarkMode) -> Unit = {},
    onAmoledDarkChange: (Boolean) -> Unit = {},
    onCustomSeedChange: (Int) -> Unit = {},
    onCardFaceServerUrlChange: ((String) -> Unit)? = null,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {},
    onExportBackupClick: () -> Unit = {},
    onRestoreBackupClick: () -> Unit = {},
    onTabSelected: (NavTab) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    com.personal.cardvault.ui.components.ChronoTopBarLogo()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            CardVaultBottomBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 外观与莫奈主题面板（Mihon 风格）
            Text(
                text = "外观与主题",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            ThemeAppearancePanel(
                selectedPreset = themePreset,
                darkMode = themeDarkMode,
                amoledDarkEnabled = amoledDarkEnabled,
                customSeedArgb = customSeedArgb,
                onPresetSelected = onThemePresetChange,
                onDarkModeSelected = onThemeDarkModeChange,
                onAmoledDarkChange = onAmoledDarkChange,
                onCustomSeedChange = onCustomSeedChange,
            )

            // 提醒开关组卡片
            Text(
                text = "提醒设置",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. 提前 3 天提醒
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Switch) { onEarlyReminderChange(!earlyReminderEnabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_early_reminder),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.settings_early_reminder_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = earlyReminderEnabled,
                            onCheckedChange = onEarlyReminderChange
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 2. 锁屏隐藏事项名称
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Switch) { onHideNameOnLockScreenChange(!hideNameOnLockScreen) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_hide_name_lockscreen),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.settings_hide_name_lockscreen_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hideNameOnLockScreen,
                            onCheckedChange = onHideNameOnLockScreenChange
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 3. 禁止截屏与最近任务遮罩（FLAG_SECURE 实时开关）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Switch) {
                                onScreenshotProtectionChange(!screenshotProtectionEnabled)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "禁止截屏与最近任务预览",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "开启后阻止应用内截屏、录屏，并在系统多任务切换列表中隐藏画面",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = screenshotProtectionEnabled,
                            onCheckedChange = onScreenshotProtectionChange
                        )
                    }
                }
            }

            // 系统权限与运行卡片
            Text(
                text = "权限与后台",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 4. 通知权限状态
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_notification_permission),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (notificationPermissionGranted) {
                                    stringResource(R.string.settings_permission_granted)
                                } else {
                                    stringResource(R.string.settings_permission_denied)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (notificationPermissionGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!notificationPermissionGranted) {
                            OutlinedButton(onClick = onRequestNotificationPermission) {
                                Text(stringResource(R.string.settings_btn_go_settings))
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 5. 休眠说明
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.settings_sleep_explanation_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_sleep_explanation_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenAppInfo) {
                            Text(stringResource(R.string.settings_btn_app_info))
                        }
                    }
                }
            }

            // 备份与恢复卡片
            Text(
                text = "备份与恢复",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 6. 导出备份
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onExportBackupClick)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_backup_export),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = CardVaultIcons.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // 7. 从备份恢复
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onRestoreBackupClick)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_backup_restore),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = CardVaultIcons.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 系统信息卡片
            Text(
                text = "安全与关于",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 8. 密钥保护级别（文档 8.9）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_security_level),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = securityLevel,
                                style = MaterialTheme.typography.labelMedium.tnum,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 9. 版本号
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_version),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = appVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (onCardFaceServerUrlChange != null) {
                        var showUrlDialog by remember { mutableStateOf(false) }
                        var draftUrl by remember(cardFaceServerUrl) { mutableStateOf(cardFaceServerUrl) }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draftUrl = cardFaceServerUrl
                                    showUrlDialog = true
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "在线卡面服务地址",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = cardFaceServerUrl.ifBlank { "未配置（仅使用离线 BIN + 相册选图）" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = CardVaultIcons.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showUrlDialog) {
                            AlertDialog(
                                onDismissRequest = { showUrlDialog = false },
                                title = { Text("在线卡面服务地址") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "填写自建 CardFace 服务地址（例如 https://cardface.example.com），仅用于按发卡行/卡组织匹配卡面，绝不上传卡号。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = draftUrl,
                                            onValueChange = { draftUrl = it },
                                            label = { Text("服务 URL") },
                                            placeholder = { Text("https://...") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            onCardFaceServerUrlChange(draftUrl.trim())
                                            showUrlDialog = false
                                        }
                                    ) {
                                        Text("保存")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showUrlDialog = false }) {
                                        Text("取消")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

// ---- Previews ----

@Preview(name = "设置页 - 正常状态", showBackground = true)
@Composable
fun PreviewSettingsScreen() {
    CardVaultTheme(themePreset = AppThemePreset.MIDNIGHT_DUSK, darkMode = ThemeDarkMode.LIGHT) {
        SettingsScreen(
            earlyReminderEnabled = true,
            hideNameOnLockScreen = false,
            themePreset = AppThemePreset.MIDNIGHT_DUSK,
            themeDarkMode = ThemeDarkMode.LIGHT,
            amoledDarkEnabled = false,
            notificationPermissionGranted = true,
            securityLevel = "StrongBox",
            appVersion = "1.0 (debug.proto)"
        )
    }
}
