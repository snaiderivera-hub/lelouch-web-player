package com.lelouch.core.model

/**
 * Estado observable paso a paso de la consola de descarga al conectar o sincronizar una lista.
 */
data class CatalogDownloadProgress(
    val isActive: Boolean = false,
    val percent: Int = 0,
    val currentStep: String = "",
    val logs: List<String> = emptyList(),
    val isError: Boolean = false,
    val isCompleted: Boolean = false
)
