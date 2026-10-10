package dev.penned.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * [HttpFetcher] 的 OkHttp 实现（D058；4.12.0）。
 *
 * 超时按规格 M2 S2：连接 10s / 读取 15s。重定向跟随 OkHttp 默认（镜像站常见 302）。
 * 不做重试——候选地址列表本身就是回退机制。
 */
class OkHttpFetcher(
    connectTimeoutSeconds: Long = 10,
    readTimeoutSeconds: Long = 15,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build(),
) : HttpFetcher {

    override suspend fun getText(url: String, userAgent: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (_: Exception) {
            null // 网络不可用 / 超时 / URL 非法：都当"这个地址失败"
        }
    }
}
