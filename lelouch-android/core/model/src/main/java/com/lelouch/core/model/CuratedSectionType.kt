package com.lelouch.core.model

/**
 * FASE 35/36: Tipos de sección curada para navegación dedicada (patrón TopicNavKey de Google).
 */
enum class CuratedSectionType(
    val title: String,
    val barColorHex: Long,
    val isMoviesOnly: Boolean = false
) {
    RECENT("RECIÉN AGREGADAS", 0xFF00E5C9, isMoviesOnly = true),
    TOP_RATED("MEJOR VALORADAS", 0xFFFFB800),
    HORROR("TERROR Y SUSPENSO", 0xFFFF5252),
    SCIFI("CIENCIA FICCIÓN", 0xFF00E5C9),
    TRUE_EVENTS("BASADO EN HECHOS REALES", 0xFFE5A93C),
    ACTION("ACCIÓN Y AVENTURA", 0xFFFF6D00),
    ANIMATION("MUNDO ANIMADO", 0xFFFF4081)
}

