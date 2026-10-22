package com.lelouch.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SourceType {
    XTREAM,
    M3U
}

@Serializable
data class SourceConfig(
    val id: String,
    val name: String,
    val serverUrl: String,
    val username: String = "",
    val password: String = "",
    val type: SourceType = SourceType.XTREAM,
    val maxConnections: Int = 1,
    val activeConnections: Int = 0,
    val expireDate: String? = null,
    val isTrial: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Token de acceso para playlists personalizadas (SourceType.M3U).
     * Se usa para construir la URL: /api/playlist/<accessToken>
     * No es la contraseña Xtream — es el token generado en Supabase/Vercel.
     */
    val accessToken: String? = null
) {
    val expireDaysRemaining: Int?
        get() {
            if (expireDate.isNullOrBlank() || expireDate == "null") return null
            val timestampMs = expireDate.toLongOrNull()?.let {
                if (it < 10000000000L) it * 1000L else it
            } ?: try {
                val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                isoFormat.parse(expireDate.substringBefore('.'))?.time
            } catch (_: Exception) { null }
            ?: return null

            val diffMs = timestampMs - System.currentTimeMillis()
            return kotlin.math.ceil(diffMs.toDouble() / (1000 * 60 * 60 * 24)).toInt()
        }

    val formattedExpireDate: String
        get() {
            if (expireDate.isNullOrBlank() || expireDate == "null") return "Ilimitado"
            val timestamp = expireDate.toLongOrNull()
            if (timestamp != null) {
                val ms = if (timestamp < 10000000000L) timestamp * 1000L else timestamp
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                return sdf.format(java.util.Date(ms))
            }
            try {
                val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val parsed = isoFormat.parse(expireDate.substringBefore('.'))
                if (parsed != null) {
                    val outFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    return outFormat.format(parsed)
                }
            } catch (_: Exception) {}
            return expireDate
        }
}

