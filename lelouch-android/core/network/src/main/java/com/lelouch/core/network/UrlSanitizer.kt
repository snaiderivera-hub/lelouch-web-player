package com.lelouch.core.network

object UrlSanitizer {

    private val PASSWORD_QUERY_REGEX = Regex("([?&](?:password|pass|pwd)=)[^&]+", RegexOption.IGNORE_CASE)
    private val USERNAME_QUERY_REGEX = Regex("([?&](?:username|user)=)[^&]+", RegexOption.IGNORE_CASE)
    private val TOKEN_QUERY_REGEX = Regex("([?&]token=)([a-zA-Z0-9_-]{4})[a-zA-Z0-9_-]*", RegexOption.IGNORE_CASE)
    private val XTREAM_PATH_REGEX = Regex("/(live|movie|series)/([^/]+)/([^/]+)/")
    private val BASIC_AUTH_REGEX = Regex("^(https?://)([^:@/]+):([^@/]+)@")

    /**
     * Sanitizes sensitive IPTV credentials from URLs (Xtream paths, auth queries, basic auth).
     * Output format example:
     *   http://provider.tv:8080/live/[REDACTED]/[REDACTED]/123.ts
     *   http://provider.tv:8080/player_api.php?username=***&password=***
     */
    fun sanitizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return "— (vacía)"
        var out = url
        // Basic auth: http://user:pass@host -> http://***:***@host
        out = BASIC_AUTH_REGEX.replace(out) { "${it.groupValues[1]}***:***@" }
        // Xtream path: /live/user/pass/id -> /live/***/***/id
        out = XTREAM_PATH_REGEX.replace(out) { "/${it.groupValues[1]}/***/***/" }
        // Query params
        out = PASSWORD_QUERY_REGEX.replace(out) { "${it.groupValues[1]}***" }
        out = USERNAME_QUERY_REGEX.replace(out) { "${it.groupValues[1]}***" }
        out = TOKEN_QUERY_REGEX.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}…" }
        return out
    }
}
