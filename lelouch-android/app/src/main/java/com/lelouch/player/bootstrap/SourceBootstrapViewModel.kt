package com.lelouch.player.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lelouch.core.domain.repository.AuthRepository
import com.lelouch.core.model.SourceConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SourceBootstrapViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _bootstrapState = MutableStateFlow<SourceBootstrapState>(SourceBootstrapState.Loading)
    val bootstrapState: StateFlow<SourceBootstrapState> = _bootstrapState.asStateFlow()

    private var syncJob: Job? = null

    init {
        syncSources()
    }

    fun retry() {
        syncSources()
    }

    /**
     * FASE 35 (1.0.97): una fuente recién agregada ya está guardada y activa, así que la
     * pantalla puede pasar a Home de inmediato. No se vuelve a llamar a `syncCloudSources()`
     * (eso producía doble sincronización y el pantallazo de carga); el `LaunchedEffect` de la
     * pantalla sincroniza el catálogo mostrando su indicador de progreso.
     */
    fun onSourceAdded(source: SourceConfig) {
        // Un bootstrap en vuelo leyó la nube antes de esta alta; si termina después, pisaría
        // el estado Ready (o incluso el Empty) de la lista recién agregada.
        syncJob?.cancel()
        _bootstrapState.value = SourceBootstrapState.Ready(source)
    }

    fun syncSources() {
        if (syncJob?.isActive == true) return // Evita dobles requests concurrentes (TEST F)

        _bootstrapState.value = SourceBootstrapState.Loading
        syncJob = viewModelScope.launch {
            try {
                val sources = authRepository.syncCloudSources()
                processSources(sources)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleSyncException(e)
            }
        }
    }

    private suspend fun processSources(sources: List<SourceConfig>) {
        if (sources.isEmpty()) {
            _bootstrapState.value = SourceBootstrapState.Empty
            return
        }

        val activeSource = authRepository.getActiveSource().first()
            ?: sources.firstOrNull { it.isActive }
            ?: sources.firstOrNull()

        if (activeSource == null) {
            _bootstrapState.value = SourceBootstrapState.Empty
            return
        }

        if (authRepository.isLastSyncFromCache) {
            _bootstrapState.value = SourceBootstrapState.UsingLocalCache(
                source = activeSource,
                warning = "Sin conexión — usando catálogo local"
            )
        } else {
            _bootstrapState.value = SourceBootstrapState.Ready(activeSource)
        }
    }

    private suspend fun handleSyncException(e: Exception) {
        val localSources = authRepository.getAllSources().first()
        val localActive = authRepository.getActiveSource().first()
            ?: localSources.firstOrNull { it.isActive }

        if (localActive != null) {
            _bootstrapState.value = SourceBootstrapState.UsingLocalCache(
                source = localActive,
                warning = "Sin conexión — usando catálogo local"
            )
        } else {
            val errorMsg = if (e.message?.contains("ERROR_LOADING_SOURCES") == true) {
                "No fue posible conectar con el servidor. Verifica tu conexión a internet o intenta nuevamente."
            } else {
                e.localizedMessage ?: "Error al sincronizar listas"
            }
            _bootstrapState.value = SourceBootstrapState.ErrorNoSource(
                message = errorMsg,
                retryable = true
            )
        }
    }
}
