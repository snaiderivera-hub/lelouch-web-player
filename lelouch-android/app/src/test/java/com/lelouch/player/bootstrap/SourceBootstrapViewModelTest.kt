package com.lelouch.player.bootstrap

import com.lelouch.core.domain.repository.AuthRepository
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SourceBootstrapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepository : AuthRepository {
        val activeSourceFlow = MutableStateFlow<SourceConfig?>(null)
        val allSourcesFlow = MutableStateFlow<List<SourceConfig>>(emptyList())
        var syncCloudSourcesHandler: suspend () -> List<SourceConfig> = { emptyList() }
        var syncCallCount = 0
        override var isLastSyncFromCache: Boolean = false

        override fun getActiveSource(): Flow<SourceConfig?> = activeSourceFlow
        override fun getAllSources(): Flow<List<SourceConfig>> = allSourcesFlow
        override suspend fun saveSource(source: SourceConfig) {}
        override suspend fun activateSource(sourceId: String): SourceConfig? = null
        override suspend fun removeSource(sourceId: String) {}
        override suspend fun validateXtream(serverUrl: String, user: String, pass: String, customName: String?): Result<SourceConfig> {
            return Result.failure(UnsupportedOperationException())
        }
        // Este flujo no añade fuentes por URL: devolver fallo explícito evita que el test
        // pase en verde si el ViewModel empezara a usarlo sin cobertura real.
        override suspend fun addSourceFromUrl(rawUrl: String, customName: String?): Result<SourceConfig> {
            return Result.failure(UnsupportedOperationException("addSourceFromUrl no está soportado en este fake"))
        }
        override suspend fun syncCloudSources(): List<SourceConfig> {
            syncCallCount++
            return syncCloudSourcesHandler()
        }
        override suspend fun logout() {}
    }

    private val validSource = SourceConfig(
        id = "xtream_valid",
        name = "LionTV",
        serverUrl = "http://liontv.example:8080",
        username = "user1",
        password = "pass1",
        type = SourceType.XTREAM,
        isActive = true
    )

    // ── TEST A: fuente válida -> Ready -> Home ──
    @Test
    fun testA_validSource_readyHome() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { listOf(validSource) }
            activeSourceFlow.value = validSource
            allSourcesFlow.value = listOf(validSource)
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.bootstrapState.value
        assertTrue("Estado debe ser Ready", state is SourceBootstrapState.Ready)
        assertEquals("xtream_valid", (state as SourceBootstrapState.Ready).source.id)
    }

    // ── TEST B: SUCCESS_EMPTY -> Empty -> spinner off ──
    @Test
    fun testB_successEmpty_emptySpinnerOff() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { emptyList() }
            activeSourceFlow.value = null
            allSourcesFlow.value = emptyList()
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.bootstrapState.value
        assertTrue("Estado debe ser Empty", state is SourceBootstrapState.Empty)
    }

    // ── TEST C: network failure sin local source -> ErrorNoSource -> spinner off ──
    @Test
    fun testC_networkFailure_noLocalSource_errorNoSourceSpinnerOff() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { throw IOException("ERROR_LOADING_SOURCES: connection timed out") }
            activeSourceFlow.value = null
            allSourcesFlow.value = emptyList()
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.bootstrapState.value
        assertTrue("Estado debe ser ErrorNoSource", state is SourceBootstrapState.ErrorNoSource)
        val errorState = state as SourceBootstrapState.ErrorNoSource
        assertTrue(errorState.retryable)
        assertFalse(errorState.message.isBlank())
    }

    // ── TEST D: network failure con local source -> UsingLocalCache / Ready -> Home ──
    @Test
    fun testD_networkFailure_withLocalSource_usingLocalCache() = runTest(testDispatcher) {
        val localSource = SourceConfig(
            id = "local_cached",
            name = "Caché Local",
            serverUrl = "http://cached.local:8080",
            username = "u",
            password = "p",
            type = SourceType.XTREAM,
            isActive = true
        )
        val fakeRepo = FakeAuthRepository().apply {
            isLastSyncFromCache = true
            syncCloudSourcesHandler = { listOf(localSource) }
            activeSourceFlow.value = localSource
            allSourcesFlow.value = listOf(localSource)
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.bootstrapState.value
        assertTrue("Estado debe ser UsingLocalCache", state is SourceBootstrapState.UsingLocalCache)
        val cacheState = state as SourceBootstrapState.UsingLocalCache
        assertEquals("local_cached", cacheState.source.id)
        assertTrue(cacheState.warning.contains("catálogo local"))
    }

    // ── TEST E: Retry después de ErrorNoSource segunda petición exitosa -> Ready -> Home ──
    @Test
    fun testE_retryAfterError_succeeds_ready() = runTest(testDispatcher) {
        var attempts = 0
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = {
                attempts++
                if (attempts == 1) {
                    throw IOException("Network timeout")
                } else {
                    listOf(validSource)
                }
            }
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()

        assertTrue("Primer intento debe terminar en Error", viewModel.bootstrapState.value is SourceBootstrapState.ErrorNoSource)

        // Simular que el segundo intento tiene la fuente activa disponible
        fakeRepo.activeSourceFlow.value = validSource
        viewModel.retry()
        advanceUntilIdle()

        assertTrue("Segundo intento debe ser Ready", viewModel.bootstrapState.value is SourceBootstrapState.Ready)
        assertEquals("xtream_valid", (viewModel.bootstrapState.value as SourceBootstrapState.Ready).source.id)
    }

    // ── TEST F: Retry pulsado varias veces rápidamente -> solo una carga activa ──
    @Test
    fun testF_rapidMultipleRetries_singleActiveSync() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { listOf(validSource) }
            activeSourceFlow.value = validSource
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        // Pulsar retry varias veces antes de que avance el despachador
        viewModel.retry()
        viewModel.retry()
        viewModel.retry()

        advanceUntilIdle()

        assertEquals("Debe haberse ejecutado solo 1 sync concurrente", 1, fakeRepo.syncCallCount)
    }

    // ── TEST G: recomposición -> no duplica bootstrap requests ──
    @Test
    fun testG_recomposition_doesNotDuplicateRequests() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { listOf(validSource) }
            activeSourceFlow.value = validSource
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()

        assertEquals(1, fakeRepo.syncCallCount)

        // En Compose, múltiples lecturas del StateFlow por recomposición no relanzan el job del ViewModel
        val state1 = viewModel.bootstrapState.value
        val state2 = viewModel.bootstrapState.value
        assertEquals(state1, state2)
        assertEquals(1, fakeRepo.syncCallCount)
    }

    // ── TEST H: una fuente recién agregada pasa a Ready sin volver a sincronizar la nube ──
    @Test
    fun testH_onSourceAdded_readyWithoutCloudSync() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { emptyList() }
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()
        assertEquals(1, fakeRepo.syncCallCount)

        viewModel.onSourceAdded(validSource)

        val state = viewModel.bootstrapState.value
        assertTrue("La fuente agregada debe estar lista de inmediato", state is SourceBootstrapState.Ready)
        assertEquals("xtream_valid", (state as SourceBootstrapState.Ready).source.id)
        assertEquals("No debe repetirse el bootstrap de la nube", 1, fakeRepo.syncCallCount)
    }

    // ── TEST I: un bootstrap en vuelo no pisa el Ready de la lista recién agregada ──
    @Test
    fun testI_onSourceAdded_cancelsInFlightBootstrap() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = {
                gate.await()
                emptyList()
            }
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        runCurrent() // El bootstrap queda suspendido esperando la red

        val added = validSource.copy(id = "xtream_recien_agregada")
        viewModel.onSourceAdded(added)

        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.bootstrapState.value
        assertTrue("El bootstrap viejo no debe pisar el Ready", state is SourceBootstrapState.Ready)
        assertEquals("xtream_recien_agregada", (state as SourceBootstrapState.Ready).source.id)
    }
}
