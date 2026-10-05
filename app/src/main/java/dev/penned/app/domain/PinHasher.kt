package dev.penned.app.domain

import java.security.MessageDigest

/**
 * PIN 哈希：SHA-256(salt + pin)，存 "salt:hash"（十六进制）。不落明文（specs/M1.md 第二批）。
 * salt 为安装期随机值，随哈希一起存，改动 PIN 时 salt 不变。
 */
object PinHasher {

    const val PIN_LENGTH = 4

    /** 生成随机 salt（8 字节 hex）。 */
    fun newSalt(): String = java.security.SecureRandom().run {
        val bytes = ByteArray(8)
        nextBytes(bytes)
        bytes.toHex()
    }

    fun hash(pin: String, salt: String): String =
        sha256(salt + pin).toHex()

    /** 组装存储串 "salt:hash"。 */
    fun encode(pin: String, salt: String): String = "$salt:${hash(pin, salt)}"

    /** 校验：stored 形如 "salt:hash"。 */
    fun verify(pin: String, stored: String): Boolean {
        val salt = stored.substringBefore(':', "")
        val expected = stored.substringAfter(':', "")
        if (salt.isEmpty() || expected.isEmpty()) return false
        // 常数时间比较，避免时序侧信道
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
    }

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
