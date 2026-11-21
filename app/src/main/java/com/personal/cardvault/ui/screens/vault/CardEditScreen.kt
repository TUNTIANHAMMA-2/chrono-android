package com.personal.cardvault.ui.screens.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.cardvault.R
import com.personal.cardvault.ui.components.CardFacePreset
import com.personal.cardvault.ui.components.CardFlipAxis
import com.personal.cardvault.ui.components.CardVaultIcons
import com.personal.cardvault.ui.components.FlippableCardFace
import com.personal.cardvault.ui.components.FormSectionHeader
import com.personal.cardvault.ui.components.GroupedFormCard
import com.personal.cardvault.ui.components.GroupedInputRow
import com.personal.cardvault.ui.components.GroupedInsetDivider
import com.personal.cardvault.ui.components.GroupedNotesRow
import com.personal.cardvault.ui.components.GroupedPickerRow
import com.personal.cardvault.ui.components.MonthYearWheelPickerSheet
import com.personal.cardvault.ui.components.Preview
import com.personal.cardvault.ui.components.STANDARD_CARD_ASPECT_RATIO
import com.personal.cardvault.ui.theme.CardVaultTheme
import com.personal.cardvault.vault.BinInfo
import com.personal.cardvault.vault.RemoteCardSkin

data class CandidateSkinUiItem(
    val skin: RemoteCardSkin,
    val previewBitmap: ImageBitmap? = null,
)

