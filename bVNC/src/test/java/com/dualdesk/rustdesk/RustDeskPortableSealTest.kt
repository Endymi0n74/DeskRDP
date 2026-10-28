package com.dualdesk.rustdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercice du scellement portable : ce qui doit tenir, c'est qu'un mot de passe scellé sous une
 * phrase ne s'ouvre qu'avec cette phrase, et que le format transporte son propre coût.
 */
class RustDeskPortableSealTest {

    @Test
    fun `a sealed password opens with the same passphrase`() {
        val sealed = RustDeskPortableSeal.seal("phrase de test", "hunter2")

        assertEquals("hunter2", RustDeskPortableSeal.open("phrase de test", sealed))
    }

    @Test
    fun `a wrong passphrase returns nothing instead of another password`() {
        val sealed = RustDeskPortableSeal.seal("phrase de test", "hunter2")

        assertNull(RustDeskPortableSeal.open("autre phrase", sealed))
    }

    @Test
    fun `the same password sealed twice gives different values`() {
        val first = RustDeskPortableSeal.seal("phrase", "hunter2")
        val second = RustDeskPortableSeal.seal("phrase", "hunter2")

        assertNotEquals(first, second)
        assertEquals("hunter2", RustDeskPortableSeal.open("phrase", first))
        assertEquals("hunter2", RustDeskPortableSeal.open("phrase", second))
    }

    @Test
    fun `the value carries its cost and its prefix`() {
        val sealed = RustDeskPortableSeal.seal("phrase", "hunter2")

        assertTrue(sealed.startsWith(RustDeskPortableSeal.PREFIX))
        val parts = sealed.substring(RustDeskPortableSeal.PREFIX.length).split(":")
        assertEquals(4, parts.size)
        assertEquals(RustDeskPortableSeal.DEFAULT_ITERATIONS.toString(), parts[0])
    }

    @Test
    fun `an old cost is honoured when the value declares it`() {
        // Un fichier produit par une version au coût plus faible doit rester lisible.
        val weakened = RustDeskPortableSeal.seal("phrase", "hunter2")
            .replaceFirst(
                "${RustDeskPortableSeal.PREFIX}${RustDeskPortableSeal.DEFAULT_ITERATIONS}:",
                RustDeskPortableSeal.PREFIX + "1000:",
            )

        // Le sel et la clé ne correspondent plus : la valeur déclarée est utilisée, donc l'ouverture
        // échoue proprement au lieu de réussir avec le mauvais coût.
        assertNull(RustDeskPortableSeal.open("phrase", weakened))
    }

    @Test
    fun `an altered record does not open`() {
        val sealed = RustDeskPortableSeal.seal("phrase", "hunter2")
        val tampered = sealed.dropLast(2) + "00"

        assertNull(RustDeskPortableSeal.open("phrase", tampered))
    }

    @Test
    fun `a value that is not portable is not touched`() {
        assertNull(RustDeskPortableSeal.open("phrase", "ks1:aabb:ccdd"))
        assertNull(RustDeskPortableSeal.open("phrase", "clear-password"))
        assertNull(RustDeskPortableSeal.open("phrase", ""))
    }

    @Test
    fun `an empty password round-trips`() {
        val sealed = RustDeskPortableSeal.seal("phrase", "")

        assertEquals("", RustDeskPortableSeal.open("phrase", sealed))
    }

    @Test
    fun `a password with separators and non-ascii characters round-trips`() {
        val password = "a:b:c — mot de passe « long »"

        assertEquals(
            password,
            RustDeskPortableSeal.open("phrase", RustDeskPortableSeal.seal("phrase", password)),
        )
    }
}
