package dev.penned.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * token 唯一出处护栏（D044 / 界面规范第十二节第 1 条）：
 * 扫 `ui/` 源码，把"就地写死的颜色、圆角、间距、图标尺寸"挡在门外——
 * 这些东西一旦各写各的，视觉一致性就靠人记，迟早漂移（AR 轮之前有 30 个文件里散着硬编码 dp）。
 *
 * 规则（带名字的 `private val ChartHeight = 96.dp` 这类**具名常量**不算违规，
 * 规则只抓"调用点里直接写字面量"）：
 * 1. `Color(0x…)` 只允许出现在 `ui/theme/`；
 * 2. 间距/尺寸/圆角类调用（padding/PaddingValues/size/width/height/heightIn/spacedBy/RoundedCornerShape）
 *    里的字面量 dp 必须 ≤ [MAX_LITERAL_DP]（0/1/2/3/4dp 是"发丝线、微调"这类无意义值），
 *    更大的值一律走 `PennedSpacing` / `PennedDimens` / `MaterialTheme.shapes`；
 * 3. 确实需要就地写的地方加 `token-ok` 注释放行（并在注释里写清为什么）。
 */
class TokenLiteralGuardTest {

    private val uiRoot = File("src/main/java/dev/penned/app/ui")
    private val themeDir = File(uiRoot, "theme")

    private val callPattern = Regex(
        """(padding|PaddingValues|paddingFromBaseline|size|sizeIn|width|height|heightIn|widthIn|""" +
            """spacedBy|RoundedCornerShape|offset)\s*\(""",
    )
    private val dpPattern = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\.dp""")

    private fun ktFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `颜色字面量只能出现在 theme`() {
        val offenders = mutableListOf<String>()
        ktFiles(uiRoot).filterNot { it.absolutePath.startsWith(themeDir.absolutePath) }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (line.contains("Color(0x") && !line.contains("token-ok")) {
                    offenders += "${file.relativeTo(uiRoot).path}:${index + 1}  ${line.trim()}"
                }
            }
        }
        assertTrue(
            "颜色字面量必须收进 ui/theme（走 token）：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `间距尺寸圆角不允许就地写大字面量`() {
        val offenders = mutableListOf<String>()
        ktFiles(uiRoot).filterNot { it.absolutePath.startsWith(themeDir.absolutePath) }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (line.trimStart().startsWith("//") || line.contains("token-ok")) return@forEachIndexed
                if (!callPattern.containsMatchIn(line)) return@forEachIndexed
                val tooBig = dpPattern.findAll(line)
                    .map { it.groupValues[1].toDouble() }
                    .filter { it > MAX_LITERAL_DP }
                    .toList()
                if (tooBig.isNotEmpty()) {
                    offenders += "${file.relativeTo(uiRoot).path}:${index + 1}  ${line.trim()}"
                }
            }
        }
        assertTrue(
            "间距/尺寸/圆角请走 PennedSpacing / PennedDimens / MaterialTheme.shapes" +
                "（确有必要的就地值加 token-ok 注释说明）：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private companion object {
        const val MAX_LITERAL_DP = 4.0
    }
}
