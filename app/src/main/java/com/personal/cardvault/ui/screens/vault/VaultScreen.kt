package com.personal.cardvault.ui.screens.vault

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.personal.cardvault.ui.components.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import com.personal.cardvault.R
import com.personal.cardvault.ui.components.CardFacePreset
import com.personal.cardvault.ui.components.CardFaceSurface
import com.personal.cardvault.ui.components.CardFlipAxis
import com.personal.cardvault.ui.components.CardMiniThumbnail
import com.personal.cardvault.ui.components.CardVaultBottomBar
import com.personal.cardvault.ui.components.CardVaultIcons
import com.personal.cardvault.ui.components.DashedEditorialCard
import com.personal.cardvault.ui.components.FlippableCardFace
import com.personal.cardvault.ui.components.NavTab
import com.personal.cardvault.ui.components.STANDARD_CARD_ASPECT_RATIO
import com.personal.cardvault.ui.components.SensitiveFieldRow
import com.personal.cardvault.ui.components.StudioSpotlightBackground
import com.personal.cardvault.ui.components.cardSpotlightAccentColor
import com.personal.cardvault.ui.theme.tnum
import com.personal.cardvault.ui.theme.CardVaultTheme

data class CardItemUiModel(
    val id: Int,
    val name: String,
    val issuer: String = "",
    val numberPreview: String = "",
    val preset: CardFacePreset = CardFacePreset.entries[id.coerceAtLeast(0) % CardFacePreset.entries.size],
    val artBitmap: ImageBitmap? = null,
    val number: String = "",
    val expMonth: String = "",
    val expYear: String = "",
    val cvv: String = "",
    val notes: String = "",
    val network: String = "",
    val cardType: String = ""
)

enum class VaultCardLayoutMode {
    WALLET_STACK,  // 方案 A：Apple Wallet 舒展叠放卡包
    CAROUSEL,      // 方案 C：横向大卡 + 就地敏感信息工作台
    GOOGLE_WALLET  // Google Wallet：顶部大卡横滑 + 快捷操作胶囊 + 底部全部卡片缩略索引列表
}

enum class VaultUiState {
    NOT_CREATED, // 还没建库
    LOCKED,      // 未解锁
    UNLOCKED,    // 已解锁
    BROKEN       // 保险箱打不开（密钥失效或丢失）
}

