package com.personal.cardvault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import java.util.Collections
import java.util.WeakHashMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cardvault.R
import com.personal.cardvault.ui.theme.CardVaultTheme
import com.personal.cardvault.ui.theme.tnum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin

/** 标准银行卡宽高比（ISO/IEC 7810 ID-1: 85.60mm / 53.98mm ≈ 1.586）。 */
const val STANDARD_CARD_ASPECT_RATIO = 1.586f

/** 标准实体银行卡圆角（短边约 5%，贴近真实 3.18mm 物理倒角）。 */
val StandardCardShape = RoundedCornerShape(percent = 5)

enum class CardFacePreset {
    OBSIDIAN,
    TITANIUM,
    SLATE_GRID,
    GRAPHITE_WAVE
}

enum class CardFlipAxis {
    HORIZONTAL, // 左右沿 Y 轴 360° 翻转（全应用统一）
    VERTICAL    // 保留枚举兼容旧调用
}

/**
 * 卡片手势意图判定结果：
 * - [SWITCH_CARD]：仅在接近纯水平（与水平轴夹角 ≤ 25°）滑动时，交由外层横向轮播切卡；
 * - [FLIP_CARD]：超过 25° 倾角的任意方向滑动（斜滑、竖滑等），均触发 360° 卡片翻转。
 */
enum class CardGestureMode {
    SWITCH_CARD,
    FLIP_CARD
}

/**
 * 根据滑动向量 `(dx, dy)` 与水平轴的夹角判定手势意图：
 * - 与水平方向夹角 ≤ [horizontalConeHalfAngleDeg]（默认 25°）→ [CardGestureMode.SWITCH_CARD]（左右切卡）
 * - 其余任何角度（> 25°）→ [CardGestureMode.FLIP_CARD]（360° 翻转卡片）
 */
fun classifyCardDragGesture(
    dx: Float,
    dy: Float,
    horizontalConeHalfAngleDeg: Float = 25f
): CardGestureMode {
    if (dx == 0f && dy == 0f) return CardGestureMode.FLIP_CARD
    val angleFromHorizontalDeg = Math.toDegrees(
        atan2(abs(dy).toDouble(), abs(dx).toDouble())
    ).toFloat()
    return if (angleFromHorizontalDeg <= horizontalConeHalfAngleDeg) {
        CardGestureMode.SWITCH_CARD
    } else {
        CardGestureMode.FLIP_CARD
    }
}

/**
 * 360° 阻尼吸附计算：结合当前旋转角与松手瞬时角速度，自动吸附到最近的正面（0° / ±360° ...）或背面（±180° / ±540° ...）。
 */
fun computeNextFlipSnapAngle(
    currentAngle: Float,
    angularVelocityDegPerSec: Float
): Float {
    val inertiaProjection = (angularVelocityDegPerSec * 0.11f).coerceIn(-150f, 150f)
    val projected = currentAngle + inertiaProjection
    return round(projected / 180f) * 180f
}

/**
 * 支持 360° 阻尼手势翻转查看正反面的银行卡面组件。
 *
 * - 当 [allowHorizontalSwitch] 为 true（轮播页）：仅 ±15° 内水平滑动交由外层切卡，其余任意倾角、任意起点滑动均流畅 360° 翻转；
 * - 当 [allowHorizontalSwitch] 为 false（详情页）：任意方向滑动或点按均可 360° 阻尼翻转。
 */
