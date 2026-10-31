package com.lelouch.core.data.util

import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import java.net.URI
import java.net.URLDecoder

/**
 * FASE 33 — Parser inteligente de URLs IPTV para Android (paridad 100% con UrlParser.js de Web Player).
 * Permite pegar enlaces completos M3U, Xtream Codes o endpoints de Vercel/Supabase
 * sin necesidad de introducir manualmente usuario, contraseña y puerto por separado.
 */
object SmartUrlParser {

    data class ParsedSmartUrl(
        val serverUrl: String,
        val username: String,
        val password: String,
        val type: SourceType,
        val suggestedName: String,
        val token: String? = null
    )

    fun parse(rawUrl: String, customName: String? = null): ParsedSmartUrl {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("La URL no puede estar vacía.")
        }

        val urlClean = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "http://$trimmed"
        } else {
            trimmed
        }

        val uri = try {
            URI(urlClean)
        } catch (_: Exception) {
            null
        }

        val query = uri?.rawQuery ?: (if (urlClean.contains('?')) urlClean.substringAfter('?') else "")
        val params = mutableMapOf<String, String>()
        if (query.isNotBlank()) {
            query.split('&').forEach { param ->
                val parts = param.split('=', limit = 2)
                if (parts.size == 2) {
                    try {
                        params[parts[0].lowercase()] = URLDecoder.decode(parts[1], "UTF-8")
                    } catch (_: Exception) {
                        params[parts[0].lowercase()] = parts[1]
                    }
                }
            }
        }

        val username = params["username"] ?: params["user"] ?: ""
        val password = params["password"] ?: params["pass"] ?: ""
        val token = params["token"] ?: (if (urlClean.contains("/api/playlist/")) urlClean.substringAfter("/api/playlist/").substringBefore('?').trim() else null)

        val host = uri?.host ?: Regex("https?://([^/:]+)").find(urlClean)?.groupValues?.get(1) ?: ""
        var port = uri?.port?.takeIf { it != -1 }?.toString() ?: (if (urlClean.startsWith("https", ignoreCase = true)) "443" else "80")

        // Optimización conocida para LionTV (puerto 80 saturado -> derivar a 8080)
        if (host.equals("liontv.es", ignoreCase = true) && (port == "80" || uri?.port == -1)) {
            port = "8080"
        }

        var scheme = uri?.scheme ?: (if (urlClean.startsWith("https", ignoreCase = true)) "https" else "http")
        val nonSslPorts = setOf("80", "8080", "8880", "2095", "25461", "8000", "8888")
        if (scheme.equals("https", ignoreCase = true) && nonSslPorts.contains(port)) {
            scheme = "http"
        }
        val serverBaseUrl = if (port != "80" && port != "443") "$scheme://$host:$port" else "$scheme://$host"

        val path = uri?.path?.lowercase() ?: urlClean.substringBefore('?').lowercase()
        val hasCredentials = username.isNotBlank() && password.isNotBlank()

        val isXtream = hasCredentials && (
            path.contains("get.php") ||
            path.contains("player_api.php") ||
            params["type"] == "m3u_plus" ||
            !path.contains("/api/playlist")
        )

        val isVercelCustom = path.contains("/api/playlist") || urlClean.contains("token=")
        val isM3u = !isXtream && (
            isVercelCustom ||
            path.endsWith(".m3u") ||
            path.endsWith(".m3u8") ||
            params["type"] == "m3u" ||
            !hasCredentials
        )

        val name = customName?.takeIf { it.isNotBlank() } ?: when {
            isVercelCustom -> "⭐ Mi Lista Personalizada LELOUCH"
            host.isNotBlank() -> "IPTV: $host"
            else -> "Nueva Lista IPTV"
        }

        return if (isXtream) {
            ParsedSmartUrl(
                serverUrl = serverBaseUrl,
                username = username,
                password = password,
                type = SourceType.XTREAM,
                suggestedName = name,
                token = null
            )
        } else {
            ParsedSmartUrl(
                serverUrl = urlClean,
                username = username,
                password = password,
                type = SourceType.M3U,
                suggestedName = name,
                token = token
            )
        }
    }
}
