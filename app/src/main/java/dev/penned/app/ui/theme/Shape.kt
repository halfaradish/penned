package dev.penned.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角阶梯（docs/界面规范.md 第六节，D043）：四档 + 全圆，别再就地造新值。
 * - extraSmall 8：徽标、进度条端头
 * - small 12：键面、输入框、内块、按钮
 * - medium 16：**卡片**（含分段卡）
 * - large 20：顶栏内的浮层、内嵌面板
 * - extraLarge 24：抽屉顶、弹窗
 * 胶囊/chip/头像/FAB 走 `CircleShape` 或 50%。
 */
val PennedShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