@Composable
fun FlippableCardFace(
    name: String,
    issuer: String,
    numberPreview: String,
    preset: CardFacePreset = CardFacePreset.OBSIDIAN,
    artBitmap: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    cardNumber: String = "",
    network: String = "",
    cardType: String = "",
    isNumberRevealed: Boolean = false,
    expiryDisplay: String = "",
    cvvDisplay: String = "",
    dimAmount: Float = 0f,
    elevation: Dp = 14.dp,
    flipAxis: CardFlipAxis = CardFlipAxis.HORIZONTAL,
    flipEnabled: Boolean = true,
    allowHorizontalSwitch: Boolean = false,
    dragToFlip: Boolean = true,
    tapToFlip: Boolean = true,
    enablePeekHint: Boolean = false,
    peekKey: Any? = Unit,
    enableGyroTilt: Boolean = false,
    initialFlipped: Boolean = false,
    flipped: Boolean? = null,
    onFlippedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val flipAnim = remember(initialFlipped) { Animatable(if (flipped ?: initialFlipped) 180f else 0f) }
    val peekAnim = remember { Animatable(0f) }
    val gyroTilt by rememberCardTiltState(enabled = enableGyroTilt)

    fun isBackAngle(angle: Float): Boolean {
        val mod = ((angle % 360f) + 360f) % 360f
        return mod > 90f && mod < 270f
    }

    LaunchedEffect(flipped) {
        if (flipped != null) {
            val currentlyBack = isBackAngle(flipAnim.value)
            if (currentlyBack != flipped) {
                peekAnim.snapTo(0f)
                val baseTurn = round(flipAnim.value / 180f) * 180f
                val target = baseTurn + 180f
                flipAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = 380f
                    )
                )
            }
        }
    }

    // 进入页面或切换至焦点卡片时，优雅地自动轻微摇摆一下再回正（非固定倾斜角），直观暗示可翻转并展示卡片厚度与磨砂反光
    LaunchedEffect(enablePeekHint, peekKey) {
        if (!enablePeekHint || !flipEnabled) {
            peekAnim.snapTo(0f)
            return@LaunchedEffect
        }
        delay(280L)
        val mod = ((flipAnim.value % 180f) + 180f) % 180f
        if (mod < 2f || mod > 178f) {
            peekAnim.animateTo(15f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
            peekAnim.animateTo(-5f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
            peekAnim.animateTo(0f, tween(durationMillis = 340, easing = FastOutSlowInEasing))
        }
    }

    val gestureModifier = if (flipEnabled && (dragToFlip || tapToFlip)) {
        Modifier.pointerInput(flipEnabled, allowHorizontalSwitch, dragToFlip, tapToFlip, onClick) {
            val slopPx = viewConfiguration.touchSlop.coerceAtLeast(16f)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var totalDx = 0f
                var totalDy = 0f
                var lockedMode: CardGestureMode? = null
                var useVerticalDrive = false
                var lastEventTimeMs = down.uptimeMillis
                var angularVelocityDegPerSec = 0f
                var gestureCancelledByOther = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (!change.pressed) {
                        // 手指抬起：判断是点按还是结束 360° 阻尼翻转
                        if (lockedMode == CardGestureMode.FLIP_CARD) {
                            val snapTarget = computeNextFlipSnapAngle(
                                currentAngle = flipAnim.value,
                                angularVelocityDegPerSec = angularVelocityDegPerSec
                            )
                            onFlippedChange?.invoke(isBackAngle(snapTarget))
                            scope.launch {
                                flipAnim.animateTo(
                                    targetValue = snapTarget,
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = 380f
                                    )
                                )
                            }
                        } else if (lockedMode == null && !gestureCancelledByOther && !change.isConsumed) {
                            // 纯点按（未超过滑动阈值）
                            if (onClick != null) {
                                onClick()
                            } else if (tapToFlip) {
                                val nextTarget = round(flipAnim.value / 180f) * 180f + 180f
                                onFlippedChange?.invoke(isBackAngle(nextTarget))
                                scope.launch {
                                    peekAnim.snapTo(0f)
                                    flipAnim.animateTo(
                                        targetValue = nextTarget,
                                        animationSpec = spring(
                                            dampingRatio = 0.68f,
                                            stiffness = 260f
                                        )
                                    )
                                }
                            }
                        }
                        break
                    }

                    if (!change.positionChanged()) continue
                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y

                    if (lockedMode == null) {
                        if (change.isConsumed) {
                            gestureCancelledByOther = true
                            break
                        }
                        val dist = hypot(totalDx, totalDy)
                        if (dist >= slopPx) {
                            val mode = if (allowHorizontalSwitch) {
                                classifyCardDragGesture(
                                    dx = totalDx,
                                    dy = totalDy,
                                    horizontalConeHalfAngleDeg = 25f
                                )
                            } else {
                                CardGestureMode.FLIP_CARD
                            }
                            if (mode == CardGestureMode.SWITCH_CARD || !dragToFlip) {
                                // ±15° 内纯水平滑动：不消费事件，完整交还给外层 HorizontalPager 切卡
                                lockedMode = CardGestureMode.SWITCH_CARD
                                break
                            } else {
                                lockedMode = CardGestureMode.FLIP_CARD
                                // 锁定驱动主轴：纯竖滑/陡斜滑用垂直分量，其余斜滑/横滑用水平分量，保证 360° 连续顺手旋转
                                useVerticalDrive = abs(totalDy) > abs(totalDx) * 1.35f
                                scope.launch { peekAnim.snapTo(0f) }
                                change.consume()
                            }
                        }
                    } else if (lockedMode == CardGestureMode.FLIP_CARD) {
                        change.consume()
                        val rawStep = if (useVerticalDrive) -delta.y else delta.x
                        // 高跟手度 360° 无边界角位移
                        val deltaDeg = rawStep * 0.68f
                        val dtMs = (change.uptimeMillis - lastEventTimeMs).coerceAtLeast(1L)
                        val instantVel = (deltaDeg / dtMs) * 1000f
                        angularVelocityDegPerSec = angularVelocityDegPerSec * 0.6f + instantVel * 0.4f
                        lastEventTimeMs = change.uptimeMillis
                        scope.launch {
                            flipAnim.snapTo(flipAnim.value + deltaDeg)
                        }
                    }
                }
            }
        }
    } else if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    val rawAngle = flipAnim.value + peekAnim.value
    val showingBack = isBackAngle(rawAngle)
    // 翻转过程自带自然扫光与磨砂高光，配合陀螺仪/重力实时视差角驱动卡面高光与全息镭射反光
    val flipGlint = sin(rawAngle * (PI / 180.0)).toFloat()
    val lightX = ((if (enableGyroTilt) gyroTilt.roll else 0f) + flipGlint * 0.82f).coerceIn(-1.2f, 1.2f)
    val lightY = (if (enableGyroTilt) gyroTilt.pitch else 0f).coerceIn(-1f, 1f)
    val dynamicLightingActive = enableGyroTilt || abs(flipGlint) > 0.02f
    val gyroRotY = if (enableGyroTilt) gyroTilt.roll * 14f else 0f
    val gyroRotX = if (enableGyroTilt) -gyroTilt.pitch * 11f else 0f

    val totalRotY = rawAngle + gyroRotY
    val totalRotX = gyroRotX
    val rotYRad = totalRotY * (PI / 180.0)
    val edgeSinY = sin(rotYRad).toFloat()
    val edgeCosY = cos(rotYRad).toFloat()
    val edgeVisibility = abs(edgeSinY).coerceIn(0f, 1f)
    // 真实 3D 物理外法线推导：可见厚度边永远位于靠近镜头的那一侧（符号严格等于 -sign(sin θ · cos θ)）
    val edgeDirection = if (edgeSinY * edgeCosY >= 0f) -1f else 1f
    // 根据卡面本身莫奈色板自动派生同色系深哑光/金属切边色，杜绝违和的刺眼纯白边条
    val edgePalette = remember(artBitmap, preset) {
        artBitmap?.let(::extractMonetBackPalette) ?: presetPalette(preset)
    }
    // 翻转角度越大，硬件投影平滑衰减至 0，彻底消除旋转时背后露出的浅灰色矩形底框
    val flipShadowScale = (1f - abs(flipGlint)).coerceIn(0f, 1f).let { it * it }
    val effectiveElevation = elevation * flipShadowScale

    Box(
        modifier = modifier.then(gestureModifier)
    ) {
        // 真实 ISO ID-1 比例卡片物理厚度层（3.0dp，10 层亚像素连续切边；自动保持与卡面一致的明暗亮度，浅色卡绝不发黑）
        if (edgeVisibility > 0.06f) {
            val layerCount = 10
            val maxThicknessDp = 3.0.dp
            val edgeAlpha = ((edgeVisibility - 0.06f) / 0.14f).coerceIn(0f, 1f)
            val avgLuma = (
                0.2126f * edgePalette.top.red + 0.7152f * edgePalette.top.green + 0.0722f * edgePalette.top.blue +
                    0.2126f * edgePalette.bottom.red + 0.7152f * edgePalette.bottom.green + 0.0722f * edgePalette.bottom.blue
                ) * 0.5f
            val isLightCard = avgLuma > 0.45f

            val edgeTopBase = if (isLightCard) {
                Color(
                    red = (edgePalette.top.red * 0.86f + 0.10f).coerceIn(0f, 1f),
                    green = (edgePalette.top.green * 0.86f + 0.10f).coerceIn(0f, 1f),
                    blue = (edgePalette.top.blue * 0.86f + 0.11f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (edgePalette.top.red * 0.75f + 0.14f).coerceIn(0f, 1f),
                    green = (edgePalette.top.green * 0.75f + 0.14f).coerceIn(0f, 1f),
                    blue = (edgePalette.top.blue * 0.75f + 0.16f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            val edgeBottomBase = if (isLightCard) {
                Color(
                    red = (edgePalette.bottom.red * 0.82f + 0.10f).coerceIn(0f, 1f),
                    green = (edgePalette.bottom.green * 0.82f + 0.10f).coerceIn(0f, 1f),
                    blue = (edgePalette.bottom.blue * 0.82f + 0.11f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (edgePalette.bottom.red * 0.68f + 0.10f).coerceIn(0f, 1f),
                    green = (edgePalette.bottom.green * 0.68f + 0.10f).coerceIn(0f, 1f),
                    blue = (edgePalette.bottom.blue * 0.68f + 0.12f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            val bevelHighlight = Color(
                red = (edgeTopBase.red * 0.55f + 0.42f).coerceIn(0f, 1f),
                green = (edgeTopBase.green * 0.55f + 0.42f).coerceIn(0f, 1f),
                blue = (edgeTopBase.blue * 0.55f + 0.44f).coerceIn(0f, 1f),
                alpha = 1f
            )

            for (layer in layerCount downTo 1) {
                val frac = layer.toFloat() / layerCount.toFloat()
                val depthShade = if (isLightCard) 1f - frac * 0.12f else 1f - frac * 0.22f
                val midSpecular = (1f - abs(frac - 0.35f) * 2.4f).coerceIn(0f, 1f) * 0.18f * edgeVisibility
                fun shade(c: Color): Color = Color(
                    red = (c.red * depthShade + bevelHighlight.red * midSpecular).coerceIn(0f, 1f),
                    green = (c.green * depthShade + bevelHighlight.green * midSpecular).coerceIn(0f, 1f),
                    blue = (c.blue * depthShade + bevelHighlight.blue * midSpecular).coerceIn(0f, 1f),
                    alpha = edgeAlpha
                )
                val layerScale = 1f - frac * 0.002f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(STANDARD_CARD_ASPECT_RATIO)
                        .graphicsLayer {
                            cameraDistance = 16f * density
                            rotationY = totalRotY
                            rotationX = totalRotX
                            scaleX = layerScale
                            scaleY = layerScale
                            translationX = edgeDirection * frac * maxThicknessDp.toPx() * edgeVisibility
                        }
                        .clip(StandardCardShape)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to shade(edgeTopBase),
                                    0.42f to shade(bevelHighlight),
                                    0.78f to shade(edgeBottomBase),
                                    1.00f to shade(edgeBottomBase)
                                )
                            )
                        )
                )
            }
        }

        // 主卡面层（正面 / 背面，360° 翻转时背面自动做 180° 反镜像矫正）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    cameraDistance = 16f * density
                    rotationY = if (showingBack) totalRotY + 180f else totalRotY
                    rotationX = totalRotX
                }
        ) {
            if (!showingBack) {
                CardFaceSurface(
                    name = name,
                    issuer = issuer,
                    numberPreview = numberPreview,
                    preset = preset,
                    artBitmap = artBitmap,
                    cardNumber = cardNumber,
                    network = network,
                    cardType = cardType,
                    dimAmount = dimAmount,
                    elevation = effectiveElevation,
                    tiltX = lightX,
                    tiltY = lightY,
                    enableDynamicLighting = dynamicLightingActive,
                    frostedIntensity = (0.35f + edgeVisibility * 0.65f).coerceIn(0f, 1f)
                )
            } else {
                CardFaceBackSurface(
                    name = name,
                    issuer = issuer,
                    numberPreview = numberPreview,
                    cardNumber = cardNumber,
                    network = network,
                    cardType = cardType,
                    isNumberRevealed = isNumberRevealed,
                    expiryDisplay = expiryDisplay,
                    cvvDisplay = cvvDisplay,
                    preset = preset,
                    artBitmap = artBitmap,
                    dimAmount = dimAmount,
                    elevation = effectiveElevation,
                    tiltX = -lightX,
                    tiltY = lightY,
                    enableDynamicLighting = dynamicLightingActive,
                    frostedIntensity = (0.35f + edgeVisibility * 0.65f).coerceIn(0f, 1f)
                )
            }
        }
    }
}

/**
 * 1.586:1 哑光极简实体卡面（正面）。
 *
 * 摒弃金属拉丝与冗余芯片装饰，突出纯哑光底色、银行徽标、掩码卡号与卡组织标识。
 */
@Composable
fun CardFaceSurface(
    name: String,
    issuer: String,
    numberPreview: String,
    preset: CardFacePreset = CardFacePreset.OBSIDIAN,
    artBitmap: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    compactHeaderOnly: Boolean = false,
    cardNumber: String = "",
    network: String = "",
    cardType: String = "",
    dimAmount: Float = 0f,
    elevation: Dp = 14.dp,
    tiltX: Float = 0f,
    tiltY: Float = 0f,
    enableDynamicLighting: Boolean = false,
    frostedIntensity: Float = 0.28f
) {
    val palette = presetPalette(preset)
    val bankIconRes = resolveBankIconRes(issuer)
    val networkIconRes = resolveCardNetworkRes(name = name, issuer = issuer, cardNumber = cardNumber, network = network)
    val cardKindTag = resolveCardKindTag(name = name, cardNumber = cardNumber, cardType = cardType)
    val tailDisplay = formatCardFrontTail(numberPreview)

    Surface(
        shape = StandardCardShape,
        color = Color.Transparent,
        border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.22f)),
        shadowElevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(STANDARD_CARD_ASPECT_RATIO)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (artBitmap != null) {
                // 当已匹配/上传真实卡面图片（如 aircards.org / cardfacedb.com / AI 生成图）时，
                // 图片本身已包含银行与卡组织元素，直接纯净展示原图，并叠加极细腻的磨砂微颗粒提升实体质感
                Image(
                    bitmap = artBitmap,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize()
                )
                if (frostedIntensity > 0.01f) {
                    CardFilmGrainOverlay(
                        alpha = (0.028f + frostedIntensity * 0.032f).coerceAtMost(0.065f),
                        modifier = Modifier.fillMaxSize()
                    )
                    CardFrostedSheenOverlay(
                        intensity = frostedIntensity,
                        tiltX = tiltX,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (dimAmount > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = dimAmount.coerceIn(0f, 0.55f)))
                    )
                }
            } else {
                // 兜底卡面设计：未获取到卡面图片时展示哑光卡面 + 细腻胶片颗粒 + 银行 SVG + 尾号 + 卡种 + 卡组织标
                CardMatteBackground(palette = palette)
                CardFilmGrainOverlay(alpha = 0.042f, modifier = Modifier.fillMaxSize())

                if (dimAmount > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = dimAmount.coerceIn(0f, 0.55f)))
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 顶行：发卡行紧凑半透明微底托 + 卡名/发卡行 | 右上角纯 4 位尾号
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (bankIconRes != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE5E7EB).copy(alpha = 0.16f),
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(bankIconRes),
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.94f),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (issuer.isNotEmpty()) {
                                    Text(
                                        text = issuer,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.64f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 右上角：纯 4 位尾号（叠放视图中每张卡一眼可区分）
                        if (tailDisplay.isNotEmpty()) {
                            Text(
                                text = tailDisplay,
                                style = MaterialTheme.typography.titleSmall.tnum.copy(
                                    letterSpacing = 1.2.sp
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.90f)
                            )
                        }
                    }

                    if (!compactHeaderOnly) {
                        // 底行：左下 DEBIT / CREDIT 卡种标，右下彩色卡组织标，四角平衡
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (cardKindTag != null) {
                                Text(
                                    text = cardKindTag,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        letterSpacing = 1.4.sp
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.64f)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            if (networkIconRes != null) {
                                Icon(
                                    painter = painterResource(networkIconRes),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(width = 54.dp, height = 35.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = CardVaultIcons.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
            }

            if (enableDynamicLighting || abs(tiltX) > 0.01f || abs(tiltY) > 0.01f) {
                CardSpecularGlareOverlay(
                    tiltX = tiltX,
                    tiltY = tiltY,
                    isCustomArt = artBitmap != null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private enum class CardNetworkKind {
    UNIONPAY,
    MASTERCARD,
    VISA,
    AMEX,
    JCB,
    UNKNOWN
}

/**
 * 1.586:1 真实实体卡背面比例复刻（参考真实招行 MasterCard / 银联卡背排布）：
 * - 顶部：插卡方向微字 + 宽幅哑光磁条
 * - 中部三段式：左侧无框珠光全息标（MasterCard 银白交叠双圆 / 银联柔金全息徽）· 中间一体化白色签名与 CVV 条 · 右侧醒目四弧非接图标
 * - 下半区双栏：左下大字完整/遮挡卡号 + 正下方 VALID/THRU 日期与卡种 · 右下克制发卡行信息与纯白发卡行图标
 */
@Composable
fun CardFaceBackSurface(
    name: String,
    issuer: String,
    numberPreview: String = "",
    cardNumber: String = "",
    network: String = "",
    cardType: String = "",
    isNumberRevealed: Boolean = false,
    expiryDisplay: String = "",
    cvvDisplay: String = "",
    preset: CardFacePreset = CardFacePreset.OBSIDIAN,
    artBitmap: ImageBitmap? = null,
    dimAmount: Float = 0f,
    elevation: Dp = 14.dp,
    tiltX: Float = 0f,
    tiltY: Float = 0f,
    enableDynamicLighting: Boolean = false,
    frostedIntensity: Float = 0.45f,
    modifier: Modifier = Modifier
) {
    // 莫奈取色：若正面有自定义卡面图则自动提取莫奈同色系深哑光色板，否则与正面预设色完全同步
    val palette = remember(artBitmap, preset) {
        artBitmap?.let(::extractMonetBackPalette) ?: presetPalette(preset)
    }
    val fgColor = palette.onSurface
    val bankIconRes = resolveBankIconRes(issuer)
    val networkKind = resolveCardNetworkKind(name = name, issuer = issuer, cardNumber = cardNumber, network = network)
    val cardKindTag = resolveCardKindTag(name = name, cardNumber = cardNumber, cardType = cardType)?.lowercase()
    val backCardNumber = formatCardBackNumber(
        cardNumber = cardNumber,
        numberPreview = numberPreview,
        revealed = isNumberRevealed
    )

    Surface(
        shape = StandardCardShape,
        color = Color.Transparent,
        border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.22f)),
        shadowElevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(STANDARD_CARD_ASPECT_RATIO)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CardMatteBackground(palette = palette)
            CardFilmGrainOverlay(
                alpha = (0.052f + frostedIntensity * 0.030f).coerceAtMost(0.085f),
                modifier = Modifier.fillMaxSize()
            )
            CardFrostedSheenOverlay(
                intensity = frostedIntensity,
                tiltX = tiltX,
                modifier = Modifier.fillMaxSize()
            )

            if (dimAmount > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dimAmount.coerceIn(0f, 0.55f)))
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 顶部：纯净留白 + 真实宽幅哑光磁条（无冗余顶栏文字）
                Box(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(Color(0xFF090A0C))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                }

                // 中部三段式：左无框珠光全息标 | 中一体化签名与 CVV 白色长条 | 右醒目四弧感应图标
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NetworkBackHologram(
                        network = networkKind,
                        tiltX = tiltX,
                        tiltY = tiltY,
                        enableDynamicLighting = enableDynamicLighting,
                        modifier = Modifier.size(width = 50.dp, height = 32.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        // 一体化白色签名条 + 右侧微灰 CVV 区（复刻真实卡背一体化结构）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFF4F4F5)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val step = 8.dp.toPx()
                                    var x = -size.height
                                    while (x < size.width + size.height) {
                                        drawLine(
                                            color = Color(0xFF9CA3AF).copy(alpha = 0.18f),
                                            start = Offset(x, size.height),
                                            end = Offset(x + size.height * 0.65f, 0f),
                                            strokeWidth = 0.9.dp.toPx()
                                        )
                                        x += step
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .fillMaxSize()
                                    .background(Color(0xFFE4E4E7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cvvDisplay.ifBlank { "•••" },
                                    style = MaterialTheme.typography.labelLarge.tnum.copy(
                                        letterSpacing = 1.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF18181B)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "持卡人签名 AUTHORIZED SIGNATURE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 6.5.sp,
                                    letterSpacing = 0.3.sp
                                ),
                                fontWeight = FontWeight.Medium,
                                color = fgColor.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "CVV",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 6.5.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                fontWeight = FontWeight.Medium,
                                color = fgColor.copy(alpha = 0.68f)
                            )
                        }
                    }

                    ContactlessWaveIcon(
                        color = fgColor.copy(alpha = 0.88f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 下半区：左下大字卡号 + 正下方 VALID THRU 与卡种 | 右下发卡行标识
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = backCardNumber,
                            style = MaterialTheme.typography.titleMedium.tnum.copy(
                                letterSpacing = 1.4.sp
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = fgColor.copy(alpha = 0.96f),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "VALID",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 6.sp,
                                            lineHeight = 7.sp,
                                            letterSpacing = 0.4.sp
                                        ),
                                        fontWeight = FontWeight.Medium,
                                        color = fgColor.copy(alpha = 0.68f)
                                    )
                                    Text(
                                        text = "THRU",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 6.sp,
                                            lineHeight = 7.sp,
                                            letterSpacing = 0.4.sp
                                        ),
                                        fontWeight = FontWeight.Medium,
                                        color = fgColor.copy(alpha = 0.68f)
                                    )
                                }
                                Text(
                                    text = expiryDisplay.ifBlank { "••/••" },
                                    style = MaterialTheme.typography.titleSmall.tnum.copy(
                                        letterSpacing = 0.8.sp
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = fgColor.copy(alpha = 0.94f)
                                )
                            }

                            if (cardKindTag != null) {
                                Text(
                                    text = cardKindTag,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        letterSpacing = 0.6.sp
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    color = fgColor.copy(alpha = 0.80f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 右下角：发卡行矢量图标（自适应浅/深底色微底托）
                    if (bankIconRes != null) {
                        Surface(
                            shape = CircleShape,
                            color = fgColor.copy(alpha = 0.14f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(bankIconRes),
                                    contentDescription = null,
                                    tint = fgColor.copy(alpha = 0.94f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (enableDynamicLighting || abs(tiltX) > 0.01f || abs(tiltY) > 0.01f) {
                CardSpecularGlareOverlay(
                    tiltX = tiltX,
                    tiltY = tiltY,
                    isCustomArt = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 实体卡面柔光磨砂（Frosted Matte / Satin）漫反射层：
 * 在翻转或倾斜时呈现细腻宽幅的丝绒漫反射高光与微弱边棱反光，赋予实体磨砂 PVC/金属质感。
 */
@Composable
private fun CardFrostedSheenOverlay(
    intensity: Float,
    tiltX: Float,
    modifier: Modifier = Modifier
) {
    val alpha = intensity.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val shift = tiltX * w * 0.42f
        // 宽幅柔和磨砂漫反射带
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = 0.03f * alpha),
                    0.35f to Color.White.copy(alpha = 0.13f * alpha),
                    0.52f to Color(0xFFF1F5F9).copy(alpha = 0.18f * alpha),
                    0.72f to Color.White.copy(alpha = 0.05f * alpha),
                    1.00f to Color.Black.copy(alpha = 0.06f * alpha)
                ),
                start = Offset(-w * 0.15f + shift, 0f),
                end = Offset(w * 1.15f + shift, h)
            )
        )
        // 顶部柔和磨砂倒角高光细线
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.28f * alpha),
                    Color.White.copy(alpha = 0.06f)
                )
            ),
            start = Offset(0f, 1.dp.toPx()),
            end = Offset(w, 1.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * 随陀螺仪/重力倾斜角度实时追踪的卡面柔光扫光与微光边缘高光（Apple Wallet 风格物理视差光影）。
 */
@Composable
private fun CardSpecularGlareOverlay(
    tiltX: Float,
    tiltY: Float,
    isCustomArt: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val tiltMag = hypot(tiltX, tiltY).coerceIn(0f, 1.2f)

        // 1. 主斜向漫反射扫光带：随 tiltX / tiltY 平移并轻微旋偏
        val cx = w * (0.5f + tiltX * 0.62f)
        val cy = h * (0.5f + tiltY * 0.48f)
        val angleRad = (0.58 + tiltX * 0.28).toFloat() // 约 33° ± 16°
        val halfSpan = maxOf(w, h) * 0.56f
        val dx = cos(angleRad) * halfSpan
        val dy = sin(angleRad) * halfSpan

        val peakAlpha = if (isCustomArt) {
            (0.09f + 0.13f * tiltMag).coerceAtMost(0.22f)
        } else {
            (0.10f + 0.15f * tiltMag).coerceAtMost(0.25f)
        }
        val midAlpha = peakAlpha * 0.35f

        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.34f to Color.White.copy(alpha = midAlpha),
                    0.50f to Color.White.copy(alpha = peakAlpha),
                    0.66f to Color.White.copy(alpha = midAlpha),
                    1.00f to Color.Transparent
                ),
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy)
            )
        )

        // 2. 卡片边缘金属/哑光切角追光（高光点随入射角沿边框滑动）
        val rimStart = Offset(w * (0.5f - tiltX * 0.55f), h * (0.5f - tiltY * 0.55f))
        val rimEnd = Offset(w * (0.5f + tiltX * 0.55f), h * (0.5f + tiltY * 0.55f))
        val rimAlpha = (0.14f + 0.22f * tiltMag).coerceAtMost(0.36f)
        val cornerPx = 16.dp.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = rimAlpha),
                    Color.White.copy(alpha = 0.04f),
                    Color.White.copy(alpha = rimAlpha * 0.55f)
                ),
                start = rimStart,
                end = rimEnd
            ),
            cornerRadius = CornerRadius(cornerPx, cornerPx),
            style = Stroke(width = 1.1.dp.toPx())
        )

        // 3. 借鉴 shader.se 光学镜头的极轻微边缘色散（仅在大倾角晃动时浮现，克制不花哨）
        if (tiltMag > 0.16f) {
            val chromaAlpha = ((tiltMag - 0.16f) * 0.26f).coerceAtMost(0.22f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = chromaAlpha),
                        Color.Transparent,
                        Color(0xFFF472B6).copy(alpha = chromaAlpha)
                    ),
                    start = rimStart,
                    end = rimEnd
                ),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 1.6.dp.toPx())
            )
        }
    }
}

