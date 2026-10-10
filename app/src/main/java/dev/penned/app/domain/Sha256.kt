package dev.penned.app.domain

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256（小写十六进制）——更新包摘要校验（规格 M2 S4）。
 *
 * 一律流式：更新包 ~8MB，不整包进内存；断点续传之后必须对**整文件**重算（不能把分段摘要拼起来）。
 */
object Sha256 {

    fun ofBytes(bytes: ByteArray): String = ofStream(bytes.inputStream())

    fun ofFile(file: File): String = file.inputStream().use { ofStream(it) }

    fun ofStream(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** 摘要比对忽略大小写（清单里可能写成大写十六进制）。 */
    fun matches(expected: String, actual: String): Boolean =
        expected.equals(actual, ignoreCase = true)
}
