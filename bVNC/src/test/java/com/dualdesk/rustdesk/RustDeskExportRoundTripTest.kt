package com.dualdesk.rustdesk

import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le trajet complet, dans l'ordre où l'application le fait : un mot de passe scellé sur l'appareil
 * part dans un document d'export sous une phrase de passe, puis est rouvert à l'import comme le
 * ferait l'autre téléphone.
 *
 * La clé du Keystore est ici une clé AES ordinaire : seul le format du sceau compte pour ce trajet,
 * et [RustDeskSecretCodec] accepte la clé qu'on lui donne. Ce que ce test ne couvre pas — le vrai
 * Keystore, l'écriture d'un fichier, les dialogues — est consigné dans la mémoire du projet.
 */
class RustDeskExportRoundTripTest {

    private val deviceKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val passphrase = "phrase de transport"

    private fun exportDocument(sealedPassword: String): String =
        "<database><table table_name=\"CONNECTION_BEAN\">" +
            "<row _id=\"1\" NICKNAME=\"555000111\" CONNECTIONTYPE=\"6\" " +
            "ADDRESS=\"555000111\" PASSWORD=\"$sealedPassword\"></row>" +
            "</table></database>"

    private fun passwordOf(document: String): String =
        document.substringAfter("PASSWORD=\"").substringBefore("\"")

    @Test
    fun `a device seal comes out sealed under the passphrase and opens on the other side`() {
        val storedOnDevice = RustDeskSecretCodec.seal(deviceKey, "hunter2")
        val exporter = RustDeskExportSanitizer.PasswordExporter { stored ->
            val clear = RustDeskSecretCodec.open(deviceKey, stored)
            if (clear == null || clear.isEmpty()) null else RustDeskPortableSeal.seal(passphrase, clear)
        }

        val exported = RustDeskExportSanitizer.protectRustDeskPasswords(
            exportDocument(storedOnDevice), exporter)

        assertNotNull(exported)
        val carried = passwordOf(exported!!.document)
        assertEquals(1, exported.passwordsCarried)
        assertTrue("le sceau de l'appareil ne doit pas sortir", !exported.document.contains("ks1:"))
        assertTrue("le clair ne doit pas sortir", !exported.document.contains("hunter2"))
        assertTrue(RustDeskPortableSeal.isPortable(carried))

        // Côté autre appareil : la phrase rouvre, une autre ne rouvre pas.
        assertEquals("hunter2", RustDeskPortableSeal.open(passphrase, carried))
        assertNull(RustDeskPortableSeal.open("mauvaise phrase", carried))
    }

    @Test
    fun `without a passphrase nothing of the password leaves, not even a portable value`() {
        val storedOnDevice = RustDeskSecretCodec.seal(deviceKey, "hunter2")

        val exported = RustDeskExportSanitizer.blankRustDeskPasswords(exportDocument(storedOnDevice))

        assertNotNull(exported)
        assertEquals(0, exported!!.passwordsCarried)
        assertEquals(1, exported.passwordsRemoved)
        assertEquals("", passwordOf(exported.document))
        assertFalse(exported.document.contains("ks1:"))
        assertFalse(exported.document.contains("pw1:"))
    }

    @Test
    fun `a legacy clear password is also carried when a passphrase is given`() {
        val exporter = RustDeskExportSanitizer.PasswordExporter { stored ->
            RustDeskPortableSeal.seal(passphrase, stored)
        }

        val exported = RustDeskExportSanitizer.protectRustDeskPasswords(
            exportDocument("ancien-clair"), exporter)

        assertNotNull(exported)
        assertEquals("ancien-clair", RustDeskPortableSeal.open(passphrase, passwordOf(exported!!.document)))
        assertFalse(exported.document.contains("ancien-clair"))
    }
}
