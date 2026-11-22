package com.lelouch.core.network

object UrlSanitizer {

    private val SAFE_QUERY_KEYS = setOf("action", "series_id", "vod_id", "stream_id", "category_id", "_t")
    private val QUERY_PARAM_REGEX = Regex("([?&])([^=&]+)=([^&#]*)")
    private val BARE_QUERY_REGEX = Regex("\\?(?![^#]*=)([^#\\s]+)")
    private val XTREAM_PATH_REGEX = Regex("/(live|movie|series)/([^/]+)/([^/]+)/")
    private val BASIC_AUTH_REGEX = Regex("^(https?://)([^:@/]+):([^@/]+)@")

    /**
     * Sanitizes sensitive IPTV credentials and tokens from URLs (Xtream paths, query tokens/secrets, basic auth).
     * Output format example:
     *   http://provider.tv:8080/live/[REDACTED]/[REDACTED]/123.ts?token=***
     *   http://provider.tv:8080/player_api.php?username=***&password=***&action=get_live_categories
     */
    fun sanitizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return "— (vacía)"
        var out = url
        // Basic auth: http://user:pass@host -> http://***:***@host
        out = BASIC_AUTH_REGEX.replace(out) { "${it.groupValues[1]}***:***@" }
        // Xtream path: /live/user/pass/id -> /live/***/***/id
        out = XTREAM_PATH_REGEX.replace(out) { "/${it.groupValues[1]}/***/***/" }
        // Query params: preservar solo claves de diagnóstico no sensibles; enmascarar tokens y credenciales
        out = QUERY_PARAM_REGEX.replace(out) { match ->
            val sep = match.groupValues[1]
            val key = match.groupValues[2]
            val value = match.groupValues[3]
            if (key.lowercase() in SAFE_QUERY_KEYS) {
                "$sep$key=$value"
            } else {
                "$sep$key=***"
            }
        }
        out = BARE_QUERY_REGEX.replace(out) { "?***" }
        return out
    }
}
