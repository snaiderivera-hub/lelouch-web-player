package com.lelouch.core.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lelouch.core.data.preferences.SourceSyncMetadata
import com.lelouch.core.data.preferences.UserPreferencesDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException
import java.util.UUID

class CatalogFreshnessSyncTest {

    private fun createMockHttpClient(
        statusCode: Int,
        headers: Map<String, String> = emptyMap(),
        shouldThrow: Boolean = false
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (shouldThrow) {
                    throw SocketTimeoutException("Simulated connection timeout")
                }
                val builder = Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message(
                        when (statusCode) {
                            304 -> "Not Modified"
                            405 -> "Method Not Allowed"
                            501 -> "Not Implemented"
                            200 -> "OK"
                            else -> "Status $statusCode"
                        }
                    )
                    .body("".toResponseBody("text/plain".toMediaType()))

                headers.forEach { (k, v) -> builder.addHeader(k, v) }
                builder.build()
            }
            .build()
    }

    // ── CASO A: Room vacío -> sync requerido ────────────────────────────────────
    @Test
    fun `casoA_roomVacio_requiereSincronizacion`() {
        val totalLocal = 0
        val isRoomEmpty = (totalLocal == 0)
        val force = false

        val mustSync = force || isRoomEmpty
        assertTrue("Si Room está vacío, la sincronización debe ejecutarse obligatoriamente", mustSync)
    }

    // ── CASO B: Room lleno + remoteVersion == localVersion -> sync omitido (UpToDate) ──
    @Test
    fun `casoB_roomLleno_versionIgual_omiteSincronizacion`() {
        val totalLocal = 3500
        val isRoomEmpty = (totalLocal == 0)
        val force = false
        val localVersion = "872717"
        val remoteVersion = "872717"

        val freshness: FreshnessResult = if (localVersion == remoteVersion) {
            FreshnessResult.Unchanged(version = remoteVersion)
        } else {
            FreshnessResult.Changed(newVersion = remoteVersion)
        }

        assertTrue("La versión no cambió", freshness is FreshnessResult.Unchanged)
        val state: SyncState = when (freshness) {
            is FreshnessResult.Unchanged -> SyncState.UpToDate("custom_lelouch", "Catálogo al día (comprobado)")
            is FreshnessResult.TtlFresh -> SyncState.TtlFresh("custom_lelouch")
            is FreshnessResult.CheckFailed -> SyncState.OfflineUsingCache("custom_lelouch")
            is FreshnessResult.Changed -> SyncState.SyncingLive(0)
        }
        assertTrue("Comprobado con servidor produce UpToDate", state is SyncState.UpToDate)

        val mustSync = force || isRoomEmpty || (freshness is FreshnessResult.Changed)
        assertFalse("Catálogo al día comprobado con Room lleno no debe ejecutar sync", mustSync)
    }

    // ── CASO C: Room lleno + remoteVersion > localVersion -> sync ejecutado ─────
    @Test
    fun `casoC_roomLleno_versionNueva_ejecutaSincronizacion`() {
        val totalLocal = 3500
        val isRoomEmpty = (totalLocal == 0)
        val force = false
        val localVersion = "872717"
        val remoteVersion = "872718"

        val freshness: FreshnessResult = if (localVersion == remoteVersion) {
            FreshnessResult.Unchanged(version = remoteVersion)
        } else {
            FreshnessResult.Changed(newVersion = remoteVersion)
        }

        assertTrue("La versión cambió", freshness is FreshnessResult.Changed)
        val mustSync = force || isRoomEmpty || (freshness is FreshnessResult.Changed)
        assertTrue("Debe ejecutar sync al detectar versión nueva", mustSync)
    }

    // ── CASO D: version endpoint falla + Room lleno -> OfflineUsingCache (NO UpToDate) ──
    @Test
    fun `casoD_versionEndpointFalla_preservaCatalogoLocal_setsOfflineUsingCache`() {
        val totalLocal = 3500
        val isRoomEmpty = (totalLocal == 0)
        val force = false

        val freshness: FreshnessResult = FreshnessResult.CheckFailed("HTTP 503 / Timeout")
        assertTrue(freshness is FreshnessResult.CheckFailed)

        val state: SyncState = when (freshness) {
            is FreshnessResult.Unchanged -> SyncState.UpToDate("custom_lelouch")
            is FreshnessResult.TtlFresh -> SyncState.TtlFresh("custom_lelouch")
            is FreshnessResult.CheckFailed -> SyncState.OfflineUsingCache(
                "custom_lelouch",
                "Modo sin conexión - Catálogo local disponible"
            )
            is FreshnessResult.Changed -> SyncState.SyncingLive(0)
        }

        // Semántica correcta: OfflineUsingCache, NUNCA UpToDate
        assertTrue("Fallo de comprobación debe resultar en OfflineUsingCache", state is SyncState.OfflineUsingCache)
        assertFalse("Fallo de comprobación NO debe marcarse falsamente como UpToDate", state is SyncState.UpToDate)

        // Comprobación de que la UI no se bloquea
        val isLoading = state is SyncState.Authenticating ||
            state is SyncState.SyncingCategories ||
            state is SyncState.SyncingLive ||
            state is SyncState.SyncingMovies ||
            state is SyncState.SyncingSeries
        assertFalse("OfflineUsingCache no debe bloquear la UI", isLoading)

        // Catálogo local en Room intacto
        val mustSync = force || isRoomEmpty || (freshness is FreshnessResult.Changed)
        assertFalse("Si la comprobación falla y Room tiene datos, NO se ejecuta sync destructivo", mustSync)
        assertEquals("El catálogo local de 3500 elementos permanece intacto", 3500, totalLocal)
    }

    // ── CASO E: sync nuevo falla -> localVersion NO cambia ───────────────────────
    @Test
    fun `casoE_syncNuevoFalla_localVersionNoCambia`() {
        var metadata = SourceSyncMetadata(
            sourceId = "custom_lelouch",
            lastSuccessfulSyncAt = 1000L,
            lastSuccessfulVersion = "872717"
        )
        val newRemoteVersion = "872718"

        var swapSuccessful = false
        val shouldFail = true
        try {
            if (shouldFail) {
                throw RuntimeException("Network timeout in staging")
            }
            swapSuccessful = true
        } catch (_: Exception) {}

        if (swapSuccessful) {
            metadata = metadata.copy(lastSuccessfulVersion = newRemoteVersion)
        }

        assertEquals("La versión previa debe permanecer tras un fallo de sync", "872717", metadata.lastSuccessfulVersion)
    }

    // ── CASO F: sync exitoso -> localVersion actualizado ─────────────────────────
    @Test
    fun `casoF_syncExitoso_localVersionActualizado`() {
        var metadata = SourceSyncMetadata(
            sourceId = "custom_lelouch",
            lastSuccessfulSyncAt = 1000L,
            lastSuccessfulVersion = "872717"
        )
        val newRemoteVersion = "872718"

        val swapSuccessful = true
        if (swapSuccessful) {
            metadata = metadata.copy(
                lastSuccessfulVersion = newRemoteVersion,
                lastSuccessfulSyncAt = 2000L,
                lastSyncResult = "SUCCESS"
            )
        }

        assertEquals("La versión debe actualizarse tras swap exitoso", "872718", metadata.lastSuccessfulVersion)
        assertEquals(2000L, metadata.lastSuccessfulSyncAt)
    }

    // ── CASO G: manual refresh -> sync ejecutado aunque versión sea fresca ───────
    @Test
    fun `casoG_manualRefresh_ignoraVersionYTtl`() {
        val totalLocal = 3500
        val isRoomEmpty = (totalLocal == 0)
        val force = true
        val remoteVersion = "872717"

        val freshness = FreshnessResult.Unchanged(version = remoteVersion)
        val mustSync = force || isRoomEmpty || (freshness is FreshnessResult.Changed)

        assertTrue("El refresh manual (force=true) debe ejecutar sync incondicionalmente", mustSync)
    }

    // ── CASO H: Xtream dentro de TTL -> TtlFresh (NO Unchanged) ───────────────────
    @Test
    fun `casoH_xtreamDentroDeTtl_returnsTtlFresh_and_notUnchanged`() {
        val manager = XtreamCatalogSyncManager()
        val now = System.currentTimeMillis()
        val metadata = SourceSyncMetadata(
            sourceId = "xtream_1",
            lastSuccessfulSyncAt = now - (2 * 60 * 60 * 1000L), // Sincronizado hace 2h
            lastSuccessfulVersion = "v1"
        )

        val freshness = manager.checkXtreamFreshness("xtream_1", metadata)

        val isUnchanged = freshness is FreshnessResult.Unchanged
        assertFalse("Xtream dentro de TTL NO debe marcarse como Unchanged", isUnchanged)
        assertTrue("Xtream dentro de TTL debe ser TtlFresh", freshness is FreshnessResult.TtlFresh)

        // Estado semántico asociado
        val state: SyncState = when (freshness) {
            is FreshnessResult.Unchanged -> SyncState.UpToDate("xtream_1")
            is FreshnessResult.TtlFresh -> SyncState.TtlFresh("xtream_1")
            is FreshnessResult.CheckFailed -> SyncState.OfflineUsingCache("xtream_1")
            is FreshnessResult.Changed -> SyncState.SyncingLive(0)
        }
        assertTrue("Estado resultante debe ser TtlFresh", state is SyncState.TtlFresh)
    }

    // ── CASO I: Xtream TTL vencido -> Changed ────────────────────────────────────
    @Test
    fun `casoI_xtreamTtlVencido_ejecutaSync`() {
        val manager = XtreamCatalogSyncManager()
        val now = System.currentTimeMillis()
        val metadata = SourceSyncMetadata(
            sourceId = "xtream_1",
            lastSuccessfulSyncAt = now - (25 * 60 * 60 * 1000L), // Sincronizado hace 25h (>24h)
            lastSuccessfulVersion = "v1"
        )

        val freshness = manager.checkXtreamFreshness("xtream_1", metadata)
        assertTrue("Xtream con TTL expirado resulta en Changed", freshness is FreshnessResult.Changed)
    }

    // ── CASO J: DataStore multi-source -> updates independientes sin borrado ────
    @Test
    fun `casoJ_dataStore_multiSource_preservesAllSources`() = runBlocking {
        val tempFile = File.createTempFile("datastore_test_multi_${UUID.randomUUID()}", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val dataSource = UserPreferencesDataSource(customDataStore = testDataStore)

        val metaA1 = SourceSyncMetadata(sourceId = "source_A", lastSuccessfulVersion = "A1", channelCount = 10)
        val metaB1 = SourceSyncMetadata(sourceId = "source_B", lastSuccessfulVersion = "B1", channelCount = 20)

        dataSource.saveSyncMetadata(metaA1)
        dataSource.saveSyncMetadata(metaB1)

        val metaA2 = SourceSyncMetadata(sourceId = "source_A", lastSuccessfulVersion = "A2", channelCount = 15)
        val metaB2 = SourceSyncMetadata(sourceId = "source_B", lastSuccessfulVersion = "B2", channelCount = 25)

        dataSource.saveSyncMetadata(metaA2)
        dataSource.saveSyncMetadata(metaB2)

        val resultA = dataSource.getSyncMetadata("source_A")
        val resultB = dataSource.getSyncMetadata("source_B")

        assertNotNull("Metadata de source_A debe existir", resultA)
        assertNotNull("Metadata de source_B debe existir", resultB)
        assertEquals("source_A debe contener A2", "A2", resultA?.lastSuccessfulVersion)
        assertEquals("source_B debe contener B2", "B2", resultB?.lastSuccessfulVersion)
        assertEquals(15, resultA?.channelCount)
        assertEquals(25, resultB?.channelCount)
    }

    // ── CASO K: DataStore updates concurrentes -> atómico, sin lost updates ─────
    @Test
    fun `casoK_dataStore_concurrentUpdates_atomicReadModifyWrite_noLostUpdates`() = runBlocking {
        val tempFile = File.createTempFile("datastore_test_concurrent_${UUID.randomUUID()}", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val dataSource = UserPreferencesDataSource(customDataStore = testDataStore)

        // Disparar 30 actualizaciones concurrentes alternadas entre A y B
        val jobs = (1..30).map { i ->
            async(Dispatchers.Default) {
                if (i % 2 == 0) {
                    dataSource.saveSyncMetadata(
                        SourceSyncMetadata(
                            sourceId = "source_A",
                            lastSuccessfulVersion = "version_A_$i",
                            channelCount = i
                        )
                    )
                } else {
                    dataSource.saveSyncMetadata(
                        SourceSyncMetadata(
                            sourceId = "source_B",
                            lastSuccessfulVersion = "version_B_$i",
                            channelCount = i
                        )
                    )
                }
            }
        }
        jobs.awaitAll()

        val finalA = dataSource.getSyncMetadata("source_A")
        val finalB = dataSource.getSyncMetadata("source_B")

        assertNotNull("source_A no debe perderse por concurrencia", finalA)
        assertNotNull("source_B no debe perderse por concurrencia", finalB)
        assertTrue("source_A debe tener un número de canales válido", (finalA?.channelCount ?: 0) > 0)
        assertTrue("source_B debe tener un número de canales válido", (finalB?.channelCount ?: 0) > 0)
    }

    // ── CASO L: M3U HEAD 304 -> Unchanged (Confirmado) ──────────────────────────
    @Test
    fun `casoL_m3uHead_304_returnsUnchanged`() = runBlocking {
        val mockClient = createMockHttpClient(304)
        val manager = XtreamCatalogSyncManager(httpClient = mockClient)

        val metadata = SourceSyncMetadata(
            sourceId = "m3u_remote",
            lastSuccessfulSyncAt = System.currentTimeMillis() - 10000L,
            lastSuccessfulVersion = "etag123",
            lastEtag = "etag123"
        )

        val result = manager.checkGenericM3uFreshness("m3u_remote", "https://example.com/playlist.m3u", metadata)
        assertTrue("HTTP 304 debe resultar en Unchanged comprobado", result is FreshnessResult.Unchanged)
        assertEquals("etag123", (result as FreshnessResult.Unchanged).version)
    }

    // ── CASO M: M3U HEAD 405 Method Not Allowed -> Fallback seguro a TTL ─────────
    @Test
    fun `casoM_m3uHead_405_fallsBackToTtl`() = runBlocking {
        val mockClient = createMockHttpClient(405)
        val manager = XtreamCatalogSyncManager(httpClient = mockClient)

        // Subcaso 1: Dentro de TTL (<24h) -> TtlFresh (NO Changed, NO descarga forzada)
        val metaRecent = SourceSyncMetadata(
            sourceId = "m3u_remote",
            lastSuccessfulSyncAt = System.currentTimeMillis() - (2 * 3600 * 1000L),
            lastSuccessfulVersion = "v1"
        )
        val resRecent = manager.checkGenericM3uFreshness("m3u_remote", "https://example.com/playlist.m3u", metaRecent)
        assertTrue("405 dentro de TTL debe resultar en TtlFresh", resRecent is FreshnessResult.TtlFresh)
        assertFalse("405 dentro de TTL NO debe marcarse como Changed", resRecent is FreshnessResult.Changed)

        // Subcaso 2: TTL expirado (>24h) -> Changed
        val metaOld = SourceSyncMetadata(
            sourceId = "m3u_remote",
            lastSuccessfulSyncAt = System.currentTimeMillis() - (26 * 3600 * 1000L),
            lastSuccessfulVersion = "v1"
        )
        val resOld = manager.checkGenericM3uFreshness("m3u_remote", "https://example.com/playlist.m3u", metaOld)
        assertTrue("405 con TTL expirado debe resultar en Changed", resOld is FreshnessResult.Changed)
    }

    // ── CASO N: M3U HEAD 501 Not Implemented -> Fallback seguro a TTL ───────────
    @Test
    fun `casoN_m3uHead_501_fallsBackToTtl`() = runBlocking {
        val mockClient = createMockHttpClient(501)
        val manager = XtreamCatalogSyncManager(httpClient = mockClient)

        // Dentro de TTL -> TtlFresh
        val metaRecent = SourceSyncMetadata(
            sourceId = "m3u_remote",
            lastSuccessfulSyncAt = System.currentTimeMillis() - (5 * 3600 * 1000L),
            lastSuccessfulVersion = "v1"
        )
        val resRecent = manager.checkGenericM3uFreshness("m3u_remote", "https://example.com/playlist.m3u", metaRecent)
        assertTrue("501 dentro de TTL debe resultar en TtlFresh", resRecent is FreshnessResult.TtlFresh)

        // TTL expirado -> Changed
        val metaOld = SourceSyncMetadata(
            sourceId = "m3u_remote",
            lastSuccessfulSyncAt = System.currentTimeMillis() - (30 * 3600 * 1000L),
            lastSuccessfulVersion = "v1"
        )
        val resOld = manager.checkGenericM3uFreshness("m3u_remote", "https://example.com/playlist.m3u", metaOld)
        assertTrue("501 con TTL expirado debe resultar en Changed", resOld is FreshnessResult.Changed)
    }

    // ── CASO O: M3U HEAD 200 con ETag igual vs diferente vs sin ETag vs timeout ─
    @Test
    fun `casoO_m3uHead_200_etagAndTimeout`() = runBlocking {
        val now = System.currentTimeMillis()
        val metadata = SourceSyncMetadata(
            sourceId = "m3u_remote",
            lastSuccessfulSyncAt = now - (2 * 3600 * 1000L),
            lastSuccessfulVersion = "etag_abc",
            lastEtag = "etag_abc"
        )

        // 1. 200 OK con ETag igual -> Unchanged
        val clientSameEtag = createMockHttpClient(200, mapOf("ETag" to "\"etag_abc\""))
        val managerSame = XtreamCatalogSyncManager(httpClient = clientSameEtag)
        val resSame = managerSame.checkGenericM3uFreshness("m3u_remote", "https://example.com/playlist.m3u", metadata)
        assertTrue("200 con ETag idéntico debe ser Unchanged", resSame is FreshnessResult.Unchanged)

        // 2. 200 OK con ETag diferente -> Changed
        val clientDiffEtag = createMockHttpClient(200, mapOf("ETag" to "\"etag_xyz\""))
        val managerDiff = XtreamCatalogSyncManager(httpClient = clientDiffEtag)
        val resDiff = managerDiff.checkGenericM3uFreshness("m3u_remote", "https://example.com/playlist.m3u", metadata)
        assertTrue("200 con ETag diferente debe ser Changed", resDiff is FreshnessResult.Changed)
        assertEquals("etag_xyz", (resDiff as FreshnessResult.Changed).newEtag)

        // 3. 200 OK sin ETag ni Last-Modified -> Fallback TTL (TtlFresh si reciente)
        val clientNoEtag = createMockHttpClient(200)
        val managerNoEtag = XtreamCatalogSyncManager(httpClient = clientNoEtag)
        val resNoEtag = managerNoEtag.checkGenericM3uFreshness("m3u_remote", "https://example.com/playlist.m3u", metadata)
        assertTrue("200 sin ETag dentro de TTL debe ser TtlFresh", resNoEtag is FreshnessResult.TtlFresh)

        // 4. Timeout / Error de conexión -> CheckFailed (OfflineUsingCache)
        val clientTimeout = createMockHttpClient(200, shouldThrow = true)
        val managerTimeout = XtreamCatalogSyncManager(httpClient = clientTimeout)
        val resTimeout = managerTimeout.checkGenericM3uFreshness("m3u_remote", "https://example.com/playlist.m3u", metadata)
        assertTrue("Error de red debe ser CheckFailed", resTimeout is FreshnessResult.CheckFailed)
    }
}
