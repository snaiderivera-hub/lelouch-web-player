package com.lelouch.player.bootstrap

import com.lelouch.core.domain.repository.AuthRepository
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
        var syncCloudSourcesHandler: () -> List<SourceConfig> = { emptyList() }
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
        override suspend fun syncCloudSources(): List<SourceConfig> {
            syncCallCount++
            return syncCloudSourcesHandler()
        }
        override suspend fun logout() {}
    }

    private val validCustomSource = SourceConfig(
        id = "custom_lelouch",
        name = "⭐ Mi Lista Personalizada LELOUCH",
        serverUrl = "https://lelouch-web-player.vercel.app/api/playlist?token=valid_tok",
        type = SourceType.M3U,
        isActive = true,
        accessToken = "valid_tok"
    )

    // ── TEST A: custom source válida -> Ready -> Home ──
    @Test
    fun testA_customSourceValid_readyHome() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { listOf(validCustomSource) }
            activeSourceFlow.value = validCustomSource
            allSourcesFlow.value = listOf(validCustomSource)
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.bootstrapState.value
        assertTrue("Estado debe ser Ready", state is SourceBootstrapState.Ready)
        assertEquals("custom_lelouch", (state as SourceBootstrapState.Ready).source.id)
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
                    listOf(validCustomSource)
                }
            }
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()

        assertTrue("Primer intento debe terminar en Error", viewModel.bootstrapState.value is SourceBootstrapState.ErrorNoSource)

        // Simular que el segundo intento tiene la fuente activa disponible
        fakeRepo.activeSourceFlow.value = validCustomSource
        viewModel.retry()
        advanceUntilIdle()

        assertTrue("Segundo intento debe ser Ready", viewModel.bootstrapState.value is SourceBootstrapState.Ready)
        assertEquals("custom_lelouch", (viewModel.bootstrapState.value as SourceBootstrapState.Ready).source.id)
    }

    // ── TEST F: Retry pulsado varias veces rápidamente -> solo una carga activa ──
    @Test
    fun testF_rapidMultipleRetries_singleActiveSync() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { listOf(validCustomSource) }
            activeSourceFlow.value = validCustomSource
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        // Pulsar retry varias veces antes de que avance el despachador
        viewModel.retry()
        viewModel.retry()
        viewModel.retry()

        advanceUntilIdle()

        assertEquals("Debe haberse ejecutado solo 1 sync concurrente", 1, fakeRepo.syncCallCount)
    }

    // ── TEST G: token revocado sin otra fuente -> recovery UI ──
    @Test
    fun testG_tokenRevoked_recoveryUI() = runTest(testDispatcher) {
        val revokedSource = SourceConfig(
            id = "custom_lelouch",
            name = "⭐ Mi Lista Personalizada LELOUCH",
            serverUrl = "https://lelouch-web-player.vercel.app/api/playlist?token=revoked",
            type = SourceType.M3U,
            isActive = false,
            accessToken = null
        )
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { listOf(revokedSource) }
            activeSourceFlow.value = revokedSource
            allSourcesFlow.value = listOf(revokedSource)
        }

        val viewModel = SourceBootstrapViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.bootstrapState.value
        assertTrue("Token revocado debe mostrar ErrorNoSource", state is SourceBootstrapState.ErrorNoSource)
        val errorState = state as SourceBootstrapState.ErrorNoSource
        assertTrue("Mensaje debe advertir sobre token revocado", errorState.message.contains("token"))
    }

    // ── TEST H: recomposición -> no duplica bootstrap requests ──
    @Test
    fun testH_recomposition_doesNotDuplicateRequests() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply {
            syncCloudSourcesHandler = { listOf(validCustomSource) }
            activeSourceFlow.value = validCustomSource
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
}
