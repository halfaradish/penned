package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** APK 签名一致性判定（规格 M2 S4）——纯函数，表驱动。 */
class ApkSignatureTest {

    private val a = byteArrayOf(1, 2, 3)
    private val b = byteArrayOf(4, 5, 6)
    private val aCopy = byteArrayOf(1, 2, 3)

    @Test
    fun `same signers table`() {
        fun expect(installed: List<ByteArray>, candidate: List<ByteArray>, same: Boolean, note: String) =
            assertEquals(note, same, ApkSignature.sameSigners(installed, candidate))

        expect(listOf(a), listOf(aCopy), true, "内容相同即一致（不靠对象同一性）")
        expect(listOf(a), listOf(a), true, "同一份证书")
        expect(listOf(a, b), listOf(b, a), true, "多签名：顺序无关")
        expect(listOf(a), listOf(b), false, "换了签名（换包）")
        expect(listOf(a), listOf(a, b), false, "数量不同")
        expect(listOf(a, b), listOf(a), false, "数量不同（反向）")
        expect(emptyList(), listOf(a), false, "读不到已装签名 → 拒")
        expect(listOf(a), emptyList(), false, "读不到待装签名（不是 APK）→ 拒")
        expect(emptyList(), emptyList(), false, "两边都读不到 → 拒")
    }

    @Test
    fun `prefix bytes do not count as same`() {
        assertEquals(false, ApkSignature.sameSigners(listOf(byteArrayOf(1, 2)), listOf(byteArrayOf(1, 2, 3))))
    }
}
