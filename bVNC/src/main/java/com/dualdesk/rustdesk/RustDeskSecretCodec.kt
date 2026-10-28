package com.dualdesk.rustdesk

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals a RustDesk password so it is never written in clear in the connection database.
 *
 * Why here and not in the database: the shared `password` column is only encrypted when the
 * application's master password is enabled (SQLCipher is keyed by it), so on a default install
 * every saved password sits in clear in `VncDatabase` — and that file is readable by a rooted
 * device or a backup. Sealing per value removes the dependency on that setting.
 *
 * The sealed shape is `ks1:<iv hex>:<ciphertext hex>`: AES-GCM with a fresh 12-byte IV, which is
 * both authenticated and non-deterministic (sealing the same password twice gives different
 * bytes). A value without the `ks1:` prefix is an older, clear-text record and is returned as is,
 * so existing accesses keep working until they are saved again.
 *
 * Hexadecimal rather than base64 on purpose: `android.util.Base64` cannot be used by the JVM
 * tests, and `java.util.Base64` only exists from API 26 while this application supports API 21.
 * A password is short enough that the size does not matter.
 */
object RustDeskSecretCodec {

    const val PREFIX = "ks1:"

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val SEPARATOR = ':'

    fun isSealed(value: String?): Boolean = value?.startsWith(PREFIX) == true

    fun seal(key: SecretKey, password: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val cipherText = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return PREFIX + hex(cipher.iv) + SEPARATOR + hex(cipherText)
    }

    /** Null when the value is sealed but cannot be opened (key lost, record altered). */
    fun open(key: SecretKey, stored: String): String? {
        if (!isSealed(stored)) return stored
        val body = stored.substring(PREFIX.length)
        val separator = body.indexOf(SEPARATOR)
        if (separator <= 0 || separator == body.length - 1) return null
        return try {
            val iv = unhex(body.substring(0, separator))
            val cipherText = unhex(body.substring(separator + 1))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun hex(bytes: ByteArray): String {
        val digits = CharArray(bytes.size * 2)
        val alphabet = "0123456789abcdef"
        for (i in bytes.indices) {
            val value = bytes[i].toInt() and 0xFF
            digits[i * 2] = alphabet[value ushr 4]
            digits[i * 2 + 1] = alphabet[value and 0x0F]
        }
        return String(digits)
    }

    private fun unhex(text: String): ByteArray {
        require(text.length % 2 == 0) { "odd-length hexadecimal" }
        val bytes = ByteArray(text.length / 2)
        for (i in bytes.indices) {
            val high = Character.digit(text[i * 2], 16)
            val low = Character.digit(text[i * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "not hexadecimal" }
            bytes[i] = ((high shl 4) or low).toByte()
        }
        return bytes
    }
}
