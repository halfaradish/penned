package dev.penned.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 安装链路（规格 M2 S5）：Intent 组成、"安装未知应用"引导、签名读不到即拒。
 *
 * Robolectric 读不到 FileProvider 的 `paths` 元数据（根全匹配不上，见 [UpdateInstaller] 注释），
 * 所以 content URI 的生成只在这里验配置口径（authority），**真机路径在 M2-8 闭环里点名验**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class UpdateInstallTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun apkFile(relative: String): File {
        val file = File(context.cacheDir, relative)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        return file
    }

    @Test
    fun `install intent is view plus archive mime plus read grant`() {
        val uri = Uri.parse("content://dev.penned.app.fileprovider/updates/penned-2.apk")

        val intent = UpdateInstaller.installIntent(uri)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals(uri, intent.data)
        assertTrue(
            "必须带读权限授予，否则系统安装器打不开",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    @Test
    fun `authority follows the application id`() {
        assertEquals("dev.penned.app.fileprovider", UpdateInstaller.authority(context))
    }

    @Test
    fun `unknown sources settings intent points at our package`() {
        val intent = UpdateInstaller.unknownSourcesSettingsIntent(context)

        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:dev.penned.app", intent.data.toString())
    }

    @Test
    fun `canInstall mirrors the platform answer`() {
        // 只断言"如实转达平台答案"——Robolectric 的 shadow 没有 setter（默认值会随版本变），
        // "未授权 → 引导设置页"的真机路径在 M2-8 模拟机上验
        assertEquals(
            context.packageManager.canRequestPackageInstalls(),
            UpdateInstaller.canInstall(context),
        )
    }

    @Test
    fun `signature check refuses a file that is not an apk`() {
        val bogus = apkFile("updates/bogus.apk")
        assertFalse("解析不出签名的文件必须判不一致", PackageSignatures.matchesInstalled(context, bogus))
    }
}