/**
 * 卡组织背面无框珠光全息防伪标（内置陀螺仪/翻转实时镭射虹彩追光）：
 * - MasterCard：真实卡片同款无框银白珠光交叠双圆胶囊 + 彩虹镭射光带
 * - 银联（UnionPay）：柔和香槟金无框圆润全息天坛徽 + 金虹全息反光
 * - Visa：银白珠光胶囊飞鸽剪影 + 银虹镭射扫光
 */
@Composable
private fun NetworkBackHologram(
    network: CardNetworkKind,
    tiltX: Float = 0f,
    tiltY: Float = 0f,
    enableDynamicLighting: Boolean = false,
    modifier: Modifier = Modifier
) {
    when (network) {
        CardNetworkKind.UNIONPAY -> UnionPayPearlHologram(tiltX, tiltY, enableDynamicLighting, modifier)
        CardNetworkKind.MASTERCARD -> MasterCardPearlHologram(tiltX, tiltY, enableDynamicLighting, modifier)
        CardNetworkKind.VISA -> VisaPearlHologram(tiltX, tiltY, enableDynamicLighting, modifier)
        else -> MasterCardPearlHologram(tiltX, tiltY, enableDynamicLighting, modifier)
    }
}

/** MasterCard 真实背面同款：银铬金属交叠双地球全息防伪标（完整经纬网球面、大陆浮雕与实时虹彩衍射）。 */
@Composable
private fun MasterCardPearlHologram(
    tiltX: Float = 0f,
    tiltY: Float = 0f,
    enableDynamicLighting: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val h = size.height
        val w = size.width
        val radius = h * 0.46f
        val leftCenter = Offset(w * 0.36f, h * 0.5f)
        val rightCenter = Offset(w * 0.64f, h * 0.5f)
        val shiftX = tiltX * w * 0.48f
        val shiftY = tiltY * h * 0.40f
        val tiltMag = hypot(tiltX, tiltY).coerceIn(0f, 1.2f)

        val leftPath = Path().apply {
            addOval(Rect(leftCenter.x - radius, leftCenter.y - radius, leftCenter.x + radius, leftCenter.y + radius))
        }
        val rightPath = Path().apply {
            addOval(Rect(rightCenter.x - radius, rightCenter.y - radius, rightCenter.x + radius, rightCenter.y + radius))
        }
        val unionPath = Path.combine(PathOperation.Union, leftPath, rightPath)

        // 1. 金属拉丝银铬基底（裁剪在交叠双圆内）
        clipPath(unionPath) {
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFD8DEE8),
                        0.28f to Color(0xFFF8FAFC),
                        0.52f to Color(0xFFA8B3C2),
                        0.78f to Color(0xFFE9EEF5),
                        1.00f to Color(0xFFBAC5D4)
                    ),
                    start = Offset(-shiftX * 0.5f, -shiftY * 0.5f),
                    end = Offset(w + shiftX * 0.5f, h + shiftY * 0.5f)
                )
            )

            // 2. 激光微雕双地球经纬网格（左右半球分别绘制经线椭圆与纬线弧）
            fun drawGlobeGraticule(center: Offset, clipGlobe: Path) {
                val gridDark = Color(0xFF334155).copy(alpha = 0.42f)
                val gridLight = Color.White.copy(alpha = 0.55f)
                val sw = 0.55.dp.toPx()

                clipPath(clipGlobe) {
                    // 球面立体微阴影（模拟 3D 凸起球体）
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.32f),
                                Color.Transparent,
                                Color(0xFF1E293B).copy(alpha = 0.24f)
                            ),
                            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.28f),
                            radius = radius * 1.15f
                        ),
                        radius = radius,
                        center = center
                    )

                    // 纬线（赤道 + 上下 ±32° / ±62°）
                    listOf(-0.62f, -0.32f, 0f, 0.32f, 0.62f).forEach { yFrac ->
                        val y = center.y + radius * yFrac
                        drawLine(
                            color = gridDark,
                            start = Offset(center.x - radius, y),
                            end = Offset(center.x + radius, y),
                            strokeWidth = sw
                        )
                    }

                    // 经线（中央子午线 + 两组椭圆弧）
                    drawLine(
                        color = gridDark,
                        start = Offset(center.x, center.y - radius),
                        end = Offset(center.x, center.y + radius),
                        strokeWidth = sw
                    )
                    listOf(0.38f, 0.72f).forEach { xScale ->
                        val rx = radius * xScale
                        drawOval(
                            color = gridDark,
                            topLeft = Offset(center.x - rx, center.y - radius),
                            size = Size(rx * 2f, radius * 2f),
                            style = Stroke(width = sw)
                        )
                    }

                    // 极简激光浮雕大陆轮廓剪影
                    val land = Path().apply {
                        moveTo(center.x - radius * 0.45f, center.y - radius * 0.25f)
                        quadraticTo(center.x - radius * 0.10f, center.y - radius * 0.48f, center.x + radius * 0.28f, center.y - radius * 0.18f)
                        quadraticTo(center.x + radius * 0.12f, center.y + radius * 0.16f, center.x - radius * 0.22f, center.y + radius * 0.36f)
                        quadraticTo(center.x - radius * 0.48f, center.y + radius * 0.08f, center.x - radius * 0.45f, center.y - radius * 0.25f)
                        close()
                    }
                    drawPath(land, color = gridLight.copy(alpha = 0.28f))
                }
            }

            drawGlobeGraticule(leftCenter, leftPath)
            drawGlobeGraticule(rightCenter, rightPath)

            // 3. 实时激光全息虹彩衍射扫光带
            val holoAlpha = if (enableDynamicLighting || tiltMag > 0.02f) {
                (0.28f + 0.38f * tiltMag).coerceAtMost(0.68f)
            } else {
                0.22f
            }
            val bandCenter = Offset(w * 0.5f + shiftX * 1.15f, h * 0.5f + shiftY * 0.9f)
            val angle = (0.55 + tiltX * 0.45).toFloat()
            val span = w * 0.88f
            val dx = cos(angle) * span
            val dy = sin(angle) * span

            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.18f to Color(0xFF38BDF8).copy(alpha = holoAlpha * 0.85f),
                        0.34f to Color(0xFFA78BFA).copy(alpha = holoAlpha * 0.88f),
                        0.48f to Color(0xFFF472B6).copy(alpha = holoAlpha * 0.82f),
                        0.58f to Color.White.copy(alpha = (holoAlpha * 1.18f).coerceAtMost(0.88f)),
                        0.72f to Color(0xFFFDE047).copy(alpha = holoAlpha * 0.80f),
                        0.86f to Color(0xFF34D399).copy(alpha = holoAlpha * 0.76f),
                        1.00f to Color.Transparent
                    ),
                    start = Offset(bandCenter.x - dx, bandCenter.y - dy),
                    end = Offset(bandCenter.x + dx, bandCenter.y + dy)
                )
            )
        }

        // 4. 双圆精细金属外轮廓刻线
        val rimStroke = Stroke(width = 0.7.dp.toPx())
        drawPath(unionPath, color = Color.White.copy(alpha = 0.65f), style = rimStroke)
        drawCircle(color = Color(0xFF475569).copy(alpha = 0.25f), radius = radius, center = leftCenter, style = Stroke(width = 0.45.dp.toPx()))
        drawCircle(color = Color(0xFF475569).copy(alpha = 0.25f), radius = radius, center = rightCenter, style = Stroke(width = 0.45.dp.toPx()))
    }
}

