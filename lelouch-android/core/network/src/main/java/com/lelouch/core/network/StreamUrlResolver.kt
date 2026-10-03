package com.lelouch.core.network

import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType

/**
 * Resolvedor único y autoritativo de la URL de reproducción final (FASE 33).
 *
 * Por qué existe: en v1.0.8 y anteriores, cuando [SourceConfig.streamUrl] venía en blanco,
 * cada pantalla reconstruía la URL con [XtreamUrlBuilder] usando `activeSource.serverUrl`
 * como base. Para la lista personalizada esa base es `https://lelouch-web-player.vercel.app`,
 * lo que generaba URLs ficticias tipo `/live/LELOUCH//123.m3u8` -> HTTP 404 en Vercel
 * (Vercel no transmite vídeo) -> ExoPlayer en `ERROR_CODE_IO_BAD_HTTP_STATUS`.
 *
 * Reglas, en orden:
 *  1. URL directa no vacía -> se devuelve tal cual. Es la fuente de verdad.
 *  2. Sin fuente activa -> vacío. No hay con qué construir.
 *  3. Fuente no-Xtream (M3U / lista por token) -> vacío. Una URL M3U no se reconstruye:
 *     su `.serverUrl` es el endpoint del manifiesto, no un servidor Xtream.
 *  4. Fuente Xtream sin credenciales -> vacío. `/live//` sin usuario nunca autentica.
 *  5. En otro caso -> construir con [XtreamUrlBuilder] (solo dominios Xtream reales).
 */
object StreamUrlResolver {

    enum class Kind { LIVE, VOD, SERIES }

    /** Hosts que jamás deben usarse como base para construir URLs de stream. */
    private val NON_STREAM_HOSTS = listOf("vercel.app", "localhost", "127.0.0.1")

    fun resolve(
        directUrl: String?,
        source: SourceConfig?,
        streamId: Int,
        kind: Kind,
        extension: String = ""
    ): String {
        // 1. URL directa guardada en Room / Supabase: siempre gana.
        directUrl?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        // 2. Sin fuente activa no hay nada con qué reconstruir.
        val src = source ?: return ""

        // 3. Lista M3U / por token: su serverUrl es un manifiesto, no un servidor Xtream.
        if (src.type != SourceType.XTREAM) return ""

        // 4. Credenciales incompletas: la URL resultante sería inútil.
        if (src.username.isBlank()) return ""

        // 4b. Salvaguarda extra: bases que no pueden servir streams de vídeo.
        val base = src.serverUrl.trim()
        if (base.isBlank()) return ""
        if (NON_STREAM_HOSTS.any { base.contains(it, ignoreCase = true) }) return ""

        // 5. Fuente Xtream legítima: construir la ruta canónica.
        return when (kind) {
            Kind.LIVE -> XtreamUrlBuilder.buildLiveStreamUrl(
                base, src.username, src.password, streamId,
                extension.ifBlank { "m3u8" }
            )
            Kind.VOD -> XtreamUrlBuilder.buildVodStreamUrl(
                base, src.username, src.password, streamId,
                extension.trimStart('.').ifBlank { "mp4" }
            )
            Kind.SERIES -> XtreamUrlBuilder.buildSeriesStreamUrl(
                base, src.username, src.password, streamId,
                extension.trimStart('.').ifBlank { "mp4" }
            )
        }
    }

    /** Versión corta para canales en vivo. */
    fun resolveLive(directUrl: String?, source: SourceConfig?, streamId: Int): String =
        resolve(directUrl, source, streamId, Kind.LIVE, "m3u8")

    /** True si la URL resultante sería reproducible (no vacía). */
    fun isPlayable(url: String): Boolean = url.isNotBlank()

    /**
     * True solo si [serverUrl] puede usarse como base Xtream legítima para construir
     * rutas `/live|movie|series/...`. Devuelve false para endpoints de manifiesto
     * (Vercel), URLs vacías o credenciales ausentes.
     */
    fun isXtreamBase(serverUrl: String?, username: String?): Boolean {
        val base = serverUrl?.trim().orEmpty()
        if (base.isEmpty()) return false
        if (username.isNullOrBlank()) return false
        if (NON_STREAM_HOSTS.any { base.contains(it, ignoreCase = true) }) return false
        return base.startsWith("http://", ignoreCase = true) ||
            base.startsWith("https://", ignoreCase = true)
    }
}
