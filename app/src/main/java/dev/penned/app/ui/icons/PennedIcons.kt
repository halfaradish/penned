package dev.penned.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘 Tab 图标（D011：不引 material-icons-extended）。
 * 路径为手写的简化 24dp 图形，够 M0 用，M6 打磨阶段可替换为正式设计。
 */
object PennedIcons {

    val Home: ImageVector by lazy {
        icon("home") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                lineTo(21f, 11f)
                lineTo(18f, 11f)
                lineTo(18f, 21f)
                lineTo(14f, 21f)
                lineTo(14f, 15f)
                lineTo(10f, 15f)
                lineTo(10f, 21f)
                lineTo(6f, 21f)
                lineTo(6f, 11f)
                lineTo(3f, 11f)
                close()
            }
        }
    }

    val Calendar: ImageVector by lazy {
        icon("calendar") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 2f)
                lineTo(7f, 4f)
                lineTo(5f, 4f)
                arcTo(2f, 2f, 0f, false, false, 3f, 6f)
                lineTo(3f, 20f)
                arcTo(2f, 2f, 0f, false, false, 5f, 22f)
                lineTo(19f, 22f)
                arcTo(2f, 2f, 0f, false, false, 21f, 20f)
                lineTo(21f, 6f)
                arcTo(2f, 2f, 0f, false, false, 19f, 4f)
                lineTo(17f, 4f)
                lineTo(17f, 2f)
                lineTo(15f, 2f)
                lineTo(15f, 4f)
                lineTo(9f, 4f)
                lineTo(9f, 2f)
                close()
                moveTo(5f, 9f)
                lineTo(19f, 9f)
                lineTo(19f, 20f)
                lineTo(5f, 20f)
                close()
            }
        }
    }

    val Wallet: ImageVector by lazy {
        icon("wallet") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 6f)
                arcTo(2f, 2f, 0f, false, true, 6f, 4f)
                lineTo(18f, 4f)
                lineTo(18f, 7f)
                lineTo(20f, 7f)
                arcTo(2f, 2f, 0f, false, true, 22f, 9f)
                lineTo(22f, 19f)
                arcTo(2f, 2f, 0f, false, true, 20f, 21f)
                lineTo(4f, 21f)
                arcTo(2f, 2f, 0f, false, true, 2f, 19f)
                lineTo(2f, 8f)
                arcTo(2f, 2f, 0f, false, true, 4f, 6f)
                close()
                moveTo(4f, 8f)
                lineTo(4f, 19f)
                lineTo(20f, 19f)
                lineTo(20f, 9f)
                lineTo(4f, 9f)
                close()
                moveTo(16f, 12f)
                arcTo(1.5f, 1.5f, 0f, false, true, 16f, 15f)
                arcTo(1.5f, 1.5f, 0f, false, true, 16f, 12f)
                close()
            }
        }
    }

    val Pie: ImageVector by lazy {
        icon("pie") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                arcTo(10f, 10f, 0f, true, false, 22f, 12f)
                lineTo(12f, 12f)
                close()
            }
        }
    }

    val Person: ImageVector by lazy {
        icon("person") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12f)
                arcTo(4.5f, 4.5f, 0f, true, false, 12f, 3f)
                arcTo(4.5f, 4.5f, 0f, false, false, 12f, 12f)
                close()
                moveTo(4f, 21f)
                curveTo(4f, 17f, 8f, 15f, 12f, 15f)
                curveTo(16f, 15f, 20f, 17f, 20f, 21f)
                close()
            }
        }
    }

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()
}
