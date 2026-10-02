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
)

