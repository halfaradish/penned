package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `verify accepts correct pin and rejects wrong`() {
        val salt = PinHasher.newSalt()
        val stored = PinHasher.encode("2506", salt)
        assertTrue(PinHasher.verify("2506", stored))
        assertFalse(PinHasher.verify("2507", stored))
        assertFalse(PinHasher.verify("250", stored))
        assertFalse(PinHasher.verify("25066", stored))
    }

    @Test
    fun `stored format is salt-hash hex without plaintext`() {
        val stored = PinHasher.encode("1234", "aabbccdd")
        assertTrue(stored.startsWith("aabbccdd:"))
        val hashPart = stored.substringAfter(':')
        assertEquals(64, hashPart.length) // SHA-256 hex
        assertFalse(stored.contains("1234:"))
        assertFalse(hashPart.contains("1234"))
    }

    @Test
    fun `same pin different salt yields different hash`() {
        val h1 = PinHasher.hash("1234", "salt-one-")
        val h2 = PinHasher.hash("1234", "salt-two-")
        assertNotEquals(h1, h2)
    }

    @Test
    fun `malformed stored strings always fail`() {
        assertFalse(PinHasher.verify("1234", ""))
        assertFalse(PinHasher.verify("1234", "nosalt"))
        assertFalse(PinHasher.verify("1234", ":nohash"))
    }

    @Test
    fun `new salt is 16 hex chars and unique`() {
        val s1 = PinHasher.newSalt()
        val s2 = PinHasher.newSalt()
        assertEquals(16, s1.length)
        assertNotEquals(s1, s2)
    }
}
