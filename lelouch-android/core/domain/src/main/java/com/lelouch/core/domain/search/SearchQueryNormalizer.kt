package com.lelouch.core.domain.search

import java.text.Normalizer

/**
 * Normalizador y constructor seguro de consultas de búsqueda.
 *
 * Cumple con los requisitos de FASE CONTROLADA — P1 #3:
 * 1. Sanitización de caracteres especiales que provocan errores de sintaxis en FTS (MATCH).
 * 2. Normalización de espacios múltiples y recorte (trim).
 * 3. Expansión y desacentuación de términos diacríticos (ej. "película" <-> "pelicula").
 * 4. Generación de prefijos FTS seguros sin romper comodines.
 */
object SearchQueryNormalizer {

    fun normalize(query: String): String {
        return query.trim().replace(Regex("\\s+"), " ")
    }

    fun stripAccents(input: String): String {
        val nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    /**
     * Construye una consulta FTS4 válida con operadores de prefijo (*) por token.
     * Si el término contiene acentos, genera una cláusula OR con el término desacentuado.
     * Retorna cadena vacía si no hay tokens con longitud >= 2.
     */
    fun buildFtsQuery(query: String): String {
        val normalized = normalize(query)
        if (normalized.length < 2) return ""

        // Eliminar caracteres de control y operadores conflictivos de FTS (*, -, :, ", (, ), etc.)
        val cleaned = normalized.replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        if (cleaned.isBlank()) return ""

        val tokens = cleaned.split(" ").filter { it.length >= 2 }
        if (tokens.isEmpty()) return ""

        return tokens.joinToString(" AND ") { token ->
            val stripped = stripAccents(token)
            if (stripped.equals(token, ignoreCase = true)) {
                "$token*"
            } else {
                "($token* OR $stripped*)"
            }
        }
    }
}