/**
 * 卡片保险箱主页面（文档 8.4、8.6、8.8，任务 T4.5、T4.9）。
 *
 * 覆盖四种状态：
 * 1. 还没建库：提示与建库按钮；未设置锁屏时提示先去设置。
 * 2. 未解锁：只显示解锁按钮，绝不显示卡片数量与名称。
 * 3. 已解锁：列表显示卡片名称与卡号后四位，提供锁定和添加按钮。
 * 4. 保险箱打不开：解释原因，提供“从备份恢复”和“重置保险箱”两个出口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    state: VaultUiState = VaultUiState.LOCKED,
    cards: List<CardItemUiModel> = emptyList(),
    layoutMode: VaultCardLayoutMode = VaultCardLayoutMode.WALLET_STACK,
    focusedCardId: Int? = null,
    hasSecureScreenLock: Boolean = true,
    selectedTab: NavTab = NavTab.VAULT,
    onCreateVault: () -> Unit = {},
    onUnlock: () -> Unit = {},
    onLock: () -> Unit = {},
    onAddCard: () -> Unit = {},
    onCardClick: (Int) -> Unit = {},
    onEditCard: ((Int) -> Unit)? = null,
    onDeleteCard: ((Int) -> Unit)? = null,
    onReorderCards: ((List<Int>) -> Unit)? = null,
    onCopyText: ((String) -> Unit)? = null,
    onToggleLayoutMode: (() -> Unit)? = null,
    onRestoreFromBackup: () -> Unit = {},
    onResetVault: () -> Unit = {},
    onTabSelected: (NavTab) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.personal.cardvault.ui.components.ChronoTopBarLogo()
                        if ((state == VaultUiState.UNLOCKED || state == VaultUiState.LOCKED) && cards.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .height(22.dp)
                                    .widthIn(min = 24.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 7.dp)
                                ) {
                                    Text(
                                        text = "${cards.size}",
                                        style = MaterialTheme.typography.labelSmall.tnum.copy(
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (state == VaultUiState.UNLOCKED || (state == VaultUiState.LOCKED && cards.isNotEmpty())) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            if (onToggleLayoutMode != null && cards.isNotEmpty()) {
                                Surface(
                                    onClick = onToggleLayoutMode,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (layoutMode == VaultCardLayoutMode.WALLET_STACK) {
                                                CardVaultIcons.ViewCarousel
                                            } else {
                                                CardVaultIcons.ViewStack
                                            },
                                            contentDescription = if (layoutMode == VaultCardLayoutMode.WALLET_STACK) {
                                                "切换轮播视图"
                                            } else {
                                                "切换叠放视图"
                                            },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            if (state == VaultUiState.UNLOCKED) {
                                Surface(
                                    onClick = onLock,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = CardVaultIcons.Lock,
                                            contentDescription = stringResource(R.string.vault_lock_button),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Surface(
                                onClick = onAddCard,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = CardVaultIcons.Add,
                                        contentDescription = stringResource(R.string.vault_add_card),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state) {
                VaultUiState.NOT_CREATED -> {
                    // 状态 1：还没建库（干净暖米白画布 + 极简建库引导）
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.size(88.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = CardVaultIcons.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.vault_not_created_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.vault_not_created_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        if (!hasSecureScreenLock) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = CardVaultIcons.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.size(12.dp))
                                    Text(
                                        text = stringResource(R.string.vault_no_screen_lock_warning),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = onCreateVault,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 280.dp)
                                    .height(48.dp)
                            ) {
                                Text(stringResource(R.string.vault_create_button))
                            }
                        }
                    }
                }

                VaultUiState.LOCKED,
                VaultUiState.UNLOCKED -> {
                    val isLocked = state == VaultUiState.LOCKED
                    // 关键修复：将 LOCKED（有封面）与 UNLOCKED 合并在同一 Compose 分支中，
                    // 解锁/上锁时仅更新 isLocked 状态而不销毁重建 VaultCarouselContent，
                    // 彻底消除解锁/上锁时的界面闪烁，并完整保留当前卡片位置与卡背翻转状态！
                    if (cards.isNotEmpty()) {
                        when (layoutMode) {
                            VaultCardLayoutMode.WALLET_STACK -> VaultWalletStackContent(
                                cards = cards,
                                onCardClick = onCardClick,
                                onReorderCards = onReorderCards
                            )
                            VaultCardLayoutMode.CAROUSEL -> VaultCarouselContent(
                                cards = cards,
                                initialFocusCardId = focusedCardId,
                                isLocked = isLocked,
                                onUnlockRequest = onUnlock,
                                onCardClick = onCardClick,
                                onEditCard = onEditCard,
                                onDeleteCard = onDeleteCard,
                                onCopyText = onCopyText
                            )
                            VaultCardLayoutMode.GOOGLE_WALLET -> VaultGoogleWalletContent(
                                cards = cards,
                                onCardClick = onCardClick
                            )
                        }
                    } else if (isLocked) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.size(84.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = CardVaultIcons.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = stringResource(R.string.vault_locked_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onUnlock,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 280.dp)
                                    .height(48.dp)
                            ) {
                                Text(stringResource(R.string.vault_unlock_button))
                            }
                        }
                    } else {
                        // 已解锁且暂无卡片：虚线编辑部卡片槽位
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DashedEditorialCard(
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                onClick = onAddCard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(STANDARD_CARD_ASPECT_RATIO)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = CardVaultIcons.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = stringResource(R.string.vault_empty_cards),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.vault_add_card),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                VaultUiState.BROKEN -> {
                    // 状态 4：保险箱打不开（文档 8.8）
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = CardVaultIcons.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                text = stringResource(R.string.vault_broken_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.vault_broken_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // 出口 1：从备份恢复
                        Button(
                            onClick = onRestoreFromBackup,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.vault_broken_restore_btn))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 出口 2：重置保险箱
                        OutlinedButton(
                            onClick = onResetVault,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.vault_broken_reset_btn))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultWalletStackContent(
    cards: List<CardItemUiModel>,
    onCardClick: (Int) -> Unit,
    onReorderCards: ((List<Int>) -> Unit)? = null
) {
    var orderedCards by remember(cards) { mutableStateOf(cards) }
    var draggingCardId by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val latestOnReorder by rememberUpdatedState(onReorderCards)

    val isInfinite = orderedCards.size > 1
    val virtualCount = if (isInfinite) 10_000 else orderedCards.size
    val initialVirtualIndex = remember(orderedCards.size) {
        if (orderedCards.size > 1) {
            val mid = 5_000
            mid - (mid % orderedCards.size)
        } else {
            0
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialVirtualIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center
    )
    val view = LocalView.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPad = 22.dp
        val cardWidth = (maxWidth - horizontalPad * 2).coerceAtLeast(240.dp)
        val cardHeight = cardWidth / STANDARD_CARD_ASPECT_RATIO
        // 紧密 2D 叠放负间距，焦点卡片滑动至视口中央时上下卡片向两端推开以完整展示 100% 高度
        val overlap = cardHeight * 0.44f
        val overlapPx = with(density) { overlap.toPx() }
        val extraGapPx = with(density) { 14.dp.toPx() }
        val stepPx = with(density) { (cardHeight - overlap).toPx().coerceAtLeast(1f) }
        val verticalCenterPad = ((maxHeight - cardHeight) / 2f).coerceAtLeast(20.dp)

        // 实时计算当前最靠近视口中心的连续虚拟焦点位置与焦点索引
        val focusPos by remember(stepPx, virtualCount) {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visible = layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) return@derivedStateOf initialVirtualIndex.toFloat()
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                val closest = visible.minByOrNull { item ->
                    ((item.offset + item.size / 2f) - viewportCenter).absoluteValue
                } ?: return@derivedStateOf initialVirtualIndex.toFloat()
                val itemCenter = closest.offset + closest.size / 2f
                (closest.index + (viewportCenter - itemCenter) / stepPx)
                    .coerceIn(0f, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0).toFloat())
            }
        }
        val activeVirtualIndex by remember {
            derivedStateOf {
                kotlin.math.round(focusPos).toInt().coerceIn(0, (virtualCount - 1).coerceAtLeast(0))
            }
        }

        // 焦点卡片切换时触发轻量级刻度震动反馈（Selection Haptic / Light Impact）
        var lastHapticIndex by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(activeVirtualIndex) {
            if (lastHapticIndex != null && lastHapticIndex != activeVirtualIndex && draggingCardId == null) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            lastHapticIndex = activeVirtualIndex
        }

        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            modifier = Modifier
                .fillMaxSize()
                // 紧凑克制的上下边缘羽化遮罩（仅在最外侧 4.5% 柔化裁切边，避免大面积发白）
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.018f to Color.Black.copy(alpha = 0.55f),
                            0.045f to Color.Black,
                            0.955f to Color.Black,
                            0.982f to Color.Black.copy(alpha = 0.55f),
                            1.00f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            userScrollEnabled = draggingCardId == null,
            contentPadding = PaddingValues(
                start = horizontalPad,
                end = horizontalPad,
                top = verticalCenterPad,
                bottom = verticalCenterPad
            ),
            verticalArrangement = Arrangement.spacedBy(-overlap)
        ) {
            items(count = virtualCount, key = { it }) { virtualIndex ->
                if (orderedCards.isEmpty()) return@items
                val realIndex = virtualIndex % orderedCards.size
                val card = orderedCards[realIndex]
                val isDragging = draggingCardId == card.id && virtualIndex == activeVirtualIndex
                val delta = virtualIndex - focusPos
                val absDelta = delta.absoluteValue
                val focusStrength = (1f - absDelta).coerceIn(0f, 1f)

                // 平滑插值：焦点卡片上下方卡片向两端推开，确保焦点卡片 100% 完整露出不被遮挡
                fun smoothStep(t: Float): Float {
                    val x = t.coerceIn(0f, 1f)
                    return x * x * (3f - 2f * x)
                }
                val pushOffsetY = when {
                    orderedCards.size <= 1 -> 0f
                    delta > 0f -> smoothStep(delta) * (overlapPx + extraGapPx)
                    delta < 0f -> -smoothStep(-delta) * (overlapPx * 0.42f + extraGapPx)
                    else -> 0f
                }

                // 严格 2D 平放：仅做轻微缩放、动态推开与 Z-Index 层级凸显，禁用任何 3D 旋转；
                // 将阴影海拔按 1dp 台阶量化，避免微滚动时每像素重建硬件阴影轮廓
                val cardScale = if (isDragging) 1.04f else lerp(0.965f, 1.02f, focusStrength)
                val cardElevation = if (isDragging) {
                    24.dp
                } else {
                    kotlin.math.round(lerp(8f, 20f, focusStrength)).dp
                }
                val cardZIndex = if (isDragging) {
                    200f
                } else {
                    100f - absDelta * 10f
                }

                val dragModifier = if (onReorderCards != null && orderedCards.size > 1) {
                    Modifier.pointerInput(card.id, virtualIndex, stepPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                draggingCardId = card.id
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                if (draggingCardId == card.id) {
                                    latestOnReorder?.invoke(orderedCards.map { it.id })
                                    draggingCardId = null
                                    dragOffsetY = 0f
                                }
                            },
                            onDragCancel = {
                                draggingCardId = null
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                val currentIdx = orderedCards.indexOfFirst { it.id == card.id }
                                if (currentIdx >= 0) {
                                    if (dragOffsetY > stepPx * 0.65f) {
                                        val nextIdx = (currentIdx + 1) % orderedCards.size
                                        val mutable = orderedCards.toMutableList()
                                        val item = mutable.removeAt(currentIdx)
                                        mutable.add(nextIdx, item)
                                        orderedCards = mutable
                                        dragOffsetY -= stepPx
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    } else if (dragOffsetY < -stepPx * 0.65f) {
                                        val prevIdx = (currentIdx - 1 + orderedCards.size) % orderedCards.size
                                        val mutable = orderedCards.toMutableList()
                                        val item = mutable.removeAt(currentIdx)
                                        mutable.add(prevIdx, item)
                                        orderedCards = mutable
                                        dragOffsetY += stepPx
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }

                // 保持叠放卡片 100% 不透明（避免上下叠放互相透底重影），仅叠加轻柔暗化遮罩凸显焦点
                val dimAlpha = if (isDragging) 0f else (1f - focusStrength) * 0.13f
                val cardShape = com.personal.cardvault.ui.components.StandardCardShape
                val cardInteractionSource = remember(card.id, virtualIndex) { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(cardZIndex)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else pushOffsetY
                            scaleX = cardScale
                            scaleY = cardScale
                        }
                        .then(dragModifier)
                ) {
                    // 无限循环 2D 平放正面展示，滑动吸附焦点完整展开，长按可上下拖动排序（禁用矩形水波纹避免拖动露出直角灰框）
                    CardFaceSurface(
                        name = card.name,
                        issuer = card.issuer,
                        numberPreview = card.numberPreview,
                        preset = card.preset,
                        artBitmap = card.artBitmap,
                        cardNumber = card.number,
                        network = card.network,
                        cardType = card.cardType,
                        elevation = cardElevation,
                        frostedIntensity = 0f,
                        modifier = Modifier.clickable(
                            enabled = draggingCardId == null,
                            interactionSource = cardInteractionSource,
                            indication = null
                        ) { onCardClick(card.id) }
                    )
                    // 柔和暗化遮罩：严格贴合卡片 20dp 圆角，轻微降低非焦点卡片干扰
                    if (dimAlpha > 0.005f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(cardShape)
                                .background(Color.Black.copy(alpha = dimAlpha))
                        )
                    }
                }
            }
        }
    }
}

private enum class CarouselPendingUnlockAction {
    REVEAL_NUMBER,
    REVEAL_CVV,
    COPY_NUMBER,
    COPY_EXPIRY,
    COPY_CVV
}

@Composable
private fun VaultCarouselContent(
    cards: List<CardItemUiModel>,
    initialFocusCardId: Int? = null,
    isLocked: Boolean = false,
    onUnlockRequest: (() -> Unit)? = null,
    onCardClick: (Int) -> Unit,
    onEditCard: ((Int) -> Unit)? = null,
    onDeleteCard: ((Int) -> Unit)? = null,
    onCopyText: ((String) -> Unit)? = null
) {
    val isInfinite = cards.size > 1
    val virtualCount = if (isInfinite) 10_000 else cards.size
    val initialRealIndex = remember(cards, initialFocusCardId) {
        if (initialFocusCardId == null || cards.isEmpty()) 0
        else cards.indexOfFirst { it.id == initialFocusCardId }.coerceAtLeast(0)
    }
    val initialPage = remember(cards.size, initialRealIndex) {
        if (cards.size > 1) {
            val mid = 5_000
            mid - (mid % cards.size) + initialRealIndex
        } else {
            0
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { virtualCount }
    )
    LaunchedEffect(initialFocusCardId, cards.size) {
        if (initialFocusCardId == null || cards.isEmpty()) return@LaunchedEffect
        val targetReal = cards.indexOfFirst { it.id == initialFocusCardId }
        if (targetReal < 0) return@LaunchedEffect
        val currentReal = pagerState.currentPage % cards.size
        if (currentReal != targetReal || pagerState.currentPage >= virtualCount) {
            val base = if (cards.size > 1) {
                val safeCurrent = pagerState.currentPage.coerceIn(0, virtualCount - 1)
                val safeReal = safeCurrent % cards.size
                safeCurrent - safeReal + targetReal
            } else {
                0
            }
            pagerState.scrollToPage(base.coerceIn(0, (virtualCount - 1).coerceAtLeast(0)))
        }
    }
    val realCurrentPage = if (cards.isEmpty()) 0 else pagerState.currentPage % cards.size
    val activeCard = cards.getOrNull(realCurrentPage)

    // 切换卡片自动重置敏感字段遮挡状态，保障隐私安全
    var isNumberMasked by remember(activeCard?.id) { mutableStateOf(true) }
    var isCvvMasked by remember(activeCard?.id) { mutableStateOf(true) }
    var pendingUnlockAction by remember(activeCard?.id) { mutableStateOf<CarouselPendingUnlockAction?>(null) }

    // 上锁时自动重新遮挡明文；解锁完成时立即兑现解锁前点击的查看/复制意图（不再保持隐藏）
    LaunchedEffect(isLocked, activeCard?.id, activeCard?.number, activeCard?.cvv, pendingUnlockAction) {
        if (isLocked) {
            isNumberMasked = true
            isCvvMasked = true
            return@LaunchedEffect
        }
        val action = pendingUnlockAction ?: return@LaunchedEffect
        val current = activeCard ?: return@LaunchedEffect
        pendingUnlockAction = null
        when (action) {
            CarouselPendingUnlockAction.REVEAL_NUMBER -> isNumberMasked = false
            CarouselPendingUnlockAction.REVEAL_CVV -> isCvvMasked = false
            CarouselPendingUnlockAction.COPY_NUMBER -> {
                val copyable = current.number.ifBlank { current.numberPreview }
                if (copyable.isNotBlank()) onCopyText?.invoke(copyable)
            }
            CarouselPendingUnlockAction.COPY_EXPIRY -> {
                if (current.expMonth.isNotBlank() && current.expYear.isNotBlank()) {
                    onCopyText?.invoke("${current.expMonth.padStart(2, '0')}/${current.expYear.takeLast(2)}")
                }
            }
            CarouselPendingUnlockAction.COPY_CVV -> {
                if (current.cvv.isNotBlank()) onCopyText?.invoke(current.cvv)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 无限循环 3D 弧形横滑展廊：
        // - 给每页留足四周 10dp/18dp 阴影与 3D 透视缓冲，彻底消除卡片投影被分页框裁切成直角灰框的问题
        // - ±15° 内纯水平滑动由 HorizontalPager 切卡；其余任意倾角、任意起点滑动均触发 360° 阻尼翻转
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 14.dp),
            pageSpacing = 4.dp,
            beyondViewportPageCount = 1,
            userScrollEnabled = cards.size > 1,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            if (cards.isEmpty()) return@HorizontalPager
            val card = cards[page % cards.size]
            val signedOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val pageOffset = signedOffset.absoluteValue.coerceIn(0f, 1f)
            val scale = lerp(0.88f, 1f, 1f - pageOffset)
            // 关键修复：切卡时不再在含阴影的 graphicsLayer 上设置 alpha < 1f（会触发直角离屏缓冲把阴影裁成 0dp 直角方框），
            // 改为随切卡进度平滑衰减投影 + 在圆角卡面内部叠加柔和暗化，彻底消除切卡时的直角底框！
            val swipeElevation = 12.dp * ((1f - pageOffset).coerceIn(0f, 1f).let { it * it })
            val cardExpiry = if (card.expMonth.isNotBlank() && card.expYear.isNotBlank()) {
                "${card.expMonth.padStart(2, '0')}/${card.expYear.takeLast(2)}"
            } else {
                ""
            }
            val backCvv = if (page == pagerState.currentPage && !isCvvMasked && card.cvv.isNotBlank()) {
                card.cvv
            } else {
                "•••"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 18.dp)
            ) {
                FlippableCardFace(
                    name = card.name,
                    issuer = card.issuer,
                    numberPreview = card.numberPreview,
                    preset = card.preset,
                    artBitmap = card.artBitmap,
                    cardNumber = card.number,
                    network = card.network,
                    cardType = card.cardType,
                    isNumberRevealed = page == pagerState.currentPage && !isNumberMasked,
                    expiryDisplay = cardExpiry,
                    cvvDisplay = backCvv,
                    dimAmount = pageOffset * 0.22f,
                    elevation = swipeElevation,
                    flipAxis = CardFlipAxis.HORIZONTAL,
                    flipEnabled = page == pagerState.currentPage,
                    allowHorizontalSwitch = true,
                    dragToFlip = true,
                    tapToFlip = true,
                    enablePeekHint = page == pagerState.currentPage,
                    peekKey = pagerState.currentPage,
                    // 完整移植原卡片详情页的陀螺仪/重力实时 3D 视差倾斜、镜面扫光、边缘色散与全息虹彩效果
                    enableGyroTilt = page == pagerState.currentPage,
                    modifier = Modifier
                        .graphicsLayer {
                            cameraDistance = 16f * density
                            rotationY = (signedOffset * 24f).coerceIn(-28f, 28f)
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
        }

        // 页码指示器
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            cards.indices.forEach { idx ->
                val selected = idx == realCurrentPage
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (selected) 20.dp else 6.dp, height = 6.dp)
                ) {}
            }
        }

        // 下方就地敏感信息工作台：零重复（不重复写卡名/银行），固定占满剩余高度（切换明文/遮挡零塌陷）
        if (activeCard != null) {
            val expiryDisplay = if (activeCard.expMonth.isNotBlank() && activeCard.expYear.isNotBlank()) {
                "${activeCard.expMonth.padStart(2, '0')}/${activeCard.expYear.takeLast(2)}"
            } else {
                ""
            }
            val numberValue = activeCard.number.ifBlank { activeCard.numberPreview }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    SensitiveFieldRow(
                        label = stringResource(R.string.card_field_number),
                        value = numberValue,
                        isMasked = isLocked || isNumberMasked,
                        onToggleMask = {
                            if (isLocked) {
                                pendingUnlockAction = CarouselPendingUnlockAction.REVEAL_NUMBER
                                onUnlockRequest?.invoke()
                            } else {
                                isNumberMasked = !isNumberMasked
                            }
                        },
                        onCopy = {
                            if (isLocked) {
                                pendingUnlockAction = CarouselPendingUnlockAction.COPY_NUMBER
                                onUnlockRequest?.invoke()
                            } else {
                                val copyable = activeCard.number.ifBlank { numberValue }
                                if (onCopyText != null && copyable.isNotBlank()) onCopyText(copyable) else onCardClick(activeCard.id)
                            }
                        },
                        copyDescription = stringResource(R.string.card_copy_number)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                    SensitiveFieldRow(
                        label = stringResource(R.string.card_field_expiry),
                        value = if (isLocked) "••/••" else expiryDisplay,
                        isMasked = false,
                        onCopy = {
                            if (isLocked) {
                                pendingUnlockAction = CarouselPendingUnlockAction.COPY_EXPIRY
                                onUnlockRequest?.invoke()
                            } else if (onCopyText != null && expiryDisplay.isNotEmpty()) {
                                onCopyText(expiryDisplay)
                            } else {
                                onCardClick(activeCard.id)
                            }
                        },
                        copyEnabled = isLocked || expiryDisplay.isNotEmpty(),
                        copyDescription = stringResource(R.string.card_copy_expiry)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                    SensitiveFieldRow(
                        label = stringResource(R.string.card_field_cvv),
                        value = if (isLocked) "•••" else activeCard.cvv,
                        isMasked = isLocked || isCvvMasked,
                        onToggleMask = {
                            if (isLocked) {
                                pendingUnlockAction = CarouselPendingUnlockAction.REVEAL_CVV
                                onUnlockRequest?.invoke()
                            } else {
                                isCvvMasked = !isCvvMasked
                            }
                        },
                        onCopy = {
                            if (isLocked) {
                                pendingUnlockAction = CarouselPendingUnlockAction.COPY_CVV
                                onUnlockRequest?.invoke()
                            } else if (onCopyText != null && activeCard.cvv.isNotEmpty()) {
                                onCopyText(activeCard.cvv)
                            } else {
                                onCardClick(activeCard.id)
                            }
                        },
                        copyEnabled = isLocked || activeCard.cvv.isNotEmpty(),
                        copyDescription = stringResource(R.string.card_copy_cvv)
                    )

                    if (activeCard.notes.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.card_field_notes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeCard.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { (onEditCard ?: onCardClick)(activeCard.id) },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = CardVaultIcons.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "编辑卡片",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        if (onDeleteCard != null) {
                            OutlinedButton(
                                onClick = { onDeleteCard(activeCard.id) },
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                modifier = Modifier.height(46.dp)
                            ) {
                                Icon(
                                    imageVector = CardVaultIcons.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultGoogleWalletContent(
    cards: List<CardItemUiModel>,
    onCardClick: (Int) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { cards.size })
    val scope = rememberCoroutineScope()
    val activeCard = cards.getOrNull(pagerState.currentPage)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部 Google Wallet 风格横向大卡轮播（右侧自然露出下一张边缘，上下滑翻看背面）
        item(key = "gw_pager") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    pageSpacing = 12.dp,
                    userScrollEnabled = cards.size > 1,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val card = cards[page]
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    val scale = lerp(0.95f, 1f, 1f - pageOffset)
                    val cardExpiry = if (card.expMonth.isNotBlank() && card.expYear.isNotBlank()) {
                        "${card.expMonth.padStart(2, '0')}/${card.expYear.takeLast(2)}"
                    } else {
                        ""
                    }

                    FlippableCardFace(
                        name = card.name,
                        issuer = card.issuer,
                        numberPreview = card.numberPreview,
                        preset = card.preset,
                        artBitmap = card.artBitmap,
                        cardNumber = card.number,
                        expiryDisplay = cardExpiry,
                        cvvDisplay = "•••",
                        elevation = lerp(6f, 16f, 1f - pageOffset).dp,
                        flipAxis = CardFlipAxis.VERTICAL,
                        flipEnabled = page == pagerState.currentPage,
                        onClick = { onCardClick(card.id) },
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                }

                // 极简居中页码指示点
                if (cards.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cards.indices.forEach { idx ->
                            val selected = idx == pagerState.currentPage
                            Surface(
                                shape = CircleShape,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                            ) {}
                        }
                    }
                }
            }
        }

        // 2. Google Wallet 标志性 Material 3 快捷操作胶囊栏
        if (activeCard != null) {
            item(key = "gw_actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GoogleWalletActionChip(
                        icon = CardVaultIcons.Copy,
                        label = "复制卡号",
                        onClick = { onCardClick(activeCard.id) },
                        modifier = Modifier.weight(1f)
                    )
                    GoogleWalletActionChip(
                        icon = CardVaultIcons.Visibility,
                        label = "完整详情",
                        onClick = { onCardClick(activeCard.id) },
                        modifier = Modifier.weight(1f)
                    )
                    GoogleWalletActionChip(
                        icon = CardVaultIcons.Edit,
                        label = "编辑",
                        onClick = { onCardClick(activeCard.id) },
                        modifier = Modifier.weight(0.85f)
                    )
                }
            }
        }

        // 3. Google Wallet 标志性底部圆角独立卡项列表（点击直接切卡/进入详情）
        item(key = "gw_list_header") {
            Text(
                text = "全部卡片",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
        }

        itemsIndexed(cards, key = { _, card -> "gw_row_${card.id}" }) { index, card ->
            val selected = index == pagerState.currentPage
            Surface(
                onClick = {
                    if (selected) {
                        onCardClick(card.id)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardMiniThumbnail(
                        issuer = card.issuer,
                        preset = card.preset,
                        artBitmap = card.artBitmap
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val subtitle = listOfNotNull(
                            card.issuer.takeIf { it.isNotBlank() },
                            card.numberPreview.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.tnum,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (selected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = CardVaultIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleWalletActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

// ---- Previews ----

@Preview(name = "保险箱 - 还没建库", showBackground = true)
@Composable
fun PreviewVaultScreenNotCreated() {
    CardVaultTheme(dynamicColor = false) {
        VaultScreen(
            state = VaultUiState.NOT_CREATED,
            hasSecureScreenLock = true
        )
    }
}

@Preview(name = "保险箱 - 未解锁", showBackground = true)
@Composable
fun PreviewVaultScreenLocked() {
    CardVaultTheme(dynamicColor = false) {
        VaultScreen(
            state = VaultUiState.LOCKED
        )
    }
}

@Preview(name = "保险箱 - 方案A 叠放卡包", showBackground = true)
@Composable
fun PreviewVaultScreenUnlocked() {
    CardVaultTheme(dynamicColor = false) {
        VaultScreen(
            state = VaultUiState.UNLOCKED,
            layoutMode = VaultCardLayoutMode.WALLET_STACK,
            cards = listOf(
                CardItemUiModel(1, "工资卡", "工商银行", "•••• 4321", CardFacePreset.OBSIDIAN),
                CardItemUiModel(2, "海淘信用卡", "招商银行", "•••• 8899", CardFacePreset.TITANIUM),
                CardItemUiModel(3, "外币储蓄卡", "中国银行", "•••• 1024", CardFacePreset.SLATE_GRID),
                CardItemUiModel(4, "日常消费卡", "交通银行", "•••• 5678", CardFacePreset.GRAPHITE_WAVE)
            )
        )
    }
}

@Preview(name = "保险箱 - 方案C 横向轮播", showBackground = true)
@Composable
fun PreviewVaultScreenCarousel() {
    CardVaultTheme(dynamicColor = false) {
        VaultScreen(
            state = VaultUiState.UNLOCKED,
            layoutMode = VaultCardLayoutMode.CAROUSEL,
            cards = listOf(
                CardItemUiModel(
                    id = 1,
                    name = "工资卡",
                    issuer = "工商银行",
                    numberPreview = "•••• 4321",
                    preset = CardFacePreset.OBSIDIAN,
                    number = "6212260200004321",
                    expMonth = "12",
                    expYear = "29",
                    cvv = "432",
                    notes = "每月 15 日发薪账户"
                ),
                CardItemUiModel(
                    id = 2,
                    name = "海淘信用卡",
                    issuer = "招商银行",
                    numberPreview = "•••• 8899",
                    preset = CardFacePreset.TITANIUM,
                    number = "5240123456788899",
                    expMonth = "08",
                    expYear = "28",
                    cvv = "888",
                    notes = "境外免货币转换费"
                ),
                CardItemUiModel(
                    id = 3,
                    name = "外币储蓄卡",
                    issuer = "中国银行",
                    numberPreview = "•••• 1024",
                    preset = CardFacePreset.SLATE_GRID,
                    number = "4563510000001024",
                    expMonth = "05",
                    expYear = "30",
                    cvv = "102"
                )
            )
        )
    }
}

@Preview(name = "保险箱 - Google Wallet 风格", showBackground = true)
@Composable
fun PreviewVaultScreenGoogleWallet() {
    CardVaultTheme(dynamicColor = false) {
        VaultScreen(
            state = VaultUiState.UNLOCKED,
            layoutMode = VaultCardLayoutMode.GOOGLE_WALLET,
            cards = listOf(
                CardItemUiModel(
                    id = 1,
                    name = "工资卡",
                    issuer = "工商银行",
                    numberPreview = "•••• 4321",
                    preset = CardFacePreset.OBSIDIAN,
                    number = "6212260200004321",
                    expMonth = "12",
                    expYear = "29",
                    cvv = "432"
                ),
                CardItemUiModel(
                    id = 2,
                    name = "海淘 MasterCard 信用卡",
                    issuer = "招商银行",
                    numberPreview = "•••• 8899",
                    preset = CardFacePreset.TITANIUM,
                    number = "5240123456788899",
                    expMonth = "08",
                    expYear = "28",
                    cvv = "888"
                ),
                CardItemUiModel(
                    id = 3,
                    name = "留学 Visa 卡",
                    issuer = "中国银行",
                    numberPreview = "•••• 1024",
                    preset = CardFacePreset.SLATE_GRID,
                    number = "4563510000001024",
                    expMonth = "05",
                    expYear = "30",
                    cvv = "102"
                ),
                CardItemUiModel(
                    id = 4,
                    name = "日常储蓄卡",
                    issuer = "交通银行",
                    numberPreview = "•••• 5678",
                    preset = CardFacePreset.GRAPHITE_WAVE,
                    number = "6222600000005678",
                    expMonth = "11",
                    expYear = "28",
                    cvv = "567"
                )
            )
        )
    }
}

@Preview(name = "保险箱 - 打不开状态", showBackground = true)
@Composable
fun PreviewVaultScreenBroken() {
    CardVaultTheme(dynamicColor = false) {
        VaultScreen(
            state = VaultUiState.BROKEN
        )
    }
}
