package dev.penned.app.data.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * [HttpFetcher] 的 OkHttp 实现（D058；4.12.0）。
 *
 * 两套超时（规格 M2 S2/S3）：清单 连接 10s / 读取 15s；下载 连接 15s / 读取 30s（大文件慢链路）。
 * 重定向跟随 OkHttp 默认（镜像站常见 302）。不做重试——候选地址列表本身就是回退机制。
 */
class OkHttpFetcher(
    connectTimeoutSeconds: Long = 10,
    readTimeoutSeconds: Long = 15,
    downloadConnectTimeoutSeconds: Long = 15,
    downloadReadTimeoutSeconds: Long = 30,
) : HttpFetcher {

    private val textClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(downloadConnectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(downloadReadTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun getText(url: String, userAgent: String): String? = withContext(Dispatchers.IO) {
        try {
            textClient.newCall(request(url, userAgent, resumeFrom = null)).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // 网络不可用 / 超时 / URL 非法：都当"这个地址失败"
        }
    }

    override suspend fun openStream(url: String, userAgent: String, resumeFrom: Long): HttpStream? =
        withContext(Dispatchers.IO) {
            try {
                val response = downloadClient.newCall(request(url, userAgent, resumeFrom)).execute()
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    response.close()
                    return@withContext null
                }
                OkHttpStream(
                    response = response,
                    body = body.byteStream(),
                    contentLength = body.contentLength(),
                    resumedFrom = if (response.code == 206) resumeFrom else 0L,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    private fun request(url: String, userAgent: String, resumeFrom: Long?): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .apply { if (resumeFrom != null && resumeFrom > 0) header("Range", "bytes=$resumeFrom-") }
            .build()

    private class OkHttpStream(
        private val response: Response,
        override val body: InputStream,
        override val contentLength: Long,
        override val resumedFrom: Long,
    ) : HttpStream {

        override fun close() {
            try {
                body.close()
            } finally {
                response.close()
            }
        }
    }
}
