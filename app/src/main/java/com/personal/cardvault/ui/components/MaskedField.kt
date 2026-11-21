package com.personal.cardvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cardvault.R
import com.personal.cardvault.ui.theme.tnum

/**
 * 敏感字段展示组件（卡号、CVV、有效期）。
 * 支持明文/遮挡切换、单独复制按钮；统一使用等宽数字避免明暗切换时排版跳动。
 */
@Composable
fun SensitiveFieldRow(
    label: String,
    value: String,
    isMasked: Boolean,
    onToggleMask: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    copyEnabled: Boolean = true,
    copyDescription: String = stringResource(R.string.card_copy_number),
    modifier: Modifier = Modifier,
    maskChar: Char = '•'
) {
    val displayValue = when {
        value.isEmpty() -> stringResource(R.string.card_field_empty)
        isMasked -> maskValue(value, maskChar)
        else -> formatPlainValue(value)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayValue,
                style = MaterialTheme.typography.titleMedium.tnum.copy(
                    letterSpacing = 0.8.sp
                ),
                color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onToggleMask != null && value.isNotEmpty()) {
                    IconButton(
                        onClick = onToggleMask,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isMasked) CardVaultIcons.Visibility else CardVaultIcons.VisibilityOff,
                            contentDescription = if (isMasked) "显示明文" else "隐藏明文",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                if (onCopy != null) {
                    IconButton(
                        onClick = onCopy,
                        enabled = copyEnabled && value.isNotEmpty(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = CardVaultIcons.Copy,
                            contentDescription = copyDescription,
                            tint = if (copyEnabled && value.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatPlainValue(raw: String): String {
    val compact = raw.filterNot { it.isWhitespace() }
    return if (compact.length >= 8 && compact.all { it.isDigit() }) {
        compact.chunked(4).joinToString(" ")
    } else {
        raw
    }
}

private fun maskValue(raw: String, maskChar: Char): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.length >= 8) {
        val lastFour = digits.takeLast(4)
        "•••• •••• •••• $lastFour"
    } else {
        String(CharArray(raw.length.coerceAtLeast(3)) { maskChar })
    }
}
