package com.personal.cardvault.ui.screens.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.cardvault.R
import com.personal.cardvault.ui.components.CardFacePreset
import com.personal.cardvault.ui.components.CardFlipAxis
import com.personal.cardvault.ui.components.CardVaultIcons
import com.personal.cardvault.ui.components.FlippableCardFace
import com.personal.cardvault.ui.components.Preview
import com.personal.cardvault.ui.components.SensitiveFieldRow
import com.personal.cardvault.ui.components.StudioSpotlightBackground
import com.personal.cardvault.ui.components.cardSpotlightAccentColor
import com.personal.cardvault.ui.components.rememberCardTiltState
import com.personal.cardvault.ui.theme.CardVaultTheme

/**
 * 卡片详情页（文档 8.6，任务 T4.5、T4.7）。
 *
 * 卡号与 CVV 默认遮挡，点按可切换显示；
 * 卡号、有效期、CVV 具备独立复制按钮（有效期格式 MM/YY，缺失时禁用）；
 * 下方标注剪贴板风险与系统一小时自动清理提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    name: String = "",
    issuer: String = "",
    number: String = "",
    expMonth: String = "",
    expYear: String = "",
    cvv: String = "",
    notes: String = "",
    network: String = "",
    cardType: String = "",
    preset: CardFacePreset = CardFacePreset.OBSIDIAN,
    artBitmap: ImageBitmap? = null,
    isNumberMasked: Boolean = true,
    isCvvMasked: Boolean = true,
    onToggleNumberMask: () -> Unit = {},
    onToggleCvvMask: () -> Unit = {},
    onCopyNumber: () -> Unit = {},
    onCopyExpiry: () -> Unit = {},
    onCopyCvv: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val expiryDisplay = if (expMonth.isNotBlank() && expYear.isNotBlank()) {
        "${expMonth.padStart(2, '0')}/${expYear.takeLast(2)}"
    } else {
        ""
    }
    val hasValidExpiry = expMonth.isNotBlank() && expYear.isNotBlank()
    var isCardFlipped by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.card_detail_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = CardVaultIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = CardVaultIcons.Edit,
                            contentDescription = stringResource(R.string.action_edit)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = CardVaultIcons.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 实体卡面直接悬浮在纯净画布上：统一左右翻转（支持点按或左右滑动）、进入时自动轻微摇摆提示可翻转、顶部留足 3D 翻转空间避免裁切
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                FlippableCardFace(
                    name = name.ifEmpty { stringResource(R.string.card_field_empty) },
                    issuer = issuer,
                    numberPreview = if (number.length >= 4) "•••• ${number.takeLast(4)}" else number,
                    preset = preset,
                    artBitmap = artBitmap,
                    cardNumber = number,
                    network = network,
                    cardType = cardType,
                    isNumberRevealed = !isNumberMasked,
                    expiryDisplay = expiryDisplay,
                    cvvDisplay = if (isCvvMasked) "•••" else cvv,
                    flipAxis = CardFlipAxis.HORIZONTAL,
                    dragToFlip = true,
                    tapToFlip = true,
                    enablePeekHint = true,
                    peekKey = "$name|$number",
                    enableGyroTilt = true,
                    flipped = isCardFlipped,
                    onFlippedChange = { isCardFlipped = it }
                )
            }

            // 敏感信息区
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    // 卡号
                    SensitiveFieldRow(
                        label = stringResource(R.string.card_field_number),
                        value = number,
                        isMasked = isNumberMasked,
                        onToggleMask = onToggleNumberMask,
                        onCopy = onCopyNumber,
                        copyDescription = stringResource(R.string.card_copy_number)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 有效期 (MM/YY)
                    SensitiveFieldRow(
                        label = stringResource(R.string.card_field_expiry),
                        value = expiryDisplay,
                        isMasked = false,
                        onCopy = onCopyExpiry,
                        copyEnabled = hasValidExpiry,
                        copyDescription = stringResource(R.string.card_copy_expiry)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // CVV
                    SensitiveFieldRow(
                        label = stringResource(R.string.card_field_cvv),
                        value = cvv,
                        isMasked = isCvvMasked,
                        onToggleMask = onToggleCvvMask,
                        onCopy = onCopyCvv,
                        copyDescription = stringResource(R.string.card_copy_cvv)
                    )

                    if (notes.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.card_field_notes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ---- Previews ----

@Preview(name = "卡片详情 - 遮挡状态", showBackground = true)
@Composable
fun PreviewCardDetailScreenMasked() {
    CardVaultTheme(dynamicColor = false) {
        CardDetailScreen(
            name = "招行运通卡",
            issuer = "招商银行",
            number = "371234567890123",
            expMonth = "08",
            expYear = "28",
            cvv = "888",
            notes = "海淘专用，额度 50,000",
            isNumberMasked = true,
            isCvvMasked = true
        )
    }
}

@Preview(name = "卡片详情 - 明文展示状态", showBackground = true)
@Composable
fun PreviewCardDetailScreenRevealed() {
    CardVaultTheme(dynamicColor = false) {
        CardDetailScreen(
            name = "工行工资卡",
            issuer = "中国工商银行",
            number = "6212260200001234",
            expMonth = "12",
            expYear = "27",
            cvv = "123",
            notes = "每月 15 日发薪账户，绑定住房公积金代扣与日常水电自动缴费。",
            isNumberMasked = false,
            isCvvMasked = false
        )
    }
}