/** 银联真实背面同款：银金全息防伪箔片 + 激光微雕「天坛祈年殿」三重攒尖翘角檐与汉白玉基座。 */
@Composable
private fun UnionPayPearlHologram(
    tiltX: Float = 0f,
    tiltY: Float = 0f,
    enableDynamicLighting: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(h * 0.16f, h * 0.16f)
        val foilPath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, corner, corner, corner, corner))
        }
        val shiftX = tiltX * w * 0.48f
        val shiftY = tiltY * h * 0.40f
        val tiltMag = hypot(tiltX, tiltY).coerceIn(0f, 1.2f)

        clipPath(foilPath) {
            // 1. 银联经典香槟银金拉丝全息箔片底
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFE7D7A8),
                        0.26f to Color(0xFFFBF3D5),
                        0.50f to Color(0xFFCBB274),
                        0.76f to Color(0xFFF3E6BC),
                        1.00f to Color(0xFFBFA25E)
                    ),
                    start = Offset(-shiftX * 0.5f, -shiftY * 0.5f),
                    end = Offset(w + shiftX * 0.5f, h + shiftY * 0.5f)
                )
            )

            // 2. 背景微雕防伪放射纹与细密水平纹
            val cx = w * 0.5f
            val microLineColor = Color(0xFF6B4C12).copy(alpha = 0.11f)
            val microStroke = 0.4.dp.toPx()
            for (i in 1..6) {
                val y = h * (i / 7f)
                drawLine(microLineColor, Offset(0f, y), Offset(w, y), strokeWidth = microStroke)
            }
            drawCircle(
                color = Color(0xFF6B4C12).copy(alpha = 0.14f),
                radius = h * 0.42f,
                center = Offset(cx, h * 0.50f),
                style = Stroke(width = 0.55.dp.toPx())
            )

            // 3. 真实「天坛祈年殿」建筑激光浮雕剪影（宝顶 + 三重翘角攒尖顶 + 柱廊 + 三层祈谷坛）
            val roofFill = Color(0xFF5C4212).copy(alpha = 0.54f)
            val roofHighlight = Color(0xFFFFFBEB).copy(alpha = 0.58f)
            val pillarColor = Color(0xFF5C4212).copy(alpha = 0.46f)

            // 顶部鎏金宝顶
            drawLine(
                color = roofFill,
                start = Offset(cx, h * 0.10f),
                end = Offset(cx, h * 0.21f),
                strokeWidth = 1.1.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = roofHighlight, radius = 1.3.dp.toPx(), center = Offset(cx, h * 0.13f))

            // 辅助函数：绘制带翘角弧度的攒尖屋檐
            fun drawPagodaRoof(topY: Float, botY: Float, topHalfW: Float, botHalfW: Float) {
                val roof = Path().apply {
                    moveTo(cx - topHalfW, topY)
                    lineTo(cx + topHalfW, topY)
                    // 右侧翘角弧线
                    quadraticTo(cx + botHalfW * 0.72f, botY - h * 0.01f, cx + botHalfW, botY - h * 0.025f)
                    lineTo(cx + botHalfW * 0.90f, botY + h * 0.015f)
                    lineTo(cx - botHalfW * 0.90f, botY + h * 0.015f)
                    lineTo(cx - botHalfW, botY - h * 0.025f)
                    // 左侧翘角弧线
                    quadraticTo(cx - botHalfW * 0.72f, botY - h * 0.01f, cx - topHalfW, topY)
                    close()
                }
                drawPath(roof, color = roofFill)
                drawLine(
                    color = roofHighlight,
                    start = Offset(cx - botHalfW * 0.82f, botY + h * 0.008f),
                    end = Offset(cx + botHalfW * 0.82f, botY + h * 0.008f),
                    strokeWidth = 0.55.dp.toPx()
                )
            }

            // 第一重檐（顶层尖顶）
            drawPagodaRoof(topY = h * 0.19f, botY = h * 0.33f, topHalfW = w * 0.025f, botHalfW = w * 0.17f)
            // 第二重檐（中层）
            drawPagodaRoof(topY = h * 0.36f, botY = h * 0.48f, topHalfW = w * 0.11f, botHalfW = w * 0.24f)
            // 第三重檐（底层宽檐）
            drawPagodaRoof(topY = h * 0.51f, botY = h * 0.62f, topHalfW = w * 0.16f, botHalfW = w * 0.30f)

            // 殿身柱廊（每层之间微柱）
            val colStroke = 0.85.dp.toPx()
            listOf(-0.12f, -0.04f, 0.04f, 0.12f).forEach { dxFrac ->
                drawLine(pillarColor, Offset(cx + w * dxFrac, h * 0.62f), Offset(cx + w * dxFrac, h * 0.72f), strokeWidth = colStroke)
            }
            listOf(-0.08f, 0f, 0.08f).forEach { dxFrac ->
                drawLine(pillarColor, Offset(cx + w * dxFrac, h * 0.48f), Offset(cx + w * dxFrac, h * 0.52f), strokeWidth = colStroke * 0.85f)
            }

            // 三层汉白玉祈谷坛台阶基座
            drawRoundRect(
                color = roofFill.copy(alpha = 0.42f),
                topLeft = Offset(cx - w * 0.26f, h * 0.72f),
                size = Size(w * 0.52f, h * 0.055f),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
            drawRoundRect(
                color = roofFill.copy(alpha = 0.48f),
                topLeft = Offset(cx - w * 0.32f, h * 0.785f),
                size = Size(w * 0.64f, h * 0.055f),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
            drawRoundRect(
                color = roofFill.copy(alpha = 0.54f),
                topLeft = Offset(cx - w * 0.38f, h * 0.85f),
                size = Size(w * 0.76f, h * 0.055f),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )

            // 4. 实时金虹激光全息衍射扫光
            val holoAlpha = if (enableDynamicLighting || tiltMag > 0.02f) {
                (0.26f + 0.38f * tiltMag).coerceAtMost(0.66f)
            } else {
                0.20f
            }
            val bandCenter = Offset(w * 0.5f + shiftX * 1.15f, h * 0.5f + shiftY * 0.9f)
            val angle = (0.52 + tiltX * 0.42).toFloat()
            val span = w * 0.86f
            val dx = cos(angle) * span
            val dy = sin(angle) * span

            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.20f to Color(0xFFF59E0B).copy(alpha = holoAlpha * 0.72f),
                        0.36f to Color(0xFFFB7185).copy(alpha = holoAlpha * 0.68f),
                        0.50f to Color(0xFFFFFBEB).copy(alpha = (holoAlpha * 1.22f).coerceAtMost(0.88f)),
                        0.66f to Color(0xFF34D399).copy(alpha = holoAlpha * 0.74f),
                        0.82f to Color(0xFF38BDF8).copy(alpha = holoAlpha * 0.68f),
                        1.00f to Color.Transparent
                    ),
                    start = Offset(bandCenter.x - dx, bandCenter.y - dy),
                    end = Offset(bandCenter.x + dx, bandCenter.y + dy)
                )
            )
        }

        // 精细金属防伪箔片倒角微框
        drawRoundRect(
            color = Color(0xFFFFFBEB).copy(alpha = 0.58f),
            cornerRadius = corner,
            style = Stroke(width = 0.65.dp.toPx())
        )
    }
}

