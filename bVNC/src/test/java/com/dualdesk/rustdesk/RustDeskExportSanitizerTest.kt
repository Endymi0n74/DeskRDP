package com.dualdesk.rustdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercice du retrait des mots de passe RustDesk de l'export, sur des documents de la forme réelle
 * (une ligne par enregistrement, les colonnes en attributs) — sans appareil ni base.
 */
class RustDeskExportSanitizerTest {

    private fun document(vararg rows: String): String =
        "<?xml version='1.0' encoding='UTF-8' ?><database>" +
            "<table table_name=\"CONNECTION_BEAN\">" + rows.joinToString("") + "</table>" +
            "<table table_name=\"MetaList\"><row _id=\"1\" PRIVATE_DATA=\"DEFAULT\"></row></table>" +
            "</database>"

    private fun rustDeskRow(id: String, password: String): String =
        "<row _id=\"$id\" NICKNAME=\"555000111\" CONNECTIONTYPE=\"6\" ADDRESS=\"555000111\" " +
            "PASSWORD=\"$password\"></row>"

    private fun rdpRow(id: String, password: String): String =
        "<row _id=\"$id\" NICKNAME=\"Poste\" CONNECTIONTYPE=\"8\" ADDRESS=\"192.0.2.10:3389\" " +
            "PASSWORD=\"$password\"></row>"

    @Test
    fun `a sealed rustdesk password is left out and an rdp password is kept`() {
        val source = document(rustDeskRow("1", "ks1:00112233445566778899aabb:deadbeef"), rdpRow("2", "s3cret"))

        val result = RustDeskExportSanitizer.blankRustDeskPasswords(source)

        assertNotNull(result)
        assertEquals(1, result!!.passwordsRemoved)
        assertTrue("le champ est vidé, pas supprimé", result.document.contains("PASSWORD=\"\""))
        assertTrue("le mot de passe RDP reste dans l'export", result.document.contains("PASSWORD=\"s3cret\""))
        assertTrue("le sceau est parti", !result.document.contains("ks1:"))
    }

    @Test
    fun `an older clear rustdesk password is left out too`() {
        val source = document(rustDeskRow("1", "hunter2"))

        val result = RustDeskExportSanitizer.blankRustDeskPasswords(source)

        assertNotNull(result)
        assertEquals(1, result!!.passwordsRemoved)
        assertTrue(!result.document.contains("hunter2"))
        assertTrue(result.document.contains("CONNECTIONTYPE=\"6\""))
    }

    @Test
    fun `every rustdesk access is cleaned and the rest of the row is kept`() {
        val source = document(
            rustDeskRow("1", "ks1:aa:bb"),
            rustDeskRow("2", "ks1:cc:dd"),
            rdpRow("3", "keep-me"),
        )

        val result = RustDeskExportSanitizer.blankRustDeskPasswords(source)

        assertNotNull(result)
        assertEquals(2, result!!.passwordsRemoved)
        assertTrue(result.document.contains("NICKNAME=\"555000111\""))
        assertTrue(result.document.contains("ADDRESS=\"555000111\""))
        assertTrue(result.document.contains("PASSWORD=\"keep-me\""))
        assertTrue(!result.document.contains("ks1:"))
    }

    @Test
    fun `a document without rustdesk accesses is unchanged`() {
        val source = document(rdpRow("1", "s3cret"))

        val result = RustDeskExportSanitizer.blankRustDeskPasswords(source)

        assertNotNull(result)
        assertEquals(0, result!!.passwordsRemoved)
        assertEquals(source, result.document)
    }

    @Test
    fun `a document without rows is returned as is`() {
        val source = "<database></database>"

        val result = RustDeskExportSanitizer.blankRustDeskPasswords(source)

        assertNotNull(result)
        assertEquals(0, result!!.passwordsRemoved)
        assertEquals(source, result.document)
    }

    @Test
    fun `a sealed value outside a connection row is still removed`() {
        // Une valeur scellée ne peut venir que de ce fork : la retirer même si la ligne n'annonce
        // pas son type de connexion évite qu'un changement de schéma la laisse passer.
        val source = document("<row _id=\"9\" PASSWORD=\"ks1:aa:bb\"></row>")

        val result = RustDeskExportSanitizer.blankRustDeskPasswords(source)

        assertNotNull(result)
        assertEquals(1, result!!.passwordsRemoved)
        assertTrue(!result.document.contains("ks1:"))
    }

    @Test
    fun `an attribute whose name merely ends with password is left alone`() {
        val source = document("<row _id=\"1\" CONNECTIONTYPE=\"8\" SSHPASSWORD=\"ssh-secret\"></row>")

        val result = RustDeskExportSanitizer.blankRustDeskPasswords(source)

        assertNotNull(result)
        assertEquals(0, result!!.passwordsRemoved)
        assertTrue(result.document.contains("SSHPASSWORD=\"ssh-secret\""))
    }

    @Test
    fun `an unterminated password value refuses the export instead of leaking it`() {
        val source = document("<row _id=\"1\" CONNECTIONTYPE=\"6\" PASSWORD=\"ks1:aa:bb></row>")

        assertNull(RustDeskExportSanitizer.blankRustDeskPasswords(source))
    }

    @Test
    fun `a sealed value outside any row refuses the export`() {
        // Forme inattendue : mieux vaut refuser d'écrire que d'écrire sans avoir su nettoyer.
        val source = "<database><table table_name=\"CONNECTION_BEAN\" PASSWORD=\"ks1:aa:bb\"></table></database>"

        assertNull(RustDeskExportSanitizer.blankRustDeskPasswords(source))
    }

    @Test
    fun `a password attribute without a recognised row refuses the export`() {
        val source = "<database><field PASSWORD=\"clear-secret\"></field></database>"

        assertNull(RustDeskExportSanitizer.blankRustDeskPasswords(source))
    }

    @Test
    fun `a truncated document refuses the export`() {
        val source = "<database><table table_name=\"CONNECTION_BEAN\">" +
            "<row _id=\"1\" CONNECTIONTYPE=\"6\" PASSWORD=\"ks1:aa:bb\""

        assertNull(RustDeskExportSanitizer.blankRustDeskPasswords(source))
    }
}
