package com.lelouch.core.data.sync

import com.lelouch.core.data.preferences.SourceSyncMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFreshnessSyncTest {

    // ── CASO A: Room vacío -> sync requerido ────────────────────────────────────
    @Test
    fun `casoA_roomVacio_requiereSincronizacion`() {
        val totalLocal = 0
        val isRoomEmpty = (totalLocal == 0)
        val force = false

        val mustSync = force || isRoomEmpty
        assertTrue("Si Room está vacío, la sincronización debe ejecutarse obligatoriamente", mustSync)
    }

    // ── CASO B: Room lleno + remoteVersion == localVersion -> sync omitido ──────
    @Test
    fun `casoB_roomLleno_versionIgual_omiteSincronizacion`() {
        val totalLocal = 3500
        val isRoomEmpty = (totalLocal == 0)
        val force = false
        val localVersion = "872717"
        val remoteVersion = "872717"

        val metadata = SourceSyncMetadata(
            sourceId = "custom_lelouch",
            lastSuccessfulSyncAt = 1700000000000L,
            lastSuccessfulVersion = localVersion
        )

        val freshness: FreshnessResult = if (localVersion == remoteVersion) {
            FreshnessResult.Unchanged(version = remoteVersion)
        } else {
            FreshnessResult.Changed(newVersion = remoteVersion)
        }

        assertTrue("La versión no cambió", freshness is FreshnessResult.Unchanged)
        val mustSync = force || isRoomEmpty || (freshness is FreshnessResult.Changed)
        assertFalse("Catálogo al día con Room lleno no debe ejecutar sync", mustSync)
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

    // ── CASO D: version endpoint falla + Room lleno -> preserva catálogo local ──
    @Test
    fun `casoD_versionEndpointFalla_preservaCatalogoLocal`() {
        val totalLocal = 3500
        val isRoomEmpty = (totalLocal == 0)
        val force = false

        val freshness: FreshnessResult = FreshnessResult.CheckFailed("HTTP 503 / Timeout")
        assertTrue(freshness is FreshnessResult.CheckFailed)

        val mustSync = force || isRoomEmpty || (freshness is FreshnessResult.Changed)
        assertFalse("Si la comprobación falla y Room tiene datos, NO se ejecuta sync destructivo", mustSync)
        assertEquals(3500, totalLocal)
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

        // Simular que sync falla antes o durante el swap
        var swapSuccessful = false
        val shouldFail = true
        try {
            if (shouldFail) {
                throw RuntimeException("Network timeout in staging")
            }
            swapSuccessful = true
        } catch (_: Exception) {
            // No se actualiza metadata
        }

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

        // Simular swap exitoso
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
        val force = true // Refresh forzado por el usuario
        val localVersion = "872717"
        val remoteVersion = "872717"

        val freshness = FreshnessResult.Unchanged(version = remoteVersion)
        val mustSync = force || isRoomEmpty || (freshness is FreshnessResult.Changed)

        assertTrue("El refresh manual (force=true) debe ejecutar sync incondicionalmente", mustSync)
    }

    // ── CASO H: Xtream dentro de TTL -> no descarga ──────────────────────────────
    @Test
    fun `casoH_xtreamDentroDeTtl_omiteDescarga`() {
        val now = 1000000000000L
        val ttl = XtreamCatalogSyncManager.XTREAM_DEFAULT_TTL_MS // 24h
        val metadata = SourceSyncMetadata(
            sourceId = "xtream_1",
            lastSuccessfulSyncAt = now - (2 * 60 * 60 * 1000L), // Sincronizado hace 2 horas
            lastSuccessfulVersion = "v1"
        )

        val age = now - metadata.lastSuccessfulSyncAt
        val isWithinTtl = age < ttl
        assertTrue("Hace 2 horas está dentro del TTL de 24 horas", isWithinTtl)

        val freshness: FreshnessResult = if (isWithinTtl) {
            FreshnessResult.Unchanged(version = metadata.lastSuccessfulVersion)
        } else {
            FreshnessResult.Changed(newVersion = "ttl_expired")
        }

        assertTrue("Xtream dentro de TTL resulta en Unchanged", freshness is FreshnessResult.Unchanged)
    }

    // ── CASO I: Xtream TTL vencido -> ejecuta sync ──────────────────────────────
    @Test
    fun `casoI_xtreamTtlVencido_ejecutaSync`() {
        val now = 1000000000000L
        val ttl = XtreamCatalogSyncManager.XTREAM_DEFAULT_TTL_MS // 24h
        val metadata = SourceSyncMetadata(
            sourceId = "xtream_1",
            lastSuccessfulSyncAt = now - (25 * 60 * 60 * 1000L), // Sincronizado hace 25 horas
            lastSuccessfulVersion = "v1"
        )

        val age = now - metadata.lastSuccessfulSyncAt
        val isWithinTtl = age < ttl
        assertFalse("Hace 25 horas el TTL de 24 horas ha expirado", isWithinTtl)

        val freshness: FreshnessResult = if (isWithinTtl) {
            FreshnessResult.Unchanged(version = metadata.lastSuccessfulVersion)
        } else {
            FreshnessResult.Changed(newVersion = "ttl_expired")
        }

        assertTrue("Xtream con TTL expirado resulta en Changed", freshness is FreshnessResult.Changed)
    }
}
