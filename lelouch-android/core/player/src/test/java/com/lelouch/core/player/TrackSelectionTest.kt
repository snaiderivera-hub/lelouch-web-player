package com.lelouch.core.player

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FASE 36 · Etiquetas y clasificación de pistas.
 * No requieren Android: solo [Format] (POJO de Media3) y constantes MIME.
 */
class TrackSelectionTest {

    private fun audioFormat(language: String?, channels: Int, mime: String = "audio/mp4a-latm") =
        Format.Builder()
            .setLanguage(language)
            .setChannelCount(channels)
            .setSampleMimeType(mime)
            .build()

    private fun videoFormat(height: Int, bitrate: Int = 0, mime: String = "video/avc") =
        Format.Builder()
            .setHeight(height)
            .setSampleMimeType(mime)
            .build()

    @Test
    fun `idioma se traduce a nombre legible`() {
        assertEquals("Español", languageDisplayName("es"))
        assertEquals("Portugués", languageDisplayName("por"))
        assertEquals("Inglés", languageDisplayName("en"))
        assertNull(languageDisplayName(null))
        assertNull(languageDisplayName(""))
    }

    @Test
    fun `audio incluye idioma y canales`() {
        assertEquals("Inglés · Estéreo", audioTrackLabel(audioFormat("en", 2)))
        assertEquals("Español · 5.1", audioTrackLabel(audioFormat("spa", 6)))
        assertEquals("Pista de audio · Mono", audioTrackLabel(audioFormat(null, 1)))
    }

    @Test
    fun `video usa etiqueta de resolucion`() {
        assertEquals("1080p FHD", videoTrackLabel(videoFormat(1080)))
        assertEquals("720p HD", videoTrackLabel(videoFormat(720)))
        assertEquals("4K UHD", videoTrackLabel(videoFormat(2160)))
        assertEquals("Auto", videoTrackLabel(videoFormat(0)))
    }

    @Test
    fun `mime de subtitulo externo se infiere por extension`() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, guessSubtitleMimeType("content://x/peli.srt"))
        assertEquals(MimeTypes.TEXT_VTT, guessSubtitleMimeType("content://x/peli.VTT"))
        assertEquals(MimeTypes.TEXT_SSA, guessSubtitleMimeType("content://x/peli.ass"))
        assertEquals(MimeTypes.APPLICATION_TTML, guessSubtitleMimeType("content://x/peli.ttml"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, guessSubtitleMimeType("content://x/sin-extension"))
    }

    @Test
    fun `hasAlternatives detecta pistas multiples`() {
        val single = TrackSelectionState(
            video = listOf(PlayerTrack("v", PlayerTrackKind.VIDEO, "1080p")),
            audio = listOf(PlayerTrack("a", PlayerTrackKind.AUDIO, "Español"))
        )
        assertEquals(false, single.hasAlternatives)

        val multi = single.copy(
            audio = single.audio + PlayerTrack("a2", PlayerTrackKind.AUDIO, "Inglés")
        )
        assertEquals(true, multi.hasAlternatives)
        assertEquals(true, multi.hasMultipleAudio)
    }
}