/** Visa 真实背面同款：银白金属全息箔片 + 展翅飞鸽精细浮雕剪影与银虹镭射扫光。 */
@Composable
private fun VisaPearlHologram(
    tiltX: Float = 0f,
    tiltY: Float = 0f,
    enableDynamicLighting: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(h * 0.16f, h * 0.16f)
        val foilPath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, corner, corner, corner, corner))
        }
        val shiftX = tiltX * w * 0.48f
        val shiftY = tiltY * h * 0.40f
        val tiltMag = hypot(tiltX, tiltY).coerceIn(0f, 1.2f)

        clipPath(foilPath) {
            // 1. 银铬拉丝全息箔片底
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFD5DCE6),
                        0.30f to Color(0xFFF8FAFC),
                        0.55f to Color(0xFFA2AEBF),
                        0.82f to Color(0xFFE9EFF6),
                        1.00f to Color(0xFFB7C2D1)
                    ),
                    start = Offset(-shiftX * 0.5f, -shiftY * 0.5f),
                    end = Offset(w + shiftX * 0.5f, h + shiftY * 0.5f)
                )
            )

            // 2. 细密防伪背景波纹线
            val waveColor = Color(0xFF475569).copy(alpha = 0.12f)
            val waveStroke = 0.45.dp.toPx()
            for (i in 1..5) {
                val y = h * (i / 6f)
                drawLine(waveColor, Offset(0f, y), Offset(w, y), strokeWidth = waveStroke)
            }

            // 3. 展翅飞鸽精细金属浮雕（上扬双翼 + 羽梢刻痕 + 躯干 + 扇形尾羽）
            val doveBody = Path().apply {
                // 鸽头与喙（朝右上方飞翔）
                moveTo(w * 0.78f, h * 0.34f)
                quadraticTo(w * 0.73f, h * 0.28f, w * 0.67f, h * 0.34f)
                // 前翼上缘（优雅扬起）
                quadraticTo(w * 0.56f, h * 0.14f, w * 0.34f, h * 0.14f)
                // 翼尖羽梢折痕
                quadraticTo(w * 0.42f, h * 0.28f, w * 0.30f, h * 0.30f)
                quadraticTo(w * 0.40f, h * 0.40f, w * 0.46f, h * 0.46f)
                // 扇形尾羽
                lineTo(w * 0.22f, h * 0.62f)
                quadraticTo(w * 0.30f, h * 0.70f, w * 0.44f, h * 0.64f)
                // 腹部与下翼
                quadraticTo(w * 0.56f, h * 0.76f, w * 0.64f, h * 0.80f)
                quadraticTo(w * 0.62f, h * 0.62f, w * 0.68f, h * 0.50f)
                quadraticTo(w * 0.74f, h * 0.42f, w * 0.78f, h * 0.34f)
                close()
            }
            drawPath(doveBody, color = Color(0xFF334155).copy(alpha = 0.48f))
            drawPath(
                doveBody,
                color = Color.White.copy(alpha = 0.72f),
                style = Stroke(width = 0.65.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
            )

            // 翼面羽纹刻线
            val featherColor = Color.White.copy(alpha = 0.55f)
            val fw = 0.5.dp.toPx()
            drawLine(featherColor, Offset(w * 0.44f, h * 0.24f), Offset(w * 0.56f, h * 0.42f), strokeWidth = fw)
            drawLine(featherColor, Offset(w * 0.38f, h * 0.32f), Offset(w * 0.52f, h * 0.46f), strokeWidth = fw)

            // 4. 银虹激光衍射扫光
            val holoAlpha = if (enableDynamicLighting || tiltMag > 0.02f) {
                (0.26f + 0.38f * tiltMag).coerceAtMost(0.66f)
            } else {
                0.20f
            }
            val bandCenter = Offset(w * 0.5f + shiftX * 1.15f, h * 0.5f + shiftY * 0.9f)
            val angle = (0.55 + tiltX * 0.45).toFloat()
            val span = w * 0.86f
            val dx = cos(angle) * span
            val dy = sin(angle) * span

            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.22f to Color(0xFF38BDF8).copy(alpha = holoAlpha * 0.82f),
                        0.40f to Color(0xFFA78BFA).copy(alpha = holoAlpha * 0.80f),
                        0.54f to Color.White.copy(alpha = (holoAlpha * 1.18f).coerceAtMost(0.86f)),
                        0.72f to Color(0xFF34D399).copy(alpha = holoAlpha * 0.74f),
                        1.00f to Color.Transparent
                    ),
                    start = Offset(bandCenter.x - dx, bandCenter.y - dy),
                    end = Offset(bandCenter.x + dx, bandCenter.y + dy)
                )
            )
        }

        drawRoundRect(
            color = Color.White.copy(alpha = 0.62f),
            cornerRadius = corner,
            style = Stroke(width = 0.65.dp.toPx())
        )
    }
}

