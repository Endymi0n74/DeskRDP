package com.dualdesk.rustdesk

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Keeps the RustDesk passwords of the connection list sealed under an Android Keystore key.
 *
 * The key never leaves the keystore: it is generated on first use, non-exportable, and only used
 * by this application. Losing it (uninstall, restore onto another device) makes the stored
 * passwords unreadable, which is reported as "no password" rather than as a broken access — the
 * device id still opens the session, the password is simply asked again by RustDesk.
 *
 * API 21 and 22 have no `KeyGenParameterSpec`; there the value is stored as before and a warning
 * is logged, because a keystore key that cannot be created is worse than a clear record.
 */
object RustDeskSecretStore {

    private const val TAG = "DualDeskRustDesk"
    private const val ALIAS = "dualdesk.rustdesk.password.v1"
    private const val KEYSTORE = "AndroidKeyStore"

    @JvmStatic
    fun isSealed(value: String?): Boolean = RustDeskSecretCodec.isSealed(value)

    /** Seals a password for storage. Throws when the keystore refuses, so nothing is written in clear. */
    @JvmStatic
    fun seal(password: String?): String? {
        if (password.isNullOrEmpty() || isSealed(password)) return password
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w(TAG, "Android Keystore unavailable before API 23: RustDesk password stored unsealed.")
            return password
        }
        return RustDeskSecretCodec.seal(key(), password)
    }

    /** Opens a stored password; a value without the sealed prefix is an older clear record. */
    @JvmStatic
    fun open(stored: String?): String? {
        if (stored.isNullOrEmpty() || !isSealed(stored)) return stored
        return try {
            RustDeskSecretCodec.open(key(), stored)
        } catch (e: Exception) {
            Log.w(TAG, "Stored RustDesk password could not be opened: $e")
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(ALIAS, null)
        if (existing is KeyStore.SecretKeyEntry) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
