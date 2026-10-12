package dev.penned.app.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.UpdateManifest
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 关于/更新页（规格 M2 S6 / UI-16 改版）：各状态版面 + 关键接线断言。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val REPO_URL = "github.com/halfaradish/penned"

    private val manifest = UpdateManifest(
        versionCode = 2,
        versionName = "0.2.0",
        minVersionCode = 1,
        changelog = listOf("新增：应用内更新", "修复：首页折叠时条目错位"),
        sha256 = "aa",
        size = 8_500_000L,
        urls = listOf("https://example.com/penned-0.2.0.apk"),
    )

    private fun content(
        state: UpdateUiState,
        needsUnknownSources: Boolean = false,
        onCheck: () -> Unit = {},
        onDownload: () -> Unit = {},
        onRetry: () -> Unit = {},
        onInstall: () -> Unit = {},
        onOpenRepo: () -> Unit = {},
    ) {
        composeRule.setContent {
            PennedTheme {
                AboutContent(
                    state = state,
                    versionName = "0.1.0",
                    versionCode = 1,
                    repoUrl = REPO_URL,
                    needsUnknownSources = needsUnknownSources,
                    onBack = {},
                    onCheck = onCheck,
                    onDownload = onDownload,
                    onCancelDownload = {},
                    onRetry = onRetry,
                    onInstall = onInstall,
                    onOpenRepo = onOpenRepo,
                    onOpenUnknownSourcesSettings = {},
                    onDismissUnknownSources = {},
                )
            }
        }
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureIdleAndAssertCheckWiring() {
        var checks = 0
        var repoOpens = 0
        content(UpdateUiState.Idle, onCheck = { checks++ }, onOpenRepo = { repoOpens++ })
        composeRule.onNodeWithText("一笔一记").assertIsDisplayed()
        composeRule.onNodeWithText("本地优先 · 无服务器").assertIsDisplayed()
        composeRule.onNodeWithText("当前版本").assertIsDisplayed()
        composeRule.onNodeWithText("0.1.0（1）").assertIsDisplayed()
        composeRule.onNodeWithText("未检查").assertIsDisplayed()
        composeRule.onNodeWithText("每天自动检查一次").assertIsDisplayed()
        composeRule.onNodeWithText("更新检查只访问静态文件，不携带任何标识。").assertIsDisplayed()
        // 整行就是「检查更新」的入口（不再是一个铺满的大按钮）
        composeRule.onNodeWithText("检查更新").performClick()
        assertEquals(1, checks)
        // 「项目主页」行：副标题显示去掉 https:// 前缀的仓库地址
        composeRule.onNodeWithText("项目主页").assertIsDisplayed()
        composeRule.onNodeWithText(REPO_URL.removePrefix("https://")).assertIsDisplayed()
        composeRule.onNodeWithText("项目主页").performClick()
        assertEquals(1, repoOpens)
        capture("ui16_about_idle")
    }

    @Test
    fun captureChecking() {
        content(UpdateUiState.Checking)
        composeRule.onNodeWithText("检查中…").assertIsDisplayed()
        capture("ui16_about_checking")
    }

    @Test
    fun captureUpToDate() {
        // 2026-10-10T21:30+08:00
        content(UpdateUiState.UpToDate(1_791_639_000_000L))
        composeRule.onNodeWithText("已是最新").assertIsDisplayed()
        composeRule.onNodeWithText("上次检查 10月10日 21:30").assertIsDisplayed()
        capture("ui16_about_uptodate")
    }

    @Test
    fun captureAvailableAndAssertDownloadWiring() {
        var downloads = 0
        content(UpdateUiState.Available(manifest, stronglyRecommended = false), onDownload = { downloads++ })
        composeRule.onNodeWithText("有新版本").assertIsDisplayed()
        composeRule.onNodeWithText("新版本 0.2.0").assertIsDisplayed()
        composeRule.onNodeWithText("· 新增：应用内更新").assertIsDisplayed()
        composeRule.onNodeWithText("安装包 8.1 MB").assertIsDisplayed()
        composeRule.onNodeWithText("下载更新").performClick()
        assertEquals(1, downloads)
        capture("ui16_about_available")
    }

    @Test
    fun captureAvailableStronglyRecommended() {
        content(UpdateUiState.Available(manifest, stronglyRecommended = true))
        composeRule.onNodeWithText("强烈建议更新").assertIsDisplayed()
        capture("ui16_about_available_strong")
    }

    @Test
    fun captureDownloading() {
        content(UpdateUiState.Downloading(downloaded = 3_570_000L, total = 8_500_000L))
        composeRule.onNodeWithText("正在下载 42%").assertIsDisplayed()
        composeRule.onNodeWithText("取消下载").assertIsDisplayed()
        capture("ui16_about_downloading")
    }

    @Test
    fun captureVerifying() {
        // 校验中不再开详情块：行上的「校验中…」就是全部信息（同一信息只留一处）
        content(UpdateUiState.Verifying)
        composeRule.onNodeWithText("校验中…").assertIsDisplayed()
        capture("ui16_about_verifying")
    }

    @Test
    fun captureReadyAndAssertInstallWiring() {
        var installs = 0
        content(UpdateUiState.Ready(File("/tmp/penned-2.apk")), onInstall = { installs++ })
        composeRule.onNodeWithText("已下载并校验通过").assertIsDisplayed()
        composeRule.onNodeWithText("去安装").performClick()
        assertEquals(1, installs)
        capture("ui16_about_ready")
    }

    @Test
    fun captureFailedAndAssertSingleRetryAction() {
        var retries = 0
        content(UpdateUiState.Failed("安装包校验失败，已删除"), onRetry = { retries++ })
        composeRule.onNodeWithText("未成功").assertIsDisplayed()
        composeRule.onNodeWithText("安装包校验失败，已删除").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
        // 失败态只留一个动作：「重新检查」与「重试」是同一个动作（retry() 没清单时就走重新检查），
        // 重新拉清单的入口是上面那行「检查更新」——别把两个按钮写回来
        composeRule.onNodeWithText("重新检查").assertDoesNotExist()
        capture("ui16_about_failed")
    }

    @Test
    fun captureUnknownSourcesDialog() {
        content(UpdateUiState.Ready(File("/tmp/penned-2.apk")), needsUnknownSources = true)
        composeRule.onNodeWithText("需要允许安装应用").assertIsDisplayed()
        composeRule.onNodeWithText("去设置").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
        capture("ui16_about_unknown_sources")
    }

    @Test
    fun captureDarkIdle() {
        composeRule.setContent {
            PennedTheme(darkTheme = true) {
                AboutContent(
                    state = UpdateUiState.Idle,
                    versionName = "0.1.0",
                    versionCode = 1,
                    repoUrl = REPO_URL,
                    needsUnknownSources = false,
                    onOpenRepo = {},
                    onBack = {},
                    onCheck = {},
                    onDownload = {},
                    onCancelDownload = {},
                    onRetry = {},
                    onInstall = {},
                    onOpenUnknownSourcesSettings = {},
                    onDismissUnknownSources = {},
                )
            }
        }
        capture("dark_about_idle")
    }

    @Test
    fun captureDarkAvailable() {
        composeRule.setContent {
            PennedTheme(darkTheme = true) {
                AboutContent(
                    state = UpdateUiState.Available(manifest, stronglyRecommended = true),
                    versionName = "0.1.0",
                    versionCode = 1,
                    repoUrl = REPO_URL,
                    needsUnknownSources = false,
                    onOpenRepo = {},
                    onBack = {},
                    onCheck = {},
                    onDownload = {},
                    onCancelDownload = {},
                    onRetry = {},
                    onInstall = {},
                    onOpenUnknownSourcesSettings = {},
                    onDismissUnknownSources = {},
                )
            }
        }
        capture("dark_about_available")
    }
}
