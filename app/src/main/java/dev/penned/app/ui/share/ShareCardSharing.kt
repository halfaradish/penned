package dev.penned.app.ui.share

import android.content.Context
import dev.penned.app.domain.ShareCardLayout
import dev.penned.app.ui.theme.ThemeAccent

/**
 * 三个入口共用的一步：**渲染 → 落 cacheDir → 拉起系统分享面板**（M3-16）。
 *
 * 入口页各自负责把数据算成 [ShareCardLayout.Plan]（纯函数），这里只做"画与分享"这一件事——
 * 三处各写一遍"写文件 + 拼 Intent"迟早漂移（Intent 组成测试也就钉不住一处）。
 */
object ShareCardSharing {

    /** 渲染并分享；返回落盘的文件（测试与走查要看它）。 */
    fun share(
        context: Context,
        plan: ShareCardLayout.Plan,
        name: String,
        accent: ThemeAccent,
        darkTheme: Boolean,
    ): java.io.File {
        val file = ShareCardRenderer.writeToCache(
            context = context,
            plan = plan,
            preview = ShareCardRenderer.paletteOf(accent, darkTheme),
            name = ShareCardRenderer.safeName(name),
        )
        ShareCardRenderer.share(context, file)
        return file
    }
}
