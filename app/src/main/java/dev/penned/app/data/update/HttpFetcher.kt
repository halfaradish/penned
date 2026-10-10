package dev.penned.app.data.update

import java.io.Closeable
import java.io.InputStream

/**
 * 更新检查/下载的最小 HTTP 抽象（规格 M2 S2/S3）。
 *
 * 抽成接口是为了可测：生产走 [OkHttpFetcher]，测试用本地假服务（[FakeHttpServer]）或直接给 fake。
 * **隐私口径**：请求只打静态文件，不带任何查询参数与设备/用户标识（规格 M2 S2）。
 */
interface HttpFetcher {

    /** GET 文本；网络失败、超时、非 2xx 一律返回 null（由调用方走下一个候选地址）。 */
    suspend fun getText(url: String, userAgent: String): String?

    /**
     * 发起下载（可带 `Range` 续传），返回可流式读的响应；非 2xx 或网络失败返回 null。
     *
     * [resumeFrom] > 0 时实现要带 `Range: bytes=<resumeFrom>-`；服务端忽略 Range 回了 200 也算成功，
     * 由 [HttpStream.resumedFrom] = 0 告知调用方"这份是从头开始的"。
     */
    suspend fun openStream(url: String, userAgent: String, resumeFrom: Long): HttpStream?
}

/** 一次流式响应的句柄（调用方负责消费与关闭）。 */
interface HttpStream : Closeable {

    /** 服务端实际从哪个偏移开始给：请求了 Range 且回 206 → 等于请求值；否则 0（从头）。 */
    val resumedFrom: Long

    /** 本次响应体长度（续传时是剩余长度；未知为 -1）。 */
    val contentLength: Long

    val body: InputStream
}
