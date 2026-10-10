package dev.penned.app.data.update

import dev.penned.app.domain.Sha256
import dev.penned.app.domain.UpdateManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/** 一次下载的结果（规格 M2 S3/S4）。 */
sealed interface DownloadResult {

    /** 下完且摘要校验通过，可以进安装流程。 */
    data class Success(val file: File) : DownloadResult

    /** 没下完（网络断/所有地址都失败）：**已下的部分留着**，重试即续传。 */
    data class Incomplete(val downloaded: Long, val total: Long, val reason: String) : DownloadResult

    /** 下完了但校验不过：文件已删除，原因给用户看。 */
    data class Rejected(val reason: String) : DownloadResult
}

/**
 * 更新包下载器（规格 M2 S3/S4）：应用内 OkHttp 流式 + `Range` 断点续传 + 进度 + 摘要校验。
 *
 * 不碰 Android API（目标文件由调用方给，通常是 `cacheDir/updates/penned-<versionCode>.apk`）——
 * 纯 Kotlin，配 [FakeHttpServer] 就能把"断线续传""服务端不支持 Range""摘要不符"都演出来。
 */
class UpdateDownloader(
    private val http: HttpFetcher,
    private val userAgent: String,
) {

    suspend fun download(
        manifest: UpdateManifest,
        targetFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): DownloadResult = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()

        // 已经下满（上次下完但没装）：只校验，不再联网
        if (targetFile.exists() && targetFile.length() == manifest.size) {
            return@withContext verify(manifest, targetFile)
        }

        var failure = "下载失败"
        for (url in manifest.urls) {
            val resumeFrom = if (targetFile.exists()) targetFile.length() else 0L
            val stream = http.openStream(url, userAgent, resumeFrom)
            if (stream == null) {
                failure = "下载地址不可用"
                continue
            }

            // 服务端认了续传（206 且偏移对得上）→ 追加写；否则从头覆盖写（别把两段拼坏）
            val append = resumeFrom > 0 && stream.resumedFrom == resumeFrom
            try {
                stream.use { s ->
                    FileOutputStream(targetFile, append).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var written = s.resumedFrom
                        onProgress(written, manifest.size)
                        while (true) {
                            coroutineContext.ensureActive() // 用户取消：立刻停（规格 M2 S3）
                            val read = s.body.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            written += read
                            onProgress(written, manifest.size)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failure = "下载中断"
                // 已下的部分留着，下次从断点续
            }

            if (targetFile.length() == manifest.size) {
                return@withContext verify(manifest, targetFile)
            }
            failure = "下载未完成"
        }

        DownloadResult.Incomplete(
            downloaded = if (targetFile.exists()) targetFile.length() else 0L,
            total = manifest.size,
            reason = failure,
        )
    }

    /** 摘要校验（规格 M2 S4）：不符就删掉，别让坏包有机会进安装流程。 */
    private fun verify(manifest: UpdateManifest, file: File): DownloadResult {
        val actual = Sha256.ofFile(file)
        if (!Sha256.matches(manifest.sha256, actual)) {
            file.delete()
            return DownloadResult.Rejected("安装包校验失败，已删除")
        }
        return DownloadResult.Success(file)
    }
}