/**
 * 卡片录入与修改页（极简纯分组表单：顶栏文字保存、零药丸按钮堆砌、「更换卡面」半屏抽屉 + 半屏滚轮有效期选择器）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(
    name: String = "",
    issuer: String = "",
    number: String = "",
    expMonth: String = "",
    expYear: String = "",
    cvv: String = "",
    notes: String = "",
    network: String = "",
    cardType: String = "",
    isNewCard: Boolean = true,
    artBitmap: ImageBitmap? = null,
    detectedBin: BinInfo? = null,
    candidateSkins: List<CandidateSkinUiItem> = emptyList(),
    skinSearchQuery: String = "",
    isSearchingSkins: Boolean = false,
    onNameChange: (String) -> Unit = {},
    onIssuerChange: (String) -> Unit = {},
    onNumberChange: (String) -> Unit = {},
    onExpMonthChange: (String) -> Unit = {},
    onExpYearChange: (String) -> Unit = {},
    onCvvChange: (String) -> Unit = {},
    onNotesChange: (String) -> Unit = {},
    onSkinSearchQueryChange: (String) -> Unit = {},
    onApplyDetectedBin: ((BinInfo) -> Unit)? = null,
    onSearchOnlineArt: (() -> Unit)? = null,
    onSearchOnlineArtWithBin: (() -> Unit)? = null,
    onPickLocalArt: (() -> Unit)? = null,
    onSelectCandidateSkin: ((RemoteCardSkin) -> Unit)? = null,
    onSubmitCommunityArt: (() -> Unit)? = null,
    onClearArt: (() -> Unit)? = null,
    onSave: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    onBack: () -> Unit = {},
) {
    val tailPreview = number.filter { it.isDigit() }.let {
        if (it.length >= 4) "•••• ${it.takeLast(4)}" else ""
    }
    val expiryPreview = if (expMonth.isNotBlank() && expYear.isNotBlank()) {
        "${expMonth.padStart(2, '0')}/${expYear.takeLast(2)}"
    } else {
        ""
    }
    val binDigits = number.filter { it.isDigit() }.take(6)
    val canSave = name.isNotBlank() || issuer.isNotBlank() || number.filter { it.isDigit() }.isNotBlank()
    var showBinConsentDialog by remember { mutableStateOf(false) }
    var showExpiryPickerSheet by remember { mutableStateOf(false) }
    var showCardArtSheet by remember { mutableStateOf(false) }

    val hasArtActions = onSearchOnlineArt != null || onPickLocalArt != null || (artBitmap != null && onClearArt != null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNewCard) {
                            stringResource(R.string.card_edit_new_title)
                        } else {
                            stringResource(R.string.card_edit_modify_title)
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
                    if (!isNewCard && onDelete != null) {
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
                            color = if (canSave) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. 实时可翻转卡面预览 + 原生 Material 3 Tonal「更换卡面」按钮
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlippableCardFace(
                    name = name.ifBlank { stringResource(R.string.card_field_name) },
                    issuer = issuer,
                    numberPreview = tailPreview,
                    preset = CardFacePreset.OBSIDIAN,
                    artBitmap = artBitmap,
                    cardNumber = number,
                    network = network,
                    cardType = cardType,
                    isNumberRevealed = false,
                    expiryDisplay = expiryPreview,
                    cvvDisplay = if (cvv.isNotBlank()) "•••" else "",
                    flipAxis = CardFlipAxis.HORIZONTAL,
                    elevation = 8.dp,
                )

                if (hasArtActions) {
                    FilledTonalButton(
                        onClick = { showCardArtSheet = true },
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = CardVaultIcons.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (artBitmap != null) "更换卡面" else "设置卡面",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            // 2. 分组卡片一：卡片基本信息（原生 Material 3 上标小标签 + 左对齐值）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormSectionHeader("基本信息")
                GroupedFormCard {
                    GroupedInputRow(
                        label = stringResource(R.string.card_field_number),
                        value = number,
                        placeholder = "输入卡号自动离线识别发卡行",
                        onValueChange = onNumberChange,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            autoCorrectEnabled = false,
                        ),
                        tabularNumbers = true,
                    )

                    if (detectedBin != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = buildString {
                                        append("BIN 识别 · ")
                                        append(detectedBin.issuerDisplay.ifBlank { detectedBin.issuerRaw.ifBlank { "未知发卡行" } })
                                        if (detectedBin.brand.isNotBlank()) append(" ${detectedBin.brand}")
                                        if (detectedBin.cardType.isNotBlank()) append(" ${detectedBin.cardType}")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                                if (onApplyDetectedBin != null) {
                                    Text(
                                        text = "应用",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { onApplyDetectedBin(detectedBin) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    GroupedInsetDivider()

                    GroupedInputRow(
                        label = stringResource(R.string.card_field_name),
                        value = name,
                        placeholder = "如：工资卡 / 日常消费卡",
                        onValueChange = onNameChange,
                    )

                    GroupedInsetDivider()

                    GroupedInputRow(
                        label = stringResource(R.string.card_field_issuer),
                        value = issuer,
                        placeholder = "如：招商银行 / 工商银行",
                        onValueChange = onIssuerChange,
                    )
                }
            }

            // 3. 分组卡片二：有效期、安全码与备注
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormSectionHeader("有效期与备注")
                GroupedFormCard {
                    GroupedPickerRow(
                        label = stringResource(R.string.card_field_expiry),
                        valueText = expiryPreview,
                        placeholder = "选择月 / 年",
                        onClick = { showExpiryPickerSheet = true },
                    )

                    GroupedInsetDivider()

                    GroupedInputRow(
                        label = stringResource(R.string.card_field_cvv),
                        value = cvv,
                        placeholder = "3 或 4 位安全码",
                        onValueChange = onCvvChange,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            autoCorrectEnabled = false,
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        tabularNumbers = true,
                    )

                    GroupedInsetDivider()

                    GroupedNotesRow(
                        label = stringResource(R.string.card_field_notes),
                        value = notes,
                        placeholder = "额度、账单日、免年费条件等",
                        onValueChange = onNotesChange,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // 半屏「更换卡面」抽屉：集中收纳在线搜索、BIN 匹配、相册导入、投稿共享与恢复默认
    if (showCardArtSheet) {
        CardArtPickerSheet(
            issuer = issuer,
            binDigits = binDigits,
            artBitmap = artBitmap,
            skinSearchQuery = skinSearchQuery,
            isSearchingSkins = isSearchingSkins,
            candidateSkins = candidateSkins,
            onSkinSearchQueryChange = onSkinSearchQueryChange,
            onSearchOnlineArt = onSearchOnlineArt,
            onRequestBinMatch = if (binDigits.length == 6 && onSearchOnlineArtWithBin != null) {
                { showBinConsentDialog = true }
            } else {
                null
            },
            onSelectCandidateSkin = onSelectCandidateSkin?.let { cb ->
                { skin ->
                    cb(skin)
                    showCardArtSheet = false
                }
            },
            onPickLocalArt = onPickLocalArt?.let { cb ->
                {
                    showCardArtSheet = false
                    cb()
                }
            },
            onSubmitCommunityArt = onSubmitCommunityArt?.let { cb ->
                {
                    showCardArtSheet = false
                    cb()
                }
            },
            onClearArt = onClearArt?.let { cb ->
                {
                    cb()
                    showCardArtSheet = false
                }
            },
            onDismiss = { showCardArtSheet = false },
        )
    }

    // 授权使用前 6 位 BIN 匹配弹窗
    if (showBinConsentDialog && onSearchOnlineArtWithBin != null) {
        AlertDialog(
            onDismissRequest = { showBinConsentDialog = false },
            title = { Text("授权使用前 6 位 BIN 匹配？") },
            text = {
                Text(
                    text = "仅上传卡号前 6 位（$binDigits，公开的发卡行号段）用于精准检索卡面，绝不上传完整卡号、尾号、有效期或 CVV。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBinConsentDialog = false
                        onSearchOnlineArtWithBin()
                    },
                ) {
                    Text("同意并匹配")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBinConsentDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 底部半屏双列滚轮有效期选择器
    if (showExpiryPickerSheet) {
        MonthYearWheelPickerSheet(
            initialMonth = expMonth,
            initialYear = expYear,
            onDismiss = { showExpiryPickerSheet = false },
            onConfirm = { m, y ->
                onExpMonthChange(m)
                onExpYearChange(y)
                showExpiryPickerSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardArtPickerSheet(
    issuer: String,
    binDigits: String,
    artBitmap: ImageBitmap?,
    skinSearchQuery: String,
    isSearchingSkins: Boolean,
    candidateSkins: List<CandidateSkinUiItem>,
    onSkinSearchQueryChange: (String) -> Unit,
    onSearchOnlineArt: (() -> Unit)?,
    onRequestBinMatch: (() -> Unit)?,
    onSelectCandidateSkin: ((RemoteCardSkin) -> Unit)?,
    onPickLocalArt: (() -> Unit)?,
    onSubmitCommunityArt: (() -> Unit)?,
    onClearArt: (() -> Unit)?,
    onDismiss: () -> Unit,
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
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "卡面样式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) {
                    Text("完成", fontWeight = FontWeight.SemiBold)
                }
            }

            // 在线检索卡面分组行
            if (onSearchOnlineArt != null) {
                GroupedFormCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (skinSearchQuery.isEmpty()) {
                                Text(
                                    text = "关键词（如 ${issuer.ifBlank { "初音未来 / 百夫长" }}）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = skinSearchQuery,
                                onValueChange = onSkinSearchQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSearchingSkins) "匹配中..." else "搜索",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(enabled = !isSearchingSkins, onClick = onSearchOnlineArt)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                        if (onRequestBinMatch != null && binDigits.length == 6) {
                            Text(
                                text = "·",
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                            Text(
                                text = "BIN匹配",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable(onClick = onRequestBinMatch)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // 候选卡面横滑区
            if (candidateSkins.isNotEmpty() && onSelectCandidateSkin != null) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(candidateSkins, key = { it.skin.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .width(136.dp)
                                .aspectRatio(STANDARD_CARD_ASPECT_RATIO)
                                .clickable { onSelectCandidateSkin(item.skin) },
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (item.previewBitmap != null) {
                                    Image(
                                        bitmap = item.previewBitmap,
                                        contentDescription = item.skin.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text(
                                        text = item.skin.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 分组操作列表（相册导入 / 投稿共享 / 恢复默认）
            GroupedFormCard {
                var needsDivider = false
                if (onPickLocalArt != null) {
                    SheetActionRow(
                        title = "从相册导入卡面",
                        subtitle = "选择本地高清卡面图片",
                        onClick = onPickLocalArt,
                    )
                    needsDivider = true
                }
                if (artBitmap != null && onSubmitCommunityArt != null) {
                    if (needsDivider) GroupedInsetDivider()
                    SheetActionRow(
                        title = "投稿共享当前卡面",
                        subtitle = "审核通过后可供其他用户匹配使用",
                        onClick = onSubmitCommunityArt,
                    )
                    needsDivider = true
                }
                if (artBitmap != null && onClearArt != null) {
                    if (needsDivider) GroupedInsetDivider()
                    SheetActionRow(
                        title = "恢复默认哑光卡面",
                        subtitle = "移除自定义卡图，使用内置银行与卡组织样式",
                        destructive = true,
                        onClick = onClearArt,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = CardVaultIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ---- Previews ----

@Preview(name = "卡片录入 - 新建卡片", showBackground = true)
@Composable
fun PreviewCardEditScreenCreate() {
    CardVaultTheme(dynamicColor = false) {
        CardEditScreen(
            name = "",
            issuer = "",
            isNewCard = true,
            onSearchOnlineArt = {},
            onPickLocalArt = {},
        )
    }
}

@Preview(name = "卡片录入 - 编辑现有卡片", showBackground = true)
@Composable
fun PreviewCardEditScreenModify() {
    CardVaultTheme(dynamicColor = false) {
        CardEditScreen(
            name = "海淘 MasterCard 信用卡",
            issuer = "招商银行",
            number = "5240113456788899",
            expMonth = "08",
            expYear = "28",
            cvv = "888",
            notes = "海淘专用",
            isNewCard = false,
            detectedBin = BinInfo(
                bin = "524011",
                brand = "MASTERCARD",
                cardType = "CREDIT",
                category = "PLATINUM",
                issuerRaw = "CHINA MERCHANTS BANK",
                issuerDisplay = "招商银行",
                countryCode = "CN",
            ),
            onSearchOnlineArt = {},
            onPickLocalArt = {},
            onDelete = {},
        )
    }
}
