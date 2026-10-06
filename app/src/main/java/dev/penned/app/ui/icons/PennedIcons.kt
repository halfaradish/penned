package dev.penned.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘图标集（D011：不引 material-icons-extended）。
 *
 * 统一语言：24dp 画布、1.9 描边、圆头圆角、只用线画（界面里不再出现 `‹ › + ✓ ⇅` 这类
 * 文字字形图标）。颜色由 `Icon(tint = ...)` 统一着色，路径里的 SolidColor 只是占位。
 */
object PennedIcons {

    /** 返回箭头（顶栏左槽）。 */
    val Back: ImageVector by lazy { icon("back") { stroke { chevronLeft() } } }

    val ChevronRight: ImageVector by lazy { icon("chevron_right") { stroke { chevronRight() } } }

    /** 账本名后的下拉指示。 */
    val ChevronDown: ImageVector by lazy {
        icon("chevron_down") { stroke { line(6.8f, 9.8f, 12f, 15f); line(12f, 15f, 17.2f, 9.8f) } }
    }

    val Search: ImageVector by lazy {
        icon("search") {
            stroke {
                circle(11f, 11f, 6.6f)
                line(15.7f, 15.7f, 20.2f, 20.2f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        icon("plus") { stroke { line(12f, 5.4f, 12f, 18.6f); line(5.4f, 12f, 18.6f, 12f) } }
    }

    val Close: ImageVector by lazy {
        icon("close") { stroke { line(6.6f, 6.6f, 17.4f, 17.4f); line(17.4f, 6.6f, 6.6f, 17.4f) } }
    }

    /** 退格（记账键盘右上键，替代 ⌫ 字形）。 */
    val Backspace: ImageVector by lazy {
        icon("backspace") {
            stroke {
                moveTo(8.8f, 5.2f)
                lineTo(19.6f, 5.2f)
                lineTo(19.6f, 18.8f)
                lineTo(8.8f, 18.8f)
                lineTo(3.2f, 12f)
                close()
                line(13.6f, 9.6f, 17.2f, 14.4f)
                line(17.2f, 9.6f, 13.6f, 14.4f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("check") { stroke { line(5.4f, 12.6f, 9.8f, 17f); line(9.8f, 17f, 18.6f, 7.2f) } }
    }

    val Trash: ImageVector by lazy {
        icon("trash") {
            stroke {
                line(4.4f, 7f, 19.6f, 7f)
                line(9.4f, 7f, 9.4f, 4.6f)
                line(9.4f, 4.6f, 14.6f, 4.6f)
                line(14.6f, 4.6f, 14.6f, 7f)
                line(6.4f, 7f, 7.5f, 20f)
                line(7.5f, 20f, 16.5f, 20f)
                line(16.5f, 20f, 17.6f, 7f)
                line(10.3f, 10.4f, 10.7f, 17f)
                line(13.7f, 10.4f, 13.3f, 17f)
            }
        }
    }

    val Edit: ImageVector by lazy {
        icon("edit") {
            stroke {
                moveTo(4.6f, 19.4f)
                lineTo(5.1f, 15.4f)
                lineTo(15.9f, 4.6f)
                lineTo(19.4f, 8.1f)
                lineTo(8.6f, 18.9f)
                close()
                line(13.4f, 7.1f, 16.9f, 10.6f)
            }
        }
    }

    val Calendar: ImageVector by lazy {
        icon("calendar") {
            stroke {
                roundRect(4f, 5f, 20f, 19.6f, 2.6f)
                line(8.6f, 3.4f, 8.6f, 6.6f)
                line(15.4f, 3.4f, 15.4f, 6.6f)
                line(4f, 10.2f, 20f, 10.2f)
            }
        }
    }

    val Wallet: ImageVector by lazy {
        icon("wallet") {
            stroke {
                roundRect(3.4f, 5.6f, 20.6f, 18.6f, 2.8f)
                line(3.4f, 10f, 20.6f, 10f)
            }
            fill { circle(16.6f, 14.3f, 1.15f) }
        }
    }

    val Pie: ImageVector by lazy {
        icon("pie") {
            stroke {
                circle(12f, 12f, 8.2f)
                line(12f, 12f, 12f, 3.8f)
                line(12f, 12f, 20.2f, 12f)
            }
        }
    }

    val Home: ImageVector by lazy {
        icon("home") {
            stroke {
                moveTo(3.4f, 10.6f)
                lineTo(12f, 3.6f)
                lineTo(20.6f, 10.6f)
                lineTo(20.6f, 20.2f)
                lineTo(3.4f, 20.2f)
                close()
                line(9.4f, 20.2f, 9.4f, 14.2f)
                line(9.4f, 14.2f, 14.6f, 14.2f)
                line(14.6f, 14.2f, 14.6f, 20.2f)
            }
        }
    }

    val Person: ImageVector by lazy {
        icon("person") {
            stroke {
                circle(12f, 8.4f, 4.1f)
                moveTo(4.7f, 20.3f)
                arcTo(7.3f, 7.3f, 0f, false, true, 19.3f, 20.3f)
            }
        }
    }

    /** 分类 / 标签（分类管理入口）。 */
    val Tag: ImageVector by lazy {
        icon("tag") {
            stroke {
                moveTo(11.6f, 3.6f)
                lineTo(20.4f, 12.4f)
                lineTo(12.4f, 20.4f)
                lineTo(3.6f, 11.6f)
                lineTo(3.6f, 4.8f)
                close()
            }
            fill { circle(7.4f, 7.5f, 1.25f) }
        }
    }

    val Lock: ImageVector by lazy {
        icon("lock") {
            stroke {
                roundRect(4.6f, 10.4f, 19.4f, 20.4f, 2.8f)
                line(8.2f, 10.4f, 8.2f, 8f)
                arcTo(3.8f, 3.8f, 0f, false, true, 15.8f, 8f)
                line(15.8f, 8f, 15.8f, 10.4f)
            }
        }
    }

    val Info: ImageVector by lazy {
        icon("info") {
            stroke {
                circle(12f, 12f, 8.2f)
                line(12f, 11.2f, 12f, 16.6f)
            }
            fill { circle(12f, 7.9f, 1.0f) }
        }
    }

    val Database: ImageVector by lazy {
        icon("database") {
            stroke {
                moveTo(4.2f, 7f)
                arcTo(7.8f, 3.1f, 0f, false, false, 19.8f, 7f)
                arcTo(7.8f, 3.1f, 0f, false, true, 4.2f, 7f)
                close()
                line(4.2f, 7f, 4.2f, 17f)
                line(19.8f, 7f, 19.8f, 17f)
                moveTo(4.2f, 17f)
                arcTo(7.8f, 3.1f, 0f, false, false, 19.8f, 17f)
            }
        }
    }

    /** 转账（两个反向箭头，替代 ⇅ 字形）。 */
    val Transfer: ImageVector by lazy {
        icon("transfer") {
            stroke {
                line(8.2f, 20f, 8.2f, 4.8f)
                line(4.8f, 8.2f, 8.2f, 4.8f)
                line(8.2f, 4.8f, 11.6f, 8.2f)
                line(15.8f, 4f, 15.8f, 19.2f)
                line(12.4f, 15.8f, 15.8f, 19.2f)
                line(15.8f, 19.2f, 19.2f, 15.8f)
            }
        }
    }

    val MoreVert: ImageVector by lazy {
        icon("more_vert") {
            fill {
                circle(12f, 5.4f, 1.35f)
                circle(12f, 12f, 1.35f)
                circle(12f, 18.6f, 1.35f)
            }
        }
    }

    /** 眼睛（资产页"显示金额"）：上下两道浅弧合成的眼形 + 瞳孔。 */
    val Eye: ImageVector by lazy {
        icon("eye") {
            stroke {
                lens()
                circle(12f, 12f, 2.6f)
            }
        }
    }

    /** 闭眼（资产页"隐藏金额"）：同一个眼形加一道斜杠。 */
    val EyeOff: ImageVector by lazy {
        icon("eye_off") {
            stroke {
                lens()
                circle(12f, 12f, 2.6f)
                line(4.2f, 19.8f, 19.8f, 4.2f)
            }
        }
    }

    // —— 构建辅助 ——

    private const val STROKE = 1.9f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.stroke(block: PathBuilder.() -> Unit) =
        path(
            name = "stroke",
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )

    private fun ImageVector.Builder.fill(block: PathBuilder.() -> Unit) =
        path(
            name = "fill",
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
            pathBuilder = block,
        )

    private fun PathBuilder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
        moveTo(x1, y1)
        lineTo(x2, y2)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    /** 眼形（左右两段浅弧，上弧由左向右、下弧由右向左）。 */
    private fun PathBuilder.lens() {
        moveTo(2.6f, 12f)
        arcTo(11f, 8f, 0f, false, true, 21.4f, 12f)
        arcTo(11f, 8f, 0f, false, true, 2.6f, 12f)
        close()
    }

    private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        moveTo(left + radius, top)
        lineTo(right - radius, top)
        arcTo(radius, radius, 0f, false, true, right, top + radius)
        lineTo(right, bottom - radius)
        arcTo(radius, radius, 0f, false, true, right - radius, bottom)
        lineTo(left + radius, bottom)
        arcTo(radius, radius, 0f, false, true, left, bottom - radius)
        lineTo(left, top + radius)
        arcTo(radius, radius, 0f, false, true, left + radius, top)
        close()
    }

    private fun PathBuilder.chevronLeft() {
        moveTo(14.4f, 5.2f)
        lineTo(7.6f, 12f)
        lineTo(14.4f, 18.8f)
    }

    private fun PathBuilder.chevronRight() {
        moveTo(9.6f, 5.2f)
        lineTo(16.4f, 12f)
        lineTo(9.6f, 18.8f)
    }
}