@Composable
private fun ContactlessWaveIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
        val cx = size.width * 0.14f
        val cy = size.height * 0.5f
        listOf(0.24f, 0.44f, 0.64f, 0.84f).forEach { factor ->
            val r = size.minDimension * factor
            drawArc(
                color = color,
                startAngle = -42f,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = stroke
            )
        }
    }
}

/** 正面只保留纯尾号 4 位数字，去掉前面冗余的省略号，保持卡面极简清爽。 */
private fun formatCardFrontTail(numberPreview: String): String {
    val digits = numberPreview.filter { it.isDigit() }
    return if (digits.length >= 4) {
        digits.takeLast(4)
    } else {
        numberPreview.replace("•", "").trim()
    }
}

/** 背面完整卡号区：解锁明文时按 4 位分组显示完整卡号，未解锁/遮挡时以省略号隐藏前段。 */
private fun formatCardBackNumber(
    cardNumber: String,
    numberPreview: String,
    revealed: Boolean
): String {
    val fullDigits = cardNumber.filter { it.isDigit() }
    if (revealed && fullDigits.length >= 8) {
        return fullDigits.chunked(4).joinToString("  ")
    }
    val tail = fullDigits.takeLast(4).ifEmpty {
        numberPreview.filter { it.isDigit() }.takeLast(4)
    }
    return if (tail.isNotEmpty()) {
        "••••  ••••  ••••  $tail"
    } else {
        "••••  ••••  ••••  ••••"
    }
}

