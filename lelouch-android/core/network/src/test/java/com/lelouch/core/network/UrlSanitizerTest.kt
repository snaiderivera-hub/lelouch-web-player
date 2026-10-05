package com.lelouch.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSanitizerTest {

    @Test
    fun `sanitizeUrl redacts xtream live stream credentials`() {
        val raw = "http://iptv.server.io:8080/live/superman/secret1234/98765.m3u8"
        val sanitized = UrlSanitizer.sanitizeUrl(raw)
        assertEquals("http://iptv.server.io:8080/live/***/***/98765.m3u8", sanitized)
        assertFalse(sanitized.contains("superman"))
        assertFalse(sanitized.contains("secret1234"))
    }

    @Test
    fun `sanitizeUrl redacts xtream movie stream credentials`() {
        val raw = "http://iptv.server.io:8080/movie/superman/secret1234/4321.mp4"
        val sanitized = UrlSanitizer.sanitizeUrl(raw)
        assertEquals("http://iptv.server.io:8080/movie/***/***/4321.mp4", sanitized)
        assertFalse(sanitized.contains("secret1234"))
    }

    @Test
    fun `sanitizeUrl redacts xtream series stream credentials`() {
        val raw = "http://iptv.server.io:8080/series/superman/secret1234/999.mkv"
        val sanitized = UrlSanitizer.sanitizeUrl(raw)
        assertEquals("http://iptv.server.io:8080/series/***/***/999.mkv", sanitized)
        assertFalse(sanitized.contains("secret1234"))
    }

    @Test
    fun `sanitizeUrl redacts query parameters for username and password`() {
        val raw = "http://iptv.server.io:8080/player_api.php?username=admin&password=mySuperPassword&action=get_live_categories"
        val sanitized = UrlSanitizer.sanitizeUrl(raw)
        assertTrue(sanitized.contains("username=***"))
        assertTrue(sanitized.contains("password=***"))
        assertFalse(sanitized.contains("admin"))
        assertFalse(sanitized.contains("mySuperPassword"))
    }

    @Test
    fun `sanitizeUrl redacts basic auth in url`() {
        val raw = "http://myuser:mypassword@iptv.server.io:8080/logo.png"
        val sanitized = UrlSanitizer.sanitizeUrl(raw)
        assertEquals("http://***:***@iptv.server.io:8080/logo.png", sanitized)
    }

    @Test
    fun `sanitizeUrl handles null or empty`() {
        assertEquals("— (vacía)", UrlSanitizer.sanitizeUrl(null))
        assertEquals("— (vacía)", UrlSanitizer.sanitizeUrl(""))
        assertEquals("— (vacía)", UrlSanitizer.sanitizeUrl("   "))
    }
}
