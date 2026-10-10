package dev.penned.app.data.update

/**
 * 更新检查/下载的最小 HTTP 抽象（规格 M2 S2）。
 *
 * 抽成接口是为了可测：生产走 [OkHttpFetcher]，测试用本地假服务（JDK 自带 `com.sun.net.httpserver`）
 * 或直接给 fake。**隐私口径**：请求只打静态文件，不带任何查询参数与设备/用户标识（规格 M2 S2）。
 */
interface HttpFetcher {

    /** GET 文本；网络失败、超时、非 2xx 一律返回 null（由调用方走下一个候选地址）。 */
    suspend fun getText(url: String, userAgent: String): String?
}
