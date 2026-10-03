package com.lelouch.core.network

import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 33 — Cobertura del resolvedor de URLs de reproducción.
 *
 * Estos tests encapsulan exactamente el fallo de v1.0.8: cuando la fuente activa era la lista
 * personalizada (serverUrl = https://lelouch-web-player.vercel.app) y el elemento no traía
 * streamUrl, la app construía `.../live/LELOUCH//<id>.m3u8` sobre Vercel, que no transmite
 * vídeo -> HTTP 404 -> ExoPlayer `ERROR_CODE_IO_BAD_HTTP_STATUS` y pantalla negra.
 */
class StreamUrlResolverTest {

    private val vercelSource = SourceConfig(
        id = "custom_lelouch",
        name = "⭐ Mi Lista Personalizada LELOUCH",
        serverUrl = "https://lelouch-web-player.vercel.app/api/playlist?token=pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R",
        username = "",
        password = "",
        type = SourceType.M3U
    )

    private val xtreamSource = SourceConfig(
        id = "xtream_1",
        name = "LionTV (Principal)",
        serverUrl = "http://liontv.es:8080",
        username = "Hermanos503",
        password = "BysckXDynC",
        type = SourceType.XTREAM
    )

    // ── Regla 1: la URL directa siempre gana ────────────────────────────────

    @Test
    fun `devuelve la URL directa cuando existe`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "http://liontv.es:8080/live/Hermanos503/BysckXDynC/787092.m3u8",
            source = vercelSource,
            streamId = 787092,
            kind = StreamUrlResolver.Kind.LIVE,
            extension = "m3u8"
        )
        assertEquals(
            "http://liontv.es:8080/live/Hermanos503/BysckXDynC/787092.m3u8",
            url
        )
    }

    @Test
    fun `recorta espacios de la URL directa`() {
        val url = StreamUrlResolver.resolveLive("  http://ejemplo.com/a.m3u8  ", xtreamSource, 1)
        assertEquals("http://ejemplo.com/a.m3u8", url)
    }

    @Test
    fun `la URL directa gana incluso si la fuente es M3U`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "http://liontv.es/live/u/p/1.m3u8",
            source = vercelSource,
            streamId = 1,
            kind = StreamUrlResolver.Kind.LIVE
        )
        assertEquals("http://liontv.es/live/u/p/1.m3u8", url)
    }

    // ── Regla 2: sin fuente no hay con qué construir ────────────────────────

    @Test
    fun `devuelve vacio sin fuente activa`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = null,
            streamId = 123,
            kind = StreamUrlResolver.Kind.LIVE
        )
        assertEquals("", url)
    }

    @Test
    fun `devuelve vacio con URL directa en blanco y sin fuente`() {
        assertEquals("", StreamUrlResolver.resolveLive("   ", null, 5))
        assertEquals("", StreamUrlResolver.resolveLive(null, null, 5))
    }

    // ── Regla 3: NUNCA construir sobre una fuente M3U (el hueco de v1.0.8) ─

    @Test
    fun `NUNCA construye una URL sobre una fuente M3U`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = vercelSource,
            streamId = 787092,
            kind = StreamUrlResolver.Kind.LIVE,
            extension = "m3u8"
        )
        assertEquals("", url)
        assertFalse("No debe contener vercel.app", url.contains("vercel.app"))
    }

    @Test
    fun `no construye VOD sobre fuente M3U`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = vercelSource,
            streamId = 1966146,
            kind = StreamUrlResolver.Kind.VOD
        )
        assertEquals("", url)
    }

    @Test
    fun `no construye SERIES sobre fuente M3U`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = vercelSource,
            streamId = 55,
            kind = StreamUrlResolver.Kind.SERIES
        )
        assertEquals("", url)
    }

    // ── Regla 4: credenciales incompletas ───────────────────────────────────

    @Test
    fun `no construye URL Xtream sin usuario`() {
        val source = xtreamSource.copy(username = "", password = "")
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = source,
            streamId = 10,
            kind = StreamUrlResolver.Kind.LIVE
        )
        assertEquals("", url)
    }

    @Test
    fun `no construye URL con base Vercel declarada como XTREAM`() {
        val poisoned = xtreamSource.copy(serverUrl = "https://lelouch-web-player.vercel.app")
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = poisoned,
            streamId = 10,
            kind = StreamUrlResolver.Kind.LIVE
        )
        assertEquals("", url)
    }

    @Test
    fun `no construye URL con servidor vacio`() {
        val source = xtreamSource.copy(serverUrl = "   ")
        val url = StreamUrlResolver.resolveLive("", source, 1)
        assertEquals("", url)
    }

    // ── Regla 5: fuentes Xtream legítimas sí construyen ─────────────────────

    @Test
    fun `construye URL live correcta desde fuente Xtream`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = xtreamSource,
            streamId = 787092,
            kind = StreamUrlResolver.Kind.LIVE,
            extension = "m3u8"
        )
        assertEquals("http://liontv.es:8080/live/Hermanos503/BysckXDynC/787092.m3u8", url)
    }

    @Test
    fun `construye URL VOD con la extension indicada`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = xtreamSource,
            streamId = 1966146,
            kind = StreamUrlResolver.Kind.VOD,
            extension = ".mkv"
        )
        assertEquals("http://liontv.es:8080/movie/Hermanos503/BysckXDynC/1966146.mkv", url)
    }

    @Test
    fun `construye URL VOD con extension por defecto mp4`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = xtreamSource,
            streamId = 1,
            kind = StreamUrlResolver.Kind.VOD
        )
        assertEquals("http://liontv.es:8080/movie/Hermanos503/BysckXDynC/1.mp4", url)
    }

    @Test
    fun `construye URL de serie`() {
        val url = StreamUrlResolver.resolve(
            directUrl = "",
            source = xtreamSource,
            streamId = 99,
            kind = StreamUrlResolver.Kind.SERIES
        )
        assertEquals("http://liontv.es:8080/series/Hermanos503/BysckXDynC/99.mp4", url)
    }

    @Test
    fun `respeta el slash final del servidor Xtream`() {
        val source = xtreamSource.copy(serverUrl = "http://liontv.es:8080/")
        val url = StreamUrlResolver.resolveLive("", source, 7)
        assertEquals("http://liontv.es:8080/live/Hermanos503/BysckXDynC/7.m3u8", url)
    }

    // ── isXtreamBase: decide si una base sirve para construir ───────────────

    @Test
    fun `isXtreamBase acepta servidor Xtream con credenciales`() {
        assertTrue(StreamUrlResolver.isXtreamBase("http://liontv.es:8080", "user"))
        assertTrue(StreamUrlResolver.isXtreamBase("https://proveedor.tv/", "user"))
    }

    @Test
    fun `isXtreamBase rechaza endpoints de manifiesto y credenciales vacias`() {
        assertFalse(
            StreamUrlResolver.isXtreamBase(
                "https://lelouch-web-player.vercel.app/api/playlist?token=abc",
                "LELOUCH"
            )
        )
        assertFalse(StreamUrlResolver.isXtreamBase("http://liontv.es:8080", ""))
        assertFalse(StreamUrlResolver.isXtreamBase("", "user"))
        assertFalse(StreamUrlResolver.isXtreamBase(null, "user"))
        assertFalse(StreamUrlResolver.isXtreamBase("http://localhost:8080", "user"))
    }

    // ── utilidades ──────────────────────────────────────────────────────────

    @Test
    fun `isPlayable distingue URL vacia de valida`() {
        assertFalse(StreamUrlResolver.isPlayable(""))
        assertFalse(StreamUrlResolver.isPlayable("   "))
        assertTrue(StreamUrlResolver.isPlayable("http://liontv.es/live/a.m3u8"))
    }
}