private data class CardPalette(
    val top: Color,
    val bottom: Color,
    val onSurface: Color = Color.White
)

private val monetBackPaletteCache: MutableMap<ImageBitmap, CardPalette> =
    Collections.synchronizedMap(WeakHashMap())

/**
 * 莫奈风格卡背取色算法（带 WeakHashMap 内存缓存 + 32x20 极速降采样，耗时 < 0.05ms，零主线程大数组分配）：
 * 分别对正面卡图上半区与下半区采样，真实还原卡面的主色相、饱和度与明暗质感：
 * - 浅色/高明度卡面（如天蓝初音未来、银白卡）生成同样明亮通透的浅莫奈色背，并自动切换深色墨水前景色；
 * - 中等/深色卡面（如深蓝招行一卡通、黑金卡）生成饱满沉稳的同色系莫奈背，搭配纯白前景色。
 */
private fun extractMonetBackPalette(bitmap: ImageBitmap): CardPalette {
    monetBackPaletteCache[bitmap]?.let { return it }

    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return presetPalette(CardFacePreset.OBSIDIAN)

    val sampleW = 16
    val sampleH = 12
    val pixels = IntArray(sampleW * sampleH)
    val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap.asAndroidBitmap(), sampleW, sampleH, true)
    scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

    fun sampleRegion(rowStart: Int, rowEndExclusive: Int): Color {
        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        var wSum = 0f
        val hsv = FloatArray(3)
        for (r in rowStart until rowEndExclusive) {
            val rowOffset = r * sampleW
            for (c in 0 until sampleW) {
                val argb = pixels[rowOffset + c]
                val a = (argb ushr 24) and 0xFF
                if (a < 128) continue
                val red = (argb ushr 16) and 0xFF
                val green = (argb ushr 8) and 0xFF
                val blue = argb and 0xFF
                android.graphics.Color.RGBToHSV(red, green, blue, hsv)
                val weight = 0.55f + hsv[1] * 1.35f
                rSum += (red / 255f) * weight
                gSum += (green / 255f) * weight
                bSum += (blue / 255f) * weight
                wSum += weight
            }
        }
        if (wSum <= 0f) return Color(0xFF1A1B1E)
        val avgR = (rSum / wSum).coerceIn(0f, 1f)
        val avgG = (gSum / wSum).coerceIn(0f, 1f)
        val avgB = (bSum / wSum).coerceIn(0f, 1f)
        android.graphics.Color.RGBToHSV(
            (avgR * 255f).toInt(),
            (avgG * 255f).toInt(),
            (avgB * 255f).toInt(),
            hsv
        )
        val boostedSat = if (hsv[1] > 0.08f) (hsv[1] * 1.12f).coerceAtMost(0.88f) else hsv[1]
        val clampedVal = hsv[2].coerceIn(0.14f, 0.90f)
        return Color.hsv(hsv[0], boostedSat, clampedVal)
    }

    val topColor = sampleRegion(0, sampleH / 2)
    val bottomColor = sampleRegion(sampleH / 2, sampleH)

    fun luminance(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    val avgLuma = (luminance(topColor) + luminance(bottomColor)) * 0.5f
    val onSurface = if (avgLuma > 0.50f) {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (bottomColor.red * 255f).toInt(),
            (bottomColor.green * 255f).toInt(),
            (bottomColor.blue * 255f).toInt(),
            hsv
        )
        Color.hsv(hsv[0], (hsv[1] * 0.85f).coerceAtMost(0.65f), 0.16f)
    } else {
        Color.White
    }
    return CardPalette(top = topColor, bottom = bottomColor, onSurface = onSurface).also {
        monetBackPaletteCache[bitmap] = it
    }
}

/** 纯净哑光色板（无金属拉丝、无杂乱条纹）。 */
private fun presetPalette(preset: CardFacePreset): CardPalette = when (preset) {
    CardFacePreset.OBSIDIAN -> CardPalette(
        top = Color(0xFF1A1B1E),
        bottom = Color(0xFF111214)
    )
    CardFacePreset.TITANIUM -> CardPalette(
        top = Color(0xFF2B2F36),
        bottom = Color(0xFF1E2127)
    )
    CardFacePreset.SLATE_GRID -> CardPalette(
        top = Color(0xFF1B2733),
        bottom = Color(0xFF121A22)
    )
    CardFacePreset.GRAPHITE_WAVE -> CardPalette(
        top = Color(0xFF282320),
        bottom = Color(0xFF1B1816)
    )
}

