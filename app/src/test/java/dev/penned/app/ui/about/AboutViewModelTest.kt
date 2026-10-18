package dev.penned.app.ui.about

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.BuildConfig
import dev.penned.app.PennedApplication
import dev.penned.app.data.update.FakeHttpServer
import dev.penned.app.data.update.OkHttpFetcher
import dev.penned.app.data.update.UpdateInstaller
import dev.penned.app.domain.Sha256
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * 关于/更新页的状态机（规格 M2 S2~S5）：检查 → 下载 → 校验 → 安装 的串流程 + 每日节流 + 只提示一次。
 *
 * 走**真的** OkHttp + [FakeHttpServer]（不 mock 网络栈），DataStore 也是真的；只有签名校验与
 * "安装未知应用"权限注入/取 Robolectric 默认值（真机路径在 M2-8 模拟机闭环里验）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class AboutViewModelTest {

    private val server = FakeHttpServer()
    private val app: PennedApplication = ApplicationProvider.getApplicationContext()
    private val context: Context = app

    private val apkBytes = ByteArray(50_000) { (it % 251).toByte() }
    private val apkPath = "/penned-3.apk"

    private fun manifestJson(versionCode: Int = 3, minVersionCode: Int = 1) = """
        {"versionCode": $versionCode, "versionName": "0.$versionCode.0", "minVersionCode": $minVersionCode,
         "changelog": ["新增：应用内更新"], "sha256": "${Sha256.ofBytes(apkBytes)}", "size": ${apkBytes.size},
         "urls": ["${server.url(apkPath)}"]}
    """.trimIndent()

    /** 预发布清单（D114）：版本名带 "-"、channel = prerelease。 */
    private fun betaManifestJson(versionCode: Int = 4) = """
        {"versionCode": $versionCode, "versionName": "0.$versionCode.0-beta", "channel": "prerelease",
         "minVersionCode": 1, "changelog": ["预发布"], "sha256": "${Sha256.ofBytes(apkBytes)}",
         "size": ${apkBytes.size}, "urls": ["${server.url(apkPath)}"]}
    """.trimIndent()

    private fun viewModel(verifier: (File) -> Boolean = { true }) =
        AboutViewModel(
            app,
            OkHttpFetcher(),
            verifier,
            stableManifestUrls = listOf(server.url("/update.json")),
            prereleaseManifestUrls = listOf(server.url("/update-prerelease.json")),
        )

    /** 每个测试先清掉通道决定：Robolectric 的 DataStore 跨方法复用，不清会串（D114）。 */
    @Before
    fun resetChannel() = runBlocking {
        app.settings.setUpdateChannel(null, explicit = false)
    }

    /** 推进主线程队列直到条件成立（IO 在别的线程上跑，所以要轮询 + idle）。 */
    private fun waitFor(note: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        fail("等待超时：$note")
    }

    private fun File.targetApk() = File(app.cacheDir, "updates/penned-3.apk")

    /**
     * 每个测试先复位节流键。
     *
     * Robolectric 里 `preferencesDataStore` 的实例是**静态单例**（按文件名缓存），同一个测试类里各方法
     * 共用一份 DataStore 文件——不清就会串（实测：上个方法写的 lastCheckAt 让下个方法的自动检查被节流）。
     */
    private fun resetThrottle() = runBlocking {
        app.settings.setLastUpdateCheckAt(0L)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `manual check reaches available and records the check time`() {
        resetThrottle()
        server.on("/update.json", body = manifestJson())
        server.start()
        val vm = viewModel()

        vm.checkNow()
        waitFor("进入 Available") { vm.state.value is UpdateUiState.Available }

        val available = vm.state.value as UpdateUiState.Available
        assertEquals(3, available.manifest.versionCode)
        // 本机 versionCode = 1，minVersionCode = 1 → 不升级文案（只做分级，从不强制，D059）
        assertFalse(available.stronglyRecommended)
        assertTrue(
            "成功检查后要把节流时间刷成当前时刻",
            runBlocking { app.settings.lastUpdateCheckAt.first() }!! > 0L,
        )
    }

    @Test
    fun `manual check failure lands in failed state`() {
        resetThrottle()
        server.start() // 没有注册 /update.json → 404
        val vm = viewModel()

        vm.checkNow()
        waitFor("进入 Failed") { vm.state.value is UpdateUiState.Failed }

        assertEquals("更新清单下载失败", (vm.state.value as UpdateUiState.Failed).reason)
        assertEquals(
            "失败不该刷新节流时间（否则一天内都不会再试）",
            0L,
            runBlocking { app.settings.lastUpdateCheckAt.first() },
        )
    }

    @Test
    fun `download verifies digest and signature then reaches ready`() {
        server.on("/update.json", body = manifestJson())
        server.onFile(apkPath, apkBytes)
        server.start()
        val vm = viewModel(verifier = { true })

        vm.checkNow()
        waitFor("进入 Available") { vm.state.value is UpdateUiState.Available }
        vm.download()
        waitFor("进入 Ready") { vm.state.value is UpdateUiState.Ready }

        val file = (vm.state.value as UpdateUiState.Ready).file
        assertEquals("落盘内容必须与清单里的摘要一致", apkBytes.toList(), file.readBytes().toList())
        assertEquals("落在 cacheDir/updates/ 下（FileProvider 只暴露这里）", file.targetApk().absolutePath, file.absolutePath)
    }

    @Test
    fun `signature mismatch rejects and deletes the package`() {
        server.on("/update.json", body = manifestJson())
        server.onFile(apkPath, apkBytes)
        server.start()
        val vm = viewModel(verifier = { false })

        vm.checkNow()
        waitFor("进入 Available") { vm.state.value is UpdateUiState.Available }
        vm.download()
        waitFor("进入 Failed") { vm.state.value is UpdateUiState.Failed }

        assertEquals("安装包签名与当前版本不一致，已删除", (vm.state.value as UpdateUiState.Failed).reason)
        assertFalse("签名不符的包必须删掉", File(app.cacheDir, "updates/penned-3.apk").exists())
    }

    @Test
    fun `auto check runs once a day and notifies once per version`() {
        resetThrottle()
        // 用一个别的测试不用的 versionCode：DataStore 单例跨方法复用，"已提示过"的键没法清，
        // 换个新版本号等价于"还没提示过"
        server.on("/update.json", body = manifestJson(versionCode = 4))
        server.start()
        val vm = viewModel()

        // 首次：要检查，且这个版本还没提示过 → 返回提示
        val notice = runBlocking { vm.autoCheckOnLaunch() }
        assertNotNull("首次自动检查应当返回提示", notice)
        assertEquals(4, notice!!.manifest.versionCode)

        // 同一天再启动：节流挡住（不再联网，也不提示）
        val requestsAfterFirst = server.requests.size
        assertNull(runBlocking { vm.autoCheckOnLaunch() })
        assertEquals("节流期内不该再发请求", requestsAfterFirst, server.requests.size)

        // 把节流时间拨回去（模拟第二天），但这个版本已经提示过了 → 检查照跑、提示不再弹
        runBlocking { app.settings.setLastUpdateCheckAt(0L) }
        assertNull("同一个版本只自动提示一次", runBlocking { vm.autoCheckOnLaunch() })
        assertTrue("节流过了要真的再查一次", server.requests.size > requestsAfterFirst)
    }

    @Test
    fun `auto check ignores the retired switch and always runs`() {
        resetThrottle()
        server.on("/update.json", body = manifestJson(versionCode = 5))
        server.start()
        val vm = viewModel()
        // 开关已下线（D064）：存储里就算留着旧版关掉的 false，自动检查也照跑
        runBlocking { app.settings.setAutoUpdateCheck(false) }

        assertNotNull("自动检查恒为开", runBlocking { vm.autoCheckOnLaunch() })
        assertTrue("开关下线后不该被存储里的旧值挡住", server.requests.isNotEmpty())

        runBlocking { app.settings.setAutoUpdateCheck(true) }
    }

    @Test
    fun `install asks for unknown sources permission when not allowed`() {
        server.on("/update.json", body = manifestJson())
        server.onFile(apkPath, apkBytes)
        server.start()
        val vm = viewModel(verifier = { true })

        vm.checkNow()
        waitFor("进入 Available") { vm.state.value is UpdateUiState.Available }
        vm.download()
        waitFor("进入 Ready") { vm.state.value is UpdateUiState.Ready }

        // Robolectric 的 PackageManager 报"未授权" → 应当弹引导而不是直接装（规格 M2 S5）
        assertFalse(UpdateInstaller.canInstall(context))
        vm.install(context)
        assertTrue("未授权时要弹引导", vm.needsUnknownSources.value)

        vm.dismissUnknownSources()
        assertFalse("引导必须可取消（D059）", vm.needsUnknownSources.value)
    }

    @Test
    fun `stored prerelease channel keeps reading beta manifest on a stable build`() {
        resetThrottle()
        // 模拟「0.2.0-beta.1 升到 0.2.0 正式版」：安装包是稳定版，但订阅还记着 prerelease
        runBlocking { app.settings.setUpdateChannel("prerelease", explicit = false) }
        server.on("/update.json", body = manifestJson(versionCode = 2))
        server.on("/update-prerelease.json", body = betaManifestJson(versionCode = 4))
        server.start()
        val vm = viewModel()

        vm.checkNow()
        waitFor("进入 Available") { vm.state.value is UpdateUiState.Available }

        assertEquals(
            "beta 订阅者升正式版后仍要看到预发布线",
            "0.4.0-beta",
            (vm.state.value as UpdateUiState.Available).manifest.versionName,
        )
        assertTrue("要读预发布清单", server.requests.any { it.path == "/update-prerelease.json" })
        assertFalse("不应回退到正式清单", server.requests.any { it.path == "/update.json" })
        assertTrue("关于页开关要显示为开", vm.prereleaseEnabled.value)
    }

    @Test
    fun `stable subscriber rejects a beta manifest at the stable url`() {
        resetThrottle()
        server.on("/update.json", body = betaManifestJson(versionCode = 4))
        server.start()
        val vm = viewModel()

        vm.checkNow()
        waitFor("进入 Failed") { vm.state.value is UpdateUiState.Failed }

        assertEquals(
            "正式版绝不能把 beta 当更新",
            "更新清单通道不符",
            (vm.state.value as UpdateUiState.Failed).reason,
        )
    }

    @Test
    fun `turning prerelease on writes explicit and then checks the beta url`() {
        resetThrottle()
        server.on("/update.json", body = manifestJson(versionCode = 2))
        server.on("/update-prerelease.json", body = betaManifestJson(versionCode = 4))
        server.start()
        val vm = viewModel()

        vm.setPrereleaseEnabled(true)
        waitFor("通道落盘") {
            runBlocking { app.settings.updateChannel.first() } == "prerelease"
        }
        assertTrue("用户显式选择要落盘", runBlocking { app.settings.updateChannelExplicit.first() })
        waitFor("开关显示为开") { vm.prereleaseEnabled.value }

        vm.checkNow()
        waitFor("进入 Available") { vm.state.value is UpdateUiState.Available }
        assertEquals(
            "0.4.0-beta",
            (vm.state.value as UpdateUiState.Available).manifest.versionName,
        )
        assertTrue(server.requests.any { it.path == "/update-prerelease.json" })
    }
}
