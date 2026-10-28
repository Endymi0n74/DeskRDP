package com.dualdesk.rustdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The sealing format, exercised without Android: [RustDeskSecretCodec] only uses `javax.crypto`,
 * the device key comes from the keystore (see [RustDeskSecretStore]).
 *
 * What matters here is that a sealed password opens back to the same text, that two seals differ
 * (so the database does not reveal that two accesses share a password), that a wrong key or an
 * altered record fails closed, and that records written before sealing existed still read.
 */
class RustDeskSecretCodecTest {

    private fun key(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()

    @Test
    fun `a sealed password opens back to the same text`() {
        val key = key()

        val sealed = RustDeskSecretCodec.seal(key, "correct horse battery staple")

        assertTrue(RustDeskSecretCodec.isSealed(sealed))
        assertFalse("the clear text must not survive in the record", sealed.contains("horse"))
        assertEquals("correct horse battery staple", RustDeskSecretCodec.open(key, sealed))
    }

    @Test
    fun `sealing twice gives different records`() {
        val key = key()

        val first = RustDeskSecretCodec.seal(key, "secret")
        val second = RustDeskSecretCodec.seal(key, "secret")

        assertNotEquals("a fresh IV must be used every time", first, second)
        assertEquals("secret", RustDeskSecretCodec.open(key, first))
        assertEquals("secret", RustDeskSecretCodec.open(key, second))
    }

    @Test
    fun `another key cannot open the record`() {
        val sealed = RustDeskSecretCodec.seal(key(), "secret")

        assertNull(RustDeskSecretCodec.open(key(), sealed))
    }

    @Test
    fun `an altered record fails closed instead of returning garbage`() {
        val key = key()
        val sealed = RustDeskSecretCodec.seal(key, "secret")
        val altered = sealed.dropLast(1) + if (sealed.last() == 'a') "b" else "a"

        assertNull(RustDeskSecretCodec.open(key, altered))
    }

    @Test
    fun `a record written before sealing is returned as is`() {
        assertFalse(RustDeskSecretCodec.isSealed("ancien-mot-de-passe"))
        assertEquals("ancien-mot-de-passe", RustDeskSecretCodec.open(key(), "ancien-mot-de-passe"))
    }
}
