package com.personal.cardvault.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.cardvault.R

/**
 * 界面层矢量图标集合。
 * 为避免依赖额外库，全部图标以轻量矢量 Path 纯代码构建。
 */
object CardVaultIcons {
    private fun createVector(name: String, block: ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    val Add: ImageVector by lazy {
        createVector("Add") {
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 13f)
                horizontalLineTo(13f)
                verticalLineTo(19f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                horizontalLineTo(5f)
                verticalLineTo(11f)
                horizontalLineTo(11f)
                verticalLineTo(5f)
                horizontalLineTo(13f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                verticalLineTo(13f)
                close()
            }
        }
    }

    val ArrowBack: ImageVector by lazy {
        createVector("ArrowBack") {
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 11f)
                horizontalLineTo(7.83f)
                lineTo(13.42f, 5.41f)
                lineTo(12f, 4f)
                lineTo(4f, 12f)
                lineTo(12f, 20f)
                lineTo(13.41f, 18.59f)
                lineTo(7.83f, 13f)
                horizontalLineTo(20f)
                verticalLineTo(11f)
                close()
            }
        }
    }

    val Check: ImageVector by lazy {
        createVector("Check") {
            path(fill = SolidColor(Color.White)) {
                moveTo(9f, 16.17f)
                lineTo(4.83f, 12f)
                lineTo(3.41f, 13.41f)
                lineTo(9f, 19f)
                lineTo(21f, 7f)
                lineTo(19.59f, 5.59f)
                lineTo(9f, 16.17f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        createVector("Close") {
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 6.41f)
                lineTo(17.59f, 5f)
                lineTo(12f, 10.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(12f, 13.41f)
                lineTo(17.59f, 19f)
                lineTo(19f, 17.59f)
                lineTo(13.41f, 12f)
                lineTo(19f, 6.41f)
                close()
            }
        }
    }

    val Delete: ImageVector by lazy {
        createVector("Delete") {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 19f)
                curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                horizontalLineTo(16f)
                curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(19f, 4f)
                horizontalLineTo(15.5f)
                lineTo(14.5f, 3f)
                horizontalLineTo(9.5f)
                lineTo(8.5f, 4f)
                horizontalLineTo(5f)
                verticalLineTo(6f)
                horizontalLineTo(19f)
                verticalLineTo(4f)
                close()
            }
        }
    }

    val Edit: ImageVector by lazy {
        createVector("Edit") {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 17.25f)
                verticalLineTo(21f)
                horizontalLineTo(6.75f)
                lineTo(17.81f, 9.94f)
                lineTo(14.06f, 6.19f)
                lineTo(3f, 17.25f)
                close()
                moveTo(20.71f, 7.04f)
                curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
                lineTo(18.37f, 3.29f)
                curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
                lineTo(15.13f, 5.12f)
                lineTo(18.88f, 8.87f)
                lineTo(20.71f, 7.04f)
                close()
            }
        }
    }

    val Lock: ImageVector by lazy {
        createVector("Lock") {
            path(fill = SolidColor(Color.White)) {
                moveTo(18f, 8f)
                horizontalLineTo(17f)
                verticalLineTo(6f)
                curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
                curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f)
                verticalLineTo(8f)
                horizontalLineTo(6f)
                curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
                verticalLineTo(20f)
                curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
                horizontalLineTo(18f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                verticalLineTo(10f)
                curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
                close()
                moveTo(12f, 17f)
                curveTo(10.9f, 17f, 10f, 16.1f, 10f, 15f)
                curveTo(10f, 13.9f, 10.9f, 13f, 12f, 13f)
                curveTo(13.1f, 13f, 14f, 13.9f, 14f, 15f)
                curveTo(14f, 16.1f, 13.1f, 17f, 12f, 17f)
                close()
                moveTo(15.1f, 8f)
                horizontalLineTo(8.9f)
                verticalLineTo(6f)
                curveTo(8.9f, 4.29f, 10.29f, 2.9f, 12f, 2.9f)
                curveTo(13.71f, 2.9f, 15.1f, 4.29f, 15.1f, 6f)
                verticalLineTo(8f)
                close()
            }
        }
    }

    val Refresh: ImageVector by lazy {
        createVector("Refresh") {
            path(fill = SolidColor(Color.White)) {
                moveTo(17.65f, 6.35f)
                curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
                curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
                curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
                curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
                horizontalLineTo(17.65f)
                curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
                curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
                curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
                curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
                lineTo(13f, 11f)
                horizontalLineTo(20f)
                verticalLineTo(4f)
                lineTo(17.65f, 6.35f)
                close()
            }
        }
    }

    val Settings: ImageVector by lazy {
        createVector("Settings") {
            path(fill = SolidColor(Color.White)) {
                moveTo(19.14f, 12.94f)
                curveTo(19.18f, 12.63f, 19.2f, 12.32f, 19.2f, 12f)
                curveTo(19.2f, 11.68f, 19.18f, 11.37f, 19.14f, 11.06f)
                lineTo(21.4f, 9.3f)
                curveTo(21.6f, 9.14f, 21.65f, 8.85f, 21.53f, 8.63f)
                lineTo(19.4f, 4.95f)
                curveTo(19.28f, 4.73f, 19.01f, 4.65f, 18.78f, 4.74f)
                lineTo(16.12f, 5.81f)
                curveTo(15.57f, 5.39f, 14.97f, 5.04f, 14.31f, 4.77f)
                lineTo(13.91f, 1.95f)
                curveTo(13.88f, 1.69f, 13.66f, 1.5f, 13.4f, 1.5f)
                horizontalLineTo(9.2f)
                curveTo(8.94f, 1.5f, 8.72f, 1.69f, 8.69f, 1.95f)
                lineTo(8.29f, 4.77f)
                curveTo(7.63f, 5.04f, 7.03f, 5.39f, 6.48f, 5.81f)
                lineTo(3.82f, 4.74f)
                curveTo(3.59f, 4.65f, 3.32f, 4.73f, 3.2f, 4.95f)
                lineTo(1.07f, 8.63f)
                curveTo(0.95f, 8.85f, 1f, 9.14f, 1.2f, 9.3f)
                lineTo(3.46f, 11.06f)
                curveTo(3.42f, 11.37f, 3.4f, 11.68f, 3.4f, 12f)
                curveTo(3.4f, 12.32f, 3.42f, 12.63f, 3.46f, 12.94f)
                lineTo(1.2f, 14.7f)
                curveTo(1f, 14.86f, 0.95f, 15.15f, 1.07f, 15.37f)
                lineTo(3.2f, 19.05f)
                curveTo(3.32f, 19.27f, 3.59f, 19.35f, 3.82f, 19.26f)
                lineTo(6.48f, 18.19f)
                curveTo(7.03f, 18.61f, 7.63f, 18.96f, 8.29f, 19.23f)
                lineTo(8.69f, 22.05f)
                curveTo(8.72f, 22.31f, 8.94f, 22.5f, 9.2f, 22.5f)
                horizontalLineTo(13.4f)
                curveTo(13.66f, 22.5f, 13.88f, 22.31f, 13.91f, 22.05f)
                lineTo(14.31f, 19.23f)
                curveTo(14.97f, 18.96f, 15.57f, 18.61f, 16.12f, 18.19f)
                lineTo(18.78f, 19.26f)
                curveTo(19.01f, 19.35f, 19.28f, 19.27f, 19.4f, 19.05f)
                lineTo(21.53f, 15.37f)
                curveTo(21.65f, 15.15f, 21.6f, 14.86f, 21.4f, 14.7f)
                lineTo(19.14f, 12.94f)
                close()
                moveTo(11.3f, 15.5f)
                curveTo(9.37f, 15.5f, 7.8f, 13.93f, 7.8f, 12f)
                curveTo(7.8f, 10.07f, 9.37f, 8.5f, 11.3f, 8.5f)
                curveTo(13.23f, 8.5f, 14.8f, 10.07f, 14.8f, 12f)
                curveTo(14.8f, 13.93f, 13.23f, 15.5f, 11.3f, 15.5f)
                close()
            }
        }
    }

    val Warning: ImageVector by lazy {
        createVector("Warning") {
            path(fill = SolidColor(Color.White)) {
                moveTo(1f, 21f)
                horizontalLineTo(23f)
                lineTo(12f, 2f)
                lineTo(1f, 21f)
                close()
                moveTo(13f, 18f)
                horizontalLineTo(11f)
                verticalLineTo(16f)
                horizontalLineTo(13f)
                verticalLineTo(18f)
                close()
                moveTo(13f, 14f)
                horizontalLineTo(11f)
                verticalLineTo(10f)
                horizontalLineTo(13f)
                verticalLineTo(14f)
                close()
            }
        }
    }

    val Calendar: ImageVector by lazy {
        createVector("Calendar") {
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 4f)
                horizontalLineTo(18f)
                verticalLineTo(2f)
                horizontalLineTo(16f)
                verticalLineTo(4f)
                horizontalLineTo(8f)
                verticalLineTo(2f)
                horizontalLineTo(6f)
                verticalLineTo(4f)
                horizontalLineTo(5f)
                curveTo(3.89f, 4f, 3.01f, 4.9f, 3.01f, 6f)
                lineTo(3f, 20f)
                curveTo(3f, 21.1f, 3.89f, 22f, 5f, 22f)
                horizontalLineTo(19f)
                curveTo(20.1f, 22f, 21f, 21.1f, 21f, 20f)
                verticalLineTo(6f)
                curveTo(21f, 4.9f, 20.1f, 4f, 19f, 4f)
                close()
                moveTo(19f, 20f)
                horizontalLineTo(5f)
                verticalLineTo(9f)
                horizontalLineTo(19f)
                verticalLineTo(20f)
                close()
            }
        }
    }

    val Shield: ImageVector by lazy {
        createVector("Shield") {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineTo(11.09f)
                curveTo(4f, 16.14f, 7.41f, 20.85f, 12f, 22f)
                curveTo(16.59f, 20.85f, 20f, 16.14f, 20f, 11.09f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                close()
            }
        }
    }

    val Copy: ImageVector by lazy {
        createVector("Copy") {
            path(fill = SolidColor(Color.White)) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
                verticalLineTo(17f)
                horizontalLineTo(4f)
                verticalLineTo(3f)
                horizontalLineTo(16f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
                verticalLineTo(21f)
                curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
                horizontalLineTo(19f)
                curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
                verticalLineTo(7f)
                curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineTo(19f)
                verticalLineTo(21f)
                close()
            }
        }
    }

    val Visibility: ImageVector by lazy {
        createVector("Visibility") {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 4.5f)
                curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
                curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
                curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
                curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
                close()
                moveTo(12f, 17f)
                curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
                curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
                curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
                curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
                close()
                moveTo(12f, 9f)
                curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
                curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
                curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
                curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
                close()
            }
        }
    }

    val VisibilityOff: ImageVector by lazy {
        createVector("VisibilityOff") {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 7f)
                curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
                curveTo(17f, 12.64f, 16.87f, 13.26f, 16.64f, 13.82f)
                lineTo(19.57f, 16.75f)
                curveTo(21.07f, 15.5f, 22.27f, 13.86f, 23f, 12f)
                curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
                curveTo(10.6f, 4.5f, 9.27f, 4.75f, 8.04f, 5.22f)
                lineTo(10.18f, 7.36f)
                curveTo(10.74f, 7.13f, 11.36f, 7f, 12f, 7f)
                close()
                moveTo(2f, 4.27f)
                lineTo(4.28f, 6.55f)
                lineTo(4.74f, 7.01f)
                curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
                curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
                curveTo(13.55f, 19.5f, 15.03f, 19.2f, 16.38f, 18.66f)
                lineTo(16.82f, 19.1f)
                lineTo(19.73f, 22f)
                lineTo(21f, 20.73f)
                lineTo(3.27f, 3f)
                lineTo(2f, 4.27f)
                close()
                moveTo(7.53f, 9.8f)
                lineTo(9.08f, 11.35f)
                curveTo(9.03f, 11.56f, 9f, 11.78f, 9f, 12f)
                curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
                curveTo(12.22f, 15f, 12.44f, 14.97f, 12.65f, 14.92f)
                lineTo(14.2f, 16.47f)
                curveTo(13.53f, 16.8f, 12.79f, 17f, 12f, 17f)
                curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
                curveTo(7f, 11.21f, 7.2f, 10.47f, 7.53f, 9.8f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        createVector("Pause") {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 19f)
                horizontalLineTo(10f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(14f, 5f)
                verticalLineTo(19f)
                horizontalLineTo(18f)
                verticalLineTo(5f)
                horizontalLineTo(14f)
                close()
            }
        }
    }

    val Play: ImageVector by lazy {
        createVector("Play") {
            path(fill = SolidColor(Color.White)) {
                moveTo(8f, 5f)
                verticalLineTo(19f)
                lineTo(19f, 12f)
                lineTo(8f, 5f)
                close()
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        createVector("ChevronRight") {
            path(fill = SolidColor(Color.White)) {
                moveTo(10f, 6f)
                lineTo(8.59f, 7.41f)
                lineTo(13.17f, 12f)
                lineTo(8.59f, 16.59f)
                lineTo(10f, 18f)
                lineTo(16f, 12f)
                close()
            }
        }
    }

    /** 横向轮播视图图标（中间主卡 + 两侧缩略边卡）。 */
    val ViewCarousel: ImageVector by lazy {
        createVector("ViewCarousel") {
            path(fill = SolidColor(Color.White)) {
                // 中间主卡片
                moveTo(7f, 5f)
                horizontalLineTo(17f)
                verticalLineTo(19f)
                horizontalLineTo(7f)
                close()
                // 左侧边卡
                moveTo(2f, 7f)
                horizontalLineTo(5f)
                verticalLineTo(17f)
                horizontalLineTo(2f)
                close()
                // 右侧边卡
                moveTo(19f, 7f)
                horizontalLineTo(22f)
                verticalLineTo(17f)
                horizontalLineTo(19f)
                close()
            }
        }
    }

    /** 竖向叠放卡包图标（三层水平叠放卡片）。 */
    val ViewStack: ImageVector by lazy {
        createVector("ViewStack") {
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 6f)
                horizontalLineTo(20f)
                verticalLineTo(9f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 11f)
                horizontalLineTo(20f)
                verticalLineTo(14f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 16f)
                horizontalLineTo(20f)
                verticalLineTo(19f)
                horizontalLineTo(4f)
                close()
            }
        }
    }

    /** 底部导航 Tab 1（Expiry）：现代计时表盘/期限环形刻度图标。 */
    val TabExpiry: ImageVector by lazy {
        createVector("TabExpiry") {
            path(fill = SolidColor(Color.White)) {
                // 顶部计时冠柄
                moveTo(9f, 2f)
                horizontalLineTo(15f)
                verticalLineTo(4f)
                horizontalLineTo(9f)
                close()
                // 表盘圆环与内部指针
                moveTo(12f, 5f)
                curveTo(7.58f, 5f, 4f, 8.58f, 4f, 13f)
                curveTo(4f, 17.42f, 7.58f, 21f, 12f, 21f)
                curveTo(16.42f, 21f, 20f, 17.42f, 20f, 13f)
                curveTo(20f, 8.58f, 16.42f, 5f, 12f, 5f)
                close()
                moveTo(12f, 19f)
                curveTo(8.69f, 19f, 6f, 16.31f, 6f, 13f)
                curveTo(6f, 9.69f, 8.69f, 7f, 12f, 7f)
                curveTo(15.31f, 7f, 18f, 9.69f, 18f, 13f)
                curveTo(18f, 16.31f, 15.31f, 19f, 12f, 19f)
                close()
                // 中心到期指针
                moveTo(11.2f, 9.5f)
                horizontalLineTo(12.8f)
                verticalLineTo(12.6f)
                lineTo(15.3f, 14.3f)
                lineTo(14.4f, 15.6f)
                lineTo(11.2f, 13.4f)
                close()
            }
        }
    }

    /** 底部导航 Tab 2（Wallet）：现代双层错位叠放实体卡包图标。 */
    val TabWallet: ImageVector by lazy {
        createVector("TabWallet") {
            path(fill = SolidColor(Color.White)) {
                // 后层叠放卡片顶边弧角
                moveTo(5f, 4f)
                horizontalLineTo(19f)
                curveTo(20.1f, 4f, 21f, 4.9f, 21f, 6f)
                verticalLineTo(6.5f)
                horizontalLineTo(3f)
                verticalLineTo(6f)
                curveTo(3f, 4.9f, 3.9f, 4f, 5f, 4f)
                close()
                // 前层主卡片（含芯片槽与圆角）
                moveTo(4f, 8f)
                horizontalLineTo(20f)
                curveTo(21.1f, 8f, 22f, 8.9f, 22f, 10f)
                verticalLineTo(18f)
                curveTo(22f, 19.1f, 21.1f, 20f, 20f, 20f)
                horizontalLineTo(4f)
                curveTo(2.9f, 20f, 2f, 19.1f, 2f, 18f)
                verticalLineTo(10f)
                curveTo(2f, 8.9f, 2.9f, 8f, 4f, 8f)
                close()
                moveTo(4f, 10f)
                verticalLineTo(18f)
                horizontalLineTo(20f)
                verticalLineTo(10f)
                horizontalLineTo(4f)
                close()
                // 卡片内部智能芯片标识
                moveTo(6f, 12.5f)
                horizontalLineTo(9.5f)
                verticalLineTo(15.5f)
                horizontalLineTo(6f)
                close()
            }
        }
    }

    /** 底部导航 Tab 3（Config）：现代三轨调音台推子配置图标。 */
    val TabConfig: ImageVector by lazy {
        createVector("TabConfig") {
            path(fill = SolidColor(Color.White)) {
                // 上轨
                moveTo(3f, 6f)
                horizontalLineTo(13f)
                verticalLineTo(8f)
                horizontalLineTo(3f)
                close()
                moveTo(15f, 4.5f)
                horizontalLineTo(17.5f)
                verticalLineTo(9.5f)
                horizontalLineTo(15f)
                close()
                moveTo(19f, 6f)
                horizontalLineTo(21f)
                verticalLineTo(8f)
                horizontalLineTo(19f)
                close()
                // 中轨
                moveTo(3f, 11f)
                horizontalLineTo(7f)
                verticalLineTo(13f)
                horizontalLineTo(3f)
                close()
                moveTo(8.5f, 9.5f)
                horizontalLineTo(11f)
                verticalLineTo(14.5f)
                horizontalLineTo(8.5f)
                close()
                moveTo(12.5f, 11f)
                horizontalLineTo(21f)
                verticalLineTo(13f)
                horizontalLineTo(12.5f)
                close()
                // 下轨
                moveTo(3f, 16f)
                horizontalLineTo(14f)
                verticalLineTo(18f)
                horizontalLineTo(3f)
                close()
                moveTo(15.5f, 14.5f)
                horizontalLineTo(18f)
                verticalLineTo(19.5f)
                horizontalLineTo(15.5f)
                close()
                moveTo(19.5f, 16f)
                horizontalLineTo(21f)
                verticalLineTo(18f)
                horizontalLineTo(19.5f)
                close()
            }
        }
    }
}

/**
 * 顶部导航栏左上角 CHRONO 品牌透明矢量徽标（28dp × 150.08dp）。
 * 自动根据当前主题背景明暗切换 `#171614` 墨色版与 `#F4F1EA` 暖白深色版，同时始终保留琥珀色圆点双色，绝不套用单色 tint。
 */
@Composable
fun ChronoTopBarLogo(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceLuma = 0.2126f * surfaceColor.red + 0.7152f * surfaceColor.green + 0.0722f * surfaceColor.blue
    val drawableRes = if (surfaceLuma < 0.45f) R.drawable.chrono_nav_dark else R.drawable.chrono_nav
    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height)
    )
}
