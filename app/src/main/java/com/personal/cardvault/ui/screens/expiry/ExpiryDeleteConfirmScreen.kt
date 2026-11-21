package com.personal.cardvault.ui.screens.expiry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.personal.cardvault.ui.components.Preview
import androidx.compose.ui.unit.dp
import com.personal.cardvault.R
import com.personal.cardvault.ui.components.CardVaultIcons
import com.personal.cardvault.ui.theme.CardVaultTheme

/**
 * 事项删除确认页（文档 7.4，来自通知点击“删除”或应用内跳转）。
 *
 * 显示事项当前的名称和到期日，让用户看清删的是什么。
 * 若事项已不存在，显示“这个事项已经不在了”并提供关闭按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryDeleteConfirmScreen(
    itemExists: Boolean = true,
    itemName: String = "",
    validThrough: String = "",
    onConfirmDelete: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.delete_item_confirm_title), fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!itemExists) {
                // 事项已不存在提示（文档 7.4）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = CardVaultIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.item_not_found_message),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = CardVaultIcons.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                text = "确认删除该事项？",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "事项名称：$itemName",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "到期日期：$validThrough",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "删除后此条到期事项将不可恢复，所有关联提醒将自动取消。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = onDismiss) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Button(
                                onClick = onConfirmDelete,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Previews ----

@Preview(name = "删除确认 - 存在事项", showBackground = true)
@Composable
fun PreviewExpiryDeleteConfirmScreenExists() {
    CardVaultTheme(dynamicColor = false) {
        ExpiryDeleteConfirmScreen(
            itemExists = true,
            itemName = "宽带费",
            validThrough = "2026-11-20"
        )
    }
}

@Preview(name = "删除确认 - 事项已不存在", showBackground = true)
@Composable
fun PreviewExpiryDeleteConfirmScreenNotFound() {
    CardVaultTheme(dynamicColor = false) {
        ExpiryDeleteConfirmScreen(
            itemExists = false
        )
    }
}
