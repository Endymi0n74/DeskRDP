package com.dualdesk.rustdesk

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals a RustDesk password under a passphrase, so an export can be restored on another device.
 *
 * The device seal ([RustDeskSecretCodec]) cannot travel: its key never leaves the keystore, and a
 * reinstall destroys it. This one is built from what the user types, which is the only secret that
 * survives a move between devices — and the only one they can lose, hence the passphrase is asked
 * at export and again at import, never stored.
 *
 * Shape: `pw1:<iterations>:<salt hex>:<iv hex>:<ciphertext hex>`. AES-GCM with a fresh 12-byte IV,
 * key derived from the passphrase by PBKDF2 with a fresh 16-byte salt. The iteration count travels
 * in the value so a file sealed by an older version stays readable after the cost is raised.
 *
 * Why PBKDF2 with HMAC-SHA1 and not SHA-256: `PBKDF2WithHmacSHA256` only exists from API 26 and
 * this application supports API 21. HMAC-SHA1 is not weakened by the collision attacks on plain
 * SHA-1 — a key derivation only needs the keyed hash to behave like a PRF.
 *
 * A wrong passphrase fails in GCM's authentication tag, which is why [open] returns null instead of
 * a different clear text: there is no way to silently decrypt to garbage.
 */
object RustDeskPortableSeal {

    const val PREFIX = "pw1:"

    /** 120 000 rounds: about a second on the oldest phone this has to run on, costly to attack. */
    const val DEFAULT_ITERATIONS = 120_000

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val SEPARATOR = ':'
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"

    @JvmStatic
    fun isPortable(value: String?): Boolean = value?.startsWith(PREFIX) == true

    /** Seals `password` under `passphrase`; the result is what the export writes. */
    @JvmStatic
    fun seal(passphrase: String, password: String): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, derive(passphrase, salt, DEFAULT_ITERATIONS))
        val cipherText = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return PREFIX +
            DEFAULT_ITERATIONS.toString() + SEPARATOR +
            hex(salt) + SEPARATOR +
            hex(cipher.iv) + SEPARATOR +
            hex(cipherText)
    }

    /** The clear password, or null when the passphrase is wrong or the value is not portable. */
    @JvmStatic
    fun open(passphrase: String, stored: String): String? {
        if (!isPortable(stored)) {
            return null
        }
        val parts = stored.substring(PREFIX.length).split(SEPARATOR)
        if (parts.size != 4) {
            return null
        }
        return try {
            val iterations = parts[0].toInt()
            if (iterations <= 0) {
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                derive(passphrase, unhex(parts[1]), iterations),
                GCMParameterSpec(TAG_BITS, unhex(parts[2])),
            )
            String(cipher.doFinal(unhex(parts[3])), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun derive(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        val key = factory.generateSecret(spec)
        return SecretKeySpec(key.encoded, "AES")
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
