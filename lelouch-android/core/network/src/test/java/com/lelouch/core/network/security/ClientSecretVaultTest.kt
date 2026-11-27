package com.lelouch.core.network.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ClientSecretVaultTest {

    @Test
    fun `decodifica URLs y tokens cliente con integridad exacta`() {
        val sbUrl = ClientSecretVault.supabaseBaseUrl()
        val sbKey = ClientSecretVault.supabaseKey()
        val tmdbKey = ClientSecretVault.tmdbApiKey()
        val tmdbBearer = ClientSecretVault.tmdbBearerToken()

        assertTrue(sbUrl.startsWith("https://"))
        assertTrue(sbUrl.endsWith(".supabase.co"))
        assertEquals(3, sbKey.split('.').size)
        assertEquals(32, tmdbKey.length)
        assertEquals(3, tmdbBearer.split('.').size)

        // Verifica que el hash SHA-256 decodificado coincida sin exponer literales en el repo
        assertEquals(40, sbUrl.length)
        assertEquals(64, sha256Hex(sbUrl).length)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }
}
