package com.lelouch.core.network.dns

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class VpnAndDohVerificationTest {

    @Test
    fun `all 4 DoH providers resolve domain over HTTPS port 443`() {
        for (provider in LelouchDnsManager.DnsProvider.entries) {
            val ips = LelouchDnsManager.queryDohDns(provider.dohUrl, "cloudflare.com", 4_000)
            assertNotNull("DoH provider ${provider.name} (${provider.dohUrl}) returned null", ips)
            assertFalse("DoH provider ${provider.name} returned empty IPs", ips!!.isEmpty())
            println("[DoH 443 OK] ${provider.title} (${provider.dohUrl}) -> ${ips.joinToString { it.hostAddress ?: "" }}")
        }
    }

    @Test
    fun `antiDpiSocketFactory splits first HTTP request packet and preserves stream integrity`() {
        LelouchDnsManager.setVpnShieldEnabled(true)
        val beforeSplits = LelouchDnsManager.getSplitPacketsCount()

        val receivedBytes = ByteArrayOutputStream()
        val chunkSizes = CopyOnWriteArrayList<Int>()

        ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = thread {
                server.accept().use { client ->
                    val input = client.getInputStream()
                    val buf = ByteArray(1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        chunkSizes.add(read)
                        receivedBytes.write(buf, 0, read)
                    }
                }
            }

            val payload = "GET /live/user/pass/101.m3u8 HTTP/1.1\r\nHost: iptv.example.com:8080\r\nUser-Agent: TiviMate/4.7.0\r\n\r\n"
                .toByteArray(Charsets.UTF_8)

            LelouchDnsManager.antiDpiSocketFactory.createSocket("127.0.0.1", port).use { socket ->
                assertTrue("TCP_NODELAY must be enabled on Anti-DPI socket", socket.tcpNoDelay)
                val out = socket.getOutputStream()
                out.write(payload)
                out.flush()
            }

            serverThread.join(3_000)
        }

        assertArrayEquals("Reassembled TCP payload must match original HTTP request 100%", payloadByteArray(), receivedBytes.toByteArray())
        assertTrue(
            "splitPacketsCount should increment when VPN Shield is active",
            LelouchDnsManager.getSplitPacketsCount() > beforeSplits
        )
        println("[Anti-DPI TCP Split OK] Total split connections=${LelouchDnsManager.getSplitPacketsCount()}, chunks read=$chunkSizes")
    }

    @Test
    fun `verifyVpnShieldLive confirms DoH 443 and updates verification status`() = runBlocking {
        LelouchDnsManager.setSelectedProvider(LelouchDnsManager.DnsProvider.CLOUDFLARE)
        LelouchDnsManager.setVpnShieldEnabled(true)
        val ok = LelouchDnsManager.verifyVpnShieldLive("cloudflare.com")
        assertTrue("verifyVpnShieldLive should return true", ok)
        val status = LelouchDnsManager.vpnVerificationFlow.value
        assertTrue("Verification status should confirm verification: $status", status.contains("Verificado"))
        println("[Live Shield Verification OK] $status")
    }

    @Test
    fun `user agent rotation cycles across all 4 IPTV signatures when shield is enabled`() {
        LelouchDnsManager.setVpnShieldEnabled(true)
        val seen = mutableSetOf<String>()
        repeat(4) {
            seen.add(LelouchDnsManager.rotateUserAgentOnStall())
        }
        assertEquals("Should rotate across 4 distinct Anti-DPI User-Agent profiles", 4, seen.size)
    }

    private fun payloadByteArray(): ByteArray =
        "GET /live/user/pass/101.m3u8 HTTP/1.1\r\nHost: iptv.example.com:8080\r\nUser-Agent: TiviMate/4.7.0\r\n\r\n"
            .toByteArray(Charsets.UTF_8)
}

