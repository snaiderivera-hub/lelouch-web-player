package com.lelouch.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

/**
 * Pistas de reproducción seleccionables (FASE 36 · paridad con reproductores IPTV premium).
 *
 * El motor expone estas listas a la UI para que el usuario pueda cambiar calidad de video,
 * pista de audio y subtítulos sin recrear el reproductor.
 */
enum class PlayerTrackKind { VIDEO, AUDIO, SUBTITLE }

data class PlayerTrack(
    val id: String,
    val kind: PlayerTrackKind,
    val label: String,
    val secondaryLabel: String? = null,
    val languageCode: String? = null,
    val isSelected: Boolean = false
)

data class TrackSelectionState(
    val video: List<PlayerTrack> = emptyList(),
    val audio: List<PlayerTrack> = emptyList(),
    val subtitles: List<PlayerTrack> = emptyList(),
    val autoVideoSelected: Boolean = true,
    val subtitlesEnabled: Boolean = false
) {
    val hasAlternatives: Boolean
        get() = video.size > 1 || audio.size > 1 || subtitles.isNotEmpty()

    val hasMultipleAudio: Boolean get() = audio.size > 1
}

/** Identificador virtual de la opción "Auto" (ABR) para la pista de video. */
const val AUTO_VIDEO_TRACK_ID = "__auto_video__"

private val LANGUAGE_NAMES = mapOf(
    "es" to "Español",
    "spa" to "Español",
    "en" to "Inglés",
    "eng" to "Inglés",
    "pt" to "Portugués",
    "por" to "Portugués",
    "fr" to "Francés",
    "fra" to "Francés",
    "de" to "Alemán",
    "deu" to "Alemán",
    "it" to "Italiano",
    "ita" to "Italiano",
    "ja" to "Japonés",
    "jpn" to "Japonés",
    "ko" to "Coreano",
    "kor" to "Coreano",
    "zh" to "Chino",
    "zho" to "Chino",
    "ru" to "Ruso",
    "rus" to "Ruso",
    "ar" to "Árabe",
    "ara" to "Árabe",
    "und" to "Desconocido"
)

internal fun languageDisplayName(code: String?): String? {
    if (code.isNullOrBlank()) return null
    return LANGUAGE_NAMES[code.lowercase()] ?: code.uppercase()
}

private fun codecLabel(mimeType: String?): String = when {
    mimeType == null -> ""
    mimeType.contains("avc", true) || mimeType.contains("h264", true) -> "H.264"
    mimeType.contains("hevc", true) || mimeType.contains("h265", true) -> "H.265"
    mimeType.contains("av01", true) -> "AV1"
    mimeType.contains("vp9", true) -> "VP9"
    mimeType.contains("vp8", true) -> "VP8"
    mimeType.contains("mpeg4", true) -> "MPEG-4"
    mimeType.contains("mp4a", true) || mimeType.contains("aac", true) -> "AAC"
    mimeType.contains("ac-3", true) || mimeType.contains("ac3", true) -> "AC-3"
    mimeType.contains("eac3", true) || mimeType.contains("ec-3", true) -> "E-AC-3"
    mimeType.contains("opus", true) -> "Opus"
    mimeType.contains("vorbis", true) -> "Vorbis"
    mimeType.contains("mp3", true) -> "MP3"
    mimeType.contains("subrip", true) || mimeType.contains("srt", true) -> "SRT"
    mimeType.contains("vtt", true) -> "WebVTT"
    mimeType.contains("ssa", true) -> "SSA/ASS"
    mimeType.contains("ttml", true) -> "TTML"
    mimeType.contains("pgs", true) -> "PGS"
    mimeType.contains("dvb", true) -> "DVB"
    else -> mimeType.substringAfterLast('/').uppercase()
}

internal fun videoTrackLabel(format: Format): String {
    val height = format.height
    val label = when {
        height >= 2160 -> "4K UHD"
        height >= 1080 -> "1080p FHD"
        height >= 720 -> "720p HD"
        height > 0 -> "${height}p"
        else -> "Auto"
    }
    return label
}

internal fun videoTrackSecondaryLabel(format: Format): String? {
    val parts = mutableListOf<String>()
    val codec = codecLabel(format.sampleMimeType)
    if (codec.isNotEmpty()) parts += codec
    if (format.bitrate > 0) parts += "${format.bitrate / 1000} kbps"
    if (format.frameRate > 0f) parts += "${format.frameRate.toInt()} fps"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun audioTrackLabel(format: Format): String {
    val language = languageDisplayName(format.language) ?: "Pista de audio"
    val channels = when (format.channelCount) {
        1 -> "Mono"
        2 -> "Estéreo"
        6 -> "5.1"
        8 -> "7.1"
        else -> if (format.channelCount > 0) "${format.channelCount} canales" else null
    }
    return if (channels != null) "$language · $channels" else language
}

internal fun audioTrackSecondaryLabel(format: Format): String? {
    val parts = mutableListOf<String>()
    val codec = codecLabel(format.sampleMimeType)
    if (codec.isNotEmpty()) parts += codec
    if (format.sampleRate > 0) parts += "${format.sampleRate / 1000} kHz"
    if (format.bitrate > 0) parts += "${format.bitrate / 1000} kbps"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun subtitleTrackLabel(format: Format): String {
    val language = languageDisplayName(format.language) ?: format.label ?: "Subtítulos"
    val forced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
    return if (forced) "$language (forzados)" else language
}

internal fun subtitleTrackSecondaryLabel(format: Format): String? {
    val parts = mutableListOf<String>()
    val codec = codecLabel(format.sampleMimeType)
    if (codec.isNotEmpty()) parts += codec
    if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) parts += "Forzados"
    if ((format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) parts += "SDH"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** MIME de subtítulo externo inferido por extensión (side-loaded). */
internal fun guessSubtitleMimeType(uri: String): String = when {
    uri.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    uri.contains(".ssa", ignoreCase = true) || uri.contains(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
    uri.contains(".ttml", ignoreCase = true) || uri.contains(".dfxp", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
    else -> MimeTypes.APPLICATION_SUBRIP
}
