package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 更新包摘要（规格 M2 S4）：已知向量 + 流式与整包一致 + 大小写容忍。 */
class Sha256Test {

    @Test
    fun `known vectors`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.ofBytes(ByteArray(0)),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.ofBytes("abc".toByteArray()),
        )
    }

    @Test
    fun `streaming file digest equals in-memory digest for multi-chunk file`() {
        // 200KB > 64KB 缓冲，跨多块读；文件与内存两条路径必须一致
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val file = File.createTempFile("penned-sha", ".bin")
        try {
            file.writeBytes(bytes)
            assertEquals(Sha256.ofBytes(bytes), Sha256.ofFile(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `matches ignores case`() {
        assertTrue(Sha256.matches("ABCDEF", "abcdef"))
        assertTrue(Sha256.matches("abcdef", "ABCDEF"))
        assertFalse(Sha256.matches("abcdef", "abcde0"))
        assertFalse(Sha256.matches("", "abc"))
    }
}
