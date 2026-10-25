package com.lelouch.core.data.preferences

/**
 * FASE 35 (1.0.97) — La lista personalizada alojada en Supabase quedó retirada.
 *
 * La app la materializaba en Room bajo el id fijo `custom_lelouch`, con un catálogo de
 * miles de ítems que se re-sincronizaba en cada arranque y que además resucitaba desde
 * `custom_playlists`/`playlists` después de borrarla. Este predicado es el único punto
 * de verdad para descartarla: lo usan la lectura de preferencias, la reconciliación con
 * la nube y la purga de arranque.
 */
const val RETIRED_CUSTOM_SOURCE_ID: String = "custom_lelouch"

/**
 * True para cualquier entrada heredada de la lista personalizada:
 * - su id canónico o cualquier id `custom_*` anterior;
 * - su endpoint de manifiesto en el host de Lelouch.
 *
 * No se filtra por el nombre: una lista normal del usuario puede llamarse "Mi Lista".
 */
fun isRetiredCustomSource(id: String, serverUrl: String): Boolean {
    if (id == RETIRED_CUSTOM_SOURCE_ID || id.startsWith("custom_")) return true
    val url = serverUrl.lowercase()
    return url.contains("lelouch-web-player.vercel.app") && url.contains("/api/playlist")
}
