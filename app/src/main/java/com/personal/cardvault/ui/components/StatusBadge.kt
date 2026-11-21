package com.personal.cardvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.cardvault.R
import com.personal.cardvault.ui.theme.tnum

enum class ExpiryBadgeType {
    EXPIRED,
    DUE_TODAY,
    DUE_SOON,
    NOT_STARTED,
    ACTIVE,
    PAUSED
}

/**
 * 到期状态徽章标签组件。
 */
@Composable
fun StatusBadge(
    type: ExpiryBadgeType,
    remainingDays: Int = 0,
    modifier: Modifier = Modifier
) {
    val text = when (type) {
        ExpiryBadgeType.EXPIRED -> stringResource(R.string.status_expired)
        ExpiryBadgeType.DUE_TODAY -> stringResource(R.string.status_due_today)
        ExpiryBadgeType.DUE_SOON -> stringResource(R.string.status_due_in_days, remainingDays)
        ExpiryBadgeType.NOT_STARTED -> stringResource(R.string.status_not_started)
        ExpiryBadgeType.ACTIVE -> stringResource(R.string.status_active)
        ExpiryBadgeType.PAUSED -> stringResource(R.string.status_paused)
    }

    val (bgColor, textColor) = when (type) {
        ExpiryBadgeType.EXPIRED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ExpiryBadgeType.DUE_TODAY, ExpiryBadgeType.DUE_SOON -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ExpiryBadgeType.PAUSED -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        ExpiryBadgeType.NOT_STARTED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ExpiryBadgeType.ACTIVE -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall.tnum.copy(fontWeight = FontWeight.Medium)
        )
    }
}
