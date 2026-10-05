package com.lelouch.core.domain.paging

import androidx.paging.PagingConfig

/**
 * Configuración centralizada de Paging 3 para catálogos IPTV.
 * Permite calibrar y medir parámetros de paginación desde un único punto de verdad.
 */
object PagingConfigs {
    val liveChannels = PagingConfig(
        pageSize = 50,
        initialLoadSize = 100,
        prefetchDistance = 20,
        enablePlaceholders = false
    )
}
