package com.personal.cardvault.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.personal.cardvault.R

enum class NavTab {
    EXPIRY,
    VAULT,
    SETTINGS
}

/**
 * 底部导航栏组件：应用三大主入口（事项、保险箱、设置）。
 */
@Composable
fun CardVaultBottomBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        NavigationBarItem(
            selected = selectedTab == NavTab.EXPIRY,
            onClick = { onTabSelected(NavTab.EXPIRY) },
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = CardVaultIcons.TabExpiry,
                    contentDescription = stringResource(R.string.nav_tab_expiry)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_tab_expiry),
                    fontWeight = if (selectedTab == NavTab.EXPIRY) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == NavTab.VAULT,
            onClick = { onTabSelected(NavTab.VAULT) },
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = CardVaultIcons.TabWallet,
                    contentDescription = stringResource(R.string.nav_tab_vault)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_tab_vault),
                    fontWeight = if (selectedTab == NavTab.VAULT) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == NavTab.SETTINGS,
            onClick = { onTabSelected(NavTab.SETTINGS) },
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = CardVaultIcons.TabConfig,
                    contentDescription = stringResource(R.string.nav_tab_settings)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_tab_settings),
                    fontWeight = if (selectedTab == NavTab.SETTINGS) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        )
    }
}