@Composable
private fun CardMatteBackground(palette: CardPalette) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette.top, palette.bottom)
            )
        )
    }
}

/**
 * 1.586:1 迷你卡面缩略图（用于 Google Wallet 下方全部卡片快速索引列表）。
 */
@Composable
fun CardMiniThumbnail(
    issuer: String,
    preset: CardFacePreset = CardFacePreset.OBSIDIAN,
    artBitmap: ImageBitmap? = null,
    modifier: Modifier = Modifier
) {
    val palette = presetPalette(preset)
    val bankIconRes = resolveBankIconRes(issuer)

    Surface(
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
            .width(48.dp)
            .aspectRatio(STANDARD_CARD_ASPECT_RATIO)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (artBitmap != null) {
                Image(
                    bitmap = artBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CardMatteBackground(palette = palette)
            }

            if (bankIconRes != null) {
                Icon(
                    painter = painterResource(bankIconRes),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun resolveBankIconRes(issuer: String): Int? {
    val s = issuer.lowercase()
    return when {
        s.contains("工商") || s.contains("icbc") -> R.drawable.ic_bank_icbc
        s.contains("招商") || s.contains("cmb") -> R.drawable.ic_bank_cmb
        s.contains("中国银行") || s.contains("中行") || s == "boc" -> R.drawable.ic_bank_boc
        s.contains("交通") || s.contains("交行") || s.contains("bocom") -> R.drawable.ic_bank_bocom
        s.contains("建设") || s.contains("建行") || s.contains("ccb") -> R.drawable.ic_bank_ccb
        s.contains("农业") || s.contains("农行") || s.contains("abc") -> R.drawable.ic_bank_abc
        s.contains("邮政") || s.contains("邮储") || s.contains("psbc") -> R.drawable.ic_bank_psbc
        s.contains("中信") || s.contains("citic") -> R.drawable.ic_bank_citic
        s.contains("浦发") || s.contains("spdb") -> R.drawable.ic_bank_spdb
        s.contains("兴业") || s.contains("cib") -> R.drawable.ic_bank_cib
        s.contains("光大") || s.contains("ceb") -> R.drawable.ic_bank_ceb
        s.contains("民生") || s.contains("cmbc") -> R.drawable.ic_bank_cmbc
        s.contains("平安") || s.contains("pab") -> R.drawable.ic_bank_pab
        s.contains("广发") || s.contains("cgb") -> R.drawable.ic_bank_cgb
        else -> null
    }
}

private fun resolveCardNetworkKind(
    name: String,
    issuer: String,
    cardNumber: String,
    network: String = ""
): CardNetworkKind {
    val net = network.trim().uppercase()
    when {
        net == "VISA" -> return CardNetworkKind.VISA
        net == "MASTERCARD" -> return CardNetworkKind.MASTERCARD
        net == "UNIONPAY" -> return CardNetworkKind.UNIONPAY
        net == "AMEX" || net == "AMERICAN EXPRESS" -> return CardNetworkKind.AMEX
        net == "JCB" -> return CardNetworkKind.JCB
    }
    val text = "$name $issuer".lowercase()
    val digits = cardNumber.filter { it.isDigit() }
    return when {
        digits.startsWith("4") || text.contains("visa") -> CardNetworkKind.VISA
        digits.startsWith("51") || digits.startsWith("52") || digits.startsWith("53") ||
            digits.startsWith("54") || digits.startsWith("55") ||
            text.contains("master") || text.contains("万事达") -> CardNetworkKind.MASTERCARD
        digits.startsWith("34") || digits.startsWith("37") ||
            text.contains("amex") || text.contains("运通") -> CardNetworkKind.AMEX
        digits.startsWith("35") || text.contains("jcb") -> CardNetworkKind.JCB
        digits.startsWith("62") || text.contains("银联") || text.contains("unionpay") ||
            resolveBankIconRes(issuer) != null -> CardNetworkKind.UNIONPAY
        else -> CardNetworkKind.UNKNOWN
    }
}

private fun resolveCardNetworkRes(
    name: String,
    issuer: String,
    cardNumber: String,
    network: String = ""
): Int? = when (resolveCardNetworkKind(name, issuer, cardNumber, network)) {
    CardNetworkKind.VISA -> R.drawable.ic_network_visa
    CardNetworkKind.MASTERCARD -> R.drawable.ic_network_mastercard
    CardNetworkKind.AMEX -> R.drawable.ic_network_amex
    CardNetworkKind.JCB -> R.drawable.ic_network_jcb
    CardNetworkKind.UNIONPAY -> R.drawable.ic_network_unionpay
    CardNetworkKind.UNKNOWN -> null
}

private fun resolveCardKindTag(name: String, cardNumber: String, cardType: String = ""): String? {
    val explicit = cardType.trim().uppercase()
    if (explicit == "DEBIT" || explicit == "CREDIT" || explicit == "PREPAID") return explicit
    val s = name.lowercase()
    return when {
        s.contains("debit") || s.contains("借记") || s.contains("储蓄") || s.contains("工资") -> "DEBIT"
        s.contains("credit") || s.contains("信用") || s.contains("运通") ||
            s.contains("visa") || s.contains("master") || s.contains("万事达") -> "CREDIT"
        cardNumber.filter { it.isDigit() }.startsWith("4") -> "CREDIT"
        else -> null
    }
}

/** 为暗房聚光舞台提取与卡面呼应的柔和主色调（有自定义图时取莫奈高光色，无图时取预设色）。 */
fun cardSpotlightAccentColor(artBitmap: ImageBitmap?, preset: CardFacePreset): Color {
    if (artBitmap != null) {
        val monet = extractMonetBackPalette(artBitmap)
        return Color(
            red = (monet.top.red * 2.4f + 0.16f).coerceIn(0.20f, 0.85f),
            green = (monet.top.green * 2.4f + 0.16f).coerceIn(0.20f, 0.85f),
            blue = (monet.top.blue * 2.4f + 0.16f).coerceIn(0.22f, 0.90f),
            alpha = 1f
        )
    }
    return when (preset) {
        CardFacePreset.OBSIDIAN -> Color(0xFF94A3B8)
        CardFacePreset.TITANIUM -> Color(0xFF7DD3FC)
        CardFacePreset.SLATE_GRID -> Color(0xFF818CF8)
        CardFacePreset.GRAPHITE_WAVE -> Color(0xFFD6B588)
    }
}

@Preview(name = "哑光卡面正反面与卡组织展示", showBackground = true)
@Composable
fun PreviewCardFaceGallery() {
    CardVaultTheme(dynamicColor = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CardFaceSurface(
                name = "工资卡",
                issuer = "工商银行",
                numberPreview = "•••• 4321",
                preset = CardFacePreset.OBSIDIAN
            )
            CardFaceSurface(
                name = "海淘 MasterCard 信用卡",
                issuer = "招商银行",
                numberPreview = "•••• 8899",
                preset = CardFacePreset.TITANIUM
            )
            // 银联卡背面（金色天坛全息标 + 模拟陀螺仪倾斜金虹追光）
            CardFaceBackSurface(
                name = "工资卡",
                issuer = "工商银行",
                numberPreview = "•••• 4321",
                cardNumber = "6212260200004321",
                isNumberRevealed = false,
                expiryDisplay = "12/29",
                cvvDisplay = "•••",
                preset = CardFacePreset.OBSIDIAN,
                tiltX = 0.38f,
                tiltY = -0.22f,
                enableDynamicLighting = true
            )
            // MasterCard 卡背面（模拟陀螺仪倾斜双圆虹彩全息反光 + 卡面柔光扫光）
            CardFaceBackSurface(
                name = "海淘 MasterCard 信用卡",
                issuer = "招商银行",
                numberPreview = "•••• 8899",
                cardNumber = "5240123456788899",
                isNumberRevealed = true,
                expiryDisplay = "08/28",
                cvvDisplay = "888",
                preset = CardFacePreset.TITANIUM,
                tiltX = -0.42f,
                tiltY = 0.24f,
                enableDynamicLighting = true
            )
        }
    }
}
