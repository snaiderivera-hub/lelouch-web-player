package com.lelouch.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lelouch.core.data.preferences.UserPreferencesDataSource
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * FASE 35 (1.0.97): la lista personalizada retirada ya no se lee ni se sintetiza.
 * Estos tests cubren el corte de resurrección y la reconciliación nube/dispositivo.
 */
class AuthRepositorySyncSourcesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createPreferencesDataSource(): UserPreferencesDataSource {
        val testFile = tempFolder.newFile("test_user_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { testFile }
        return UserPreferencesDataSource(dataStore)
    }

    private class PrefsFixture(
        val dataStore: DataStore<Preferences>,
        val prefs: UserPreferencesDataSource
    )

    /**
     * Escribe el JSON crudo de una instalación anterior, que sí podía contener la fila
     * retirada. `saveAllSources` ya la filtra, así que no sirve para reproducir el estado.
     */
    private fun createLegacyPreferences(
        retired: SourceConfig,
        kept: List<SourceConfig>,
        activeId: String
    ): PrefsFixture {
        val testFile = tempFolder.newFile("test_legacy_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { testFile }
        runBlocking {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("saved_sources_json")] =
                    Json.encodeToString(listOf(retired) + kept)
                prefs[stringPreferencesKey("active_source_id")] = activeId
                prefs[stringPreferencesKey("source_id")] = activeId
            }
        }
        return PrefsFixture(dataStore, UserPreferencesDataSource(dataStore))
    }

    private fun retiredSource() = SourceConfig(
        id = "custom_lelouch",
        name = "⭐ Mi Lista LELOUCH",
        serverUrl = "https://lelouch-web-player.vercel.app/api/playlist?playlist_id=744b1d07-a740-4ea7-a4db-8604ed65f4c5",
        type = SourceType.M3U,
        isActive = true,
        accessToken = null
    )

    private fun realSource(id: String = "xtream_real") = SourceConfig(
        id = id,
        name = "LionTV",
        serverUrl = "http://liontv.example:8080",
        username = "user1",
        password = "pass1",
        type = SourceType.XTREAM,
        isActive = false
    )

    private fun jsonResponse(chainRequest: okhttp3.Request, code: Int, json: String): Response {
        return Response.Builder()
            .request(chainRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error $code")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun legacyRow(
        id: String,
        name: String,
        url: String,
        serverUrl: String,
        username: String,
        password: String = "",
        isActive: Boolean
    ): String =
        """{"id":"$id","name":"$name","url":"$url","server_url":"$serverUrl","username":"$username","password":"$password","is_active":$isActive}"""

    // ── A: una fila legacy de la lista retirada se filtra; las reales se conservan ──
    @Test
    fun testA_legacyRetiredRowFiltered_realSourceKept() = runBlocking {
        val prefs = createPreferencesDataSource()
        val body = "[" + listOf(
            legacyRow(
                id = "c97e05ec-62a1-4e97-8bbc-81245e4c18fc",
                name = "? Mi Lista Personalizada LELOUCH",
                url = "https://lelouch-web-player.vercel.app/api/playlist?playlist_id=744b1d07-a740-4ea7-a4db-8604ed65f4c5",
                serverUrl = "https://lelouch-web-player.vercel.app",
                username = "LELOUCH",
                isActive = true
            ),
            legacyRow(
                id = "8cac2593-0000-0000-0000-000000000001",
                name = "LionTV",
                url = "http://liontv.es:8080/get.php?username=user1&password=pass1",
                serverUrl = "http://liontv.es:8080",
                username = "user1",
                password = "pass1",
                isActive = false
            )
        ).joinToString(",") + "]"

        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain -> jsonResponse(chain.request(), 200, body) }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        assertEquals("Solo debe quedar la lista real", 1, sources.size)
        assertEquals("LionTV", sources.first().name)
        assertEquals(SourceType.XTREAM, sources.first().type)
        assertTrue(sources.first().isActive)
        assertFalse(
            "Nunca debe quedar una fuente de la lista retirada",
            sources.any { it.serverUrl.contains("/api/playlist") }
        )
    }

    // ── B: la app no vuelve a consultar custom_playlists ni playlist_access_tokens ──
    @Test
    fun testB_noCustomEndpointIsEverRead() = runBlocking {
        val prefs = createPreferencesDataSource()
        val requestedUrls = mutableListOf<String>()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrls.add(chain.request().url.toString())
                // Solo la lista retirada, que debe descartarse sin tocar las tablas custom.
                val body = "[" + legacyRow(
                    id = "c97e05ec-62a1-4e97-8bbc-81245e4c18fc",
                    name = "? Mi Lista Personalizada LELOUCH",
                    url = "https://lelouch-web-player.vercel.app/api/playlist?token=abc",
                    serverUrl = "https://lelouch-web-player.vercel.app",
                    username = "LELOUCH",
                    isActive = true
                ) + "]"
                jsonResponse(chain.request(), 200, body)
            }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        assertTrue("La nube solo tenía la lista retirada", sources.isEmpty())
        assertNull(prefs.activeSource.first())
        assertTrue(
            "No debe consultarse custom_playlists",
            requestedUrls.none { it.contains("custom_playlists") }
        )
        assertTrue(
            "No debe consultarse playlist_access_tokens",
            requestedUrls.none { it.contains("playlist_access_tokens") }
        )
    }

    // ── C: nube 404 y sin fuentes locales -> ERROR_LOADING_SOURCES ──
    @Test
    fun testC_networkFailureWithoutLocalSources_throwsErrorLoadingSources() = runBlocking {
        val prefs = createPreferencesDataSource()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain -> jsonResponse(chain.request(), 404, "Not Found") }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        try {
            repository.syncCloudSources()
            fail("Debe lanzar IOException con ERROR_LOADING_SOURCES")
        } catch (e: IOException) {
            assertTrue(
                e.message?.contains("ERROR_LOADING_SOURCES") == true
            )
        }
    }

    // ── D: nube vacía + fuentes locales -> se preservan las locales ──
    @Test
    fun testD_cloudEmpty_preservesLocalSources() = runBlocking {
        val prefs = createPreferencesDataSource()
        val local = SourceConfig(
            id = "xtream_local",
            name = "Mi Xtream",
            serverUrl = "http://iptv.local:8080",
            username = "user",
            password = "pwd",
            type = SourceType.XTREAM,
            isActive = true
        )
        prefs.saveActiveSource(local)

        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain -> jsonResponse(chain.request(), 200, "[]") }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        assertEquals(1, sources.size)
        assertEquals("xtream_local", sources.first().id)
        assertEquals("xtream_local", prefs.activeSource.first()?.id)
        assertFalse(repository.isLastSyncFromCache)
    }

    // ── E: la nube trae UUID, el dispositivo conserva su id local (catálogo Room intacto) ──
    @Test
    fun testE_cloudUuidRowReconciledToLocalId() = runBlocking {
        val prefs = createPreferencesDataSource()
        val local = SourceConfig(
            id = "xtream_${"user1".hashCode()}_${"http://liontv.example:8080".hashCode()}",
            name = "LionTV (local)",
            serverUrl = "http://liontv.example:8080",
            username = "user1",
            password = "pass1",
            type = SourceType.XTREAM,
            isActive = true
        )
        prefs.saveActiveSource(local)

        val body = "[" + legacyRow(
            id = "8cac2593-0000-0000-0000-000000000001",
            name = "LionTV (nube)",
            url = "http://liontv.example:8080/get.php?username=user1&password=pass1",
            serverUrl = "http://liontv.example:8080",
            username = "user1",
            isActive = false
        ) + "]"
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain -> jsonResponse(chain.request(), 200, body) }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        assertEquals(1, sources.size)
        assertEquals("Debe conservarse el id local que indexa Room", local.id, sources.first().id)
        assertEquals("pass1", sources.first().password)
        assertEquals(local.id, prefs.activeSource.first()?.id)
    }

    // ── F: una fuente local que la nube no menciona no desaparece ──
    @Test
    fun testF_localOnlySourcePreservedAlongsideCloudSources() = runBlocking {
        val prefs = createPreferencesDataSource()
        val localOnly = SourceConfig(
            id = "xtream_manual",
            name = "Alta manual",
            serverUrl = "http://manual.example:8080",
            username = "manual",
            password = "p",
            type = SourceType.XTREAM,
            isActive = true
        )
        prefs.saveActiveSource(localOnly)

        val body = "[" + legacyRow(
            id = "11111111-1111-1111-1111-111111111111",
            name = "Otra lista",
            url = "http://otra.example:8080/get.php?username=u2&password=p2",
            serverUrl = "http://otra.example:8080",
            username = "u2",
            isActive = false
        ) + "]"
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain -> jsonResponse(chain.request(), 200, body) }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        assertEquals(2, sources.size)
        assertNotNull(sources.firstOrNull { it.id == "xtream_manual" })
        assertNotNull(sources.firstOrNull { it.serverUrl == "http://otra.example:8080" })
        assertEquals("La fuente activa local debe seguir activa", "xtream_manual", sources.first { it.isActive }.id)
    }

    // ── G: red caída con fuentes locales -> caché local utilizable ──
    @Test
    fun testG_localSourceExists_networkFails_preservesLocal() = runBlocking {
        val prefs = createPreferencesDataSource()
        val existingSource = SourceConfig(
            id = "local_existing",
            name = "Local Cache Source",
            serverUrl = "http://iptv.local:8080",
            username = "user",
            password = "pwd",
            type = SourceType.XTREAM,
            isActive = true
        )
        prefs.saveActiveSource(existingSource)

        val httpClient = OkHttpClient.Builder()
            .addInterceptor { throw SocketTimeoutException("Network down") }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        assertEquals(1, sources.size)
        assertEquals("local_existing", sources.first().id)
        assertEquals("local_existing", prefs.activeSource.first()?.id)
        assertTrue(repository.isLastSyncFromCache)
    }

    // ── H: saveAllSources/allSources descartan cualquier fuente retirada ──
    @Test
    fun testH_retiredSourceIsNeverPersisted() = runBlocking {
        val prefs = createPreferencesDataSource()
        val retired = SourceConfig(
            id = "custom_lelouch",
            name = "⭐ Mi Lista LELOUCH",
            serverUrl = "https://lelouch-web-player.vercel.app/api/playlist?token=tokenReal123",
            type = SourceType.M3U,
            isActive = true,
            accessToken = "tokenReal123"
        )
        val normal = SourceConfig(
            id = "xtream_liontv",
            name = "LionTV",
            serverUrl = "http://liontv.example:8080",
            username = "user1",
            password = "pass1",
            type = SourceType.XTREAM,
            isActive = false
        )
        prefs.saveAllSources(listOf(retired, normal))

        val sources = prefs.allSources.first()

        assertEquals(1, sources.size)
        assertEquals("xtream_liontv", sources.first().id)
    }

    // ── I: activar una fuente retirada no hace nada; una real se activa ──
    @Test
    fun testI_activateSource_ignoresRetiredAndSwitchesNormal() = runBlocking {
        val prefs = createPreferencesDataSource()
        val normalA = SourceConfig(
            id = "xtream_a",
            name = "Lista A",
            serverUrl = "http://a.example:8080",
            username = "a",
            password = "p",
            type = SourceType.XTREAM,
            isActive = true
        )
        val normalB = SourceConfig(
            id = "xtream_b",
            name = "Lista B",
            serverUrl = "http://b.example:8080",
            username = "b",
            password = "p",
            type = SourceType.XTREAM,
            isActive = false
        )
        prefs.saveAllSources(listOf(normalA, normalB))

        assertNull(prefs.setActiveSource("custom_lelouch"))

        val activated = prefs.setActiveSource("xtream_b")
        assertEquals("xtream_b", activated?.id)
        assertEquals("xtream_b", prefs.activeSource.first()?.id)
        assertEquals(1, prefs.allSources.first().count { it.isActive })
    }

    // ── J: la info real de la cuenta Xtream se persiste y se refleja en la fuente activa ──
    @Test
    fun testJ_updateSourceAccountInfo_persistsRealValues() = runBlocking {
        val prefs = createPreferencesDataSource()
        val source = SourceConfig(
            id = "xtream_info",
            name = "Cuenta",
            serverUrl = "http://info.example:8080",
            username = "u",
            password = "p",
            type = SourceType.XTREAM,
            maxConnections = 1,
            activeConnections = 0,
            expireDate = "2026-01-01",
            isActive = true
        )
        prefs.saveActiveSource(source)

        prefs.updateSourceAccountInfo(
            sourceId = source.id,
            activeConnections = 3,
            maxConnections = 5,
            expireDate = "2027-12-31",
            isTrial = false
        )

        val active = prefs.activeSource.first()
        assertNotNull(active)
        assertEquals(3, active?.activeConnections)
        assertEquals(5, active?.maxConnections)
        assertEquals("2027-12-31", active?.expireDate)
    }

    // ── K: el puntero activo apuntaba a la lista retirada -> se usa la primera real ──
    @Test
    fun testK_danglingRetiredActivePointer_fallsBackToRealSource() = runBlocking {
        val fixture = createLegacyPreferences(
            retired = retiredSource(),
            kept = listOf(realSource()),
            activeId = "custom_lelouch"
        )
        val prefs = fixture.prefs

        val all = prefs.allSources.first()
        assertEquals("La lista retirada no debe aparecer", 1, all.size)
        assertEquals("xtream_real", all.first().id)
        assertTrue("La primera fuente real debe quedar activa", all.first().isActive)

        val active = prefs.activeSource.first()
        assertNotNull("La app no debe quedarse sin fuente activa", active)
        assertEquals("xtream_real", active?.id)
    }

    // ── L: purgeRetiredSources borra la fila heredada y repara el puntero activo ──
    @Test
    fun testL_purgeRetiredSources_removesRowAndRepairsActivePointer() = runBlocking {
        val fixture = createLegacyPreferences(
            retired = retiredSource(),
            kept = listOf(realSource()),
            activeId = "custom_lelouch"
        )

        fixture.prefs.purgeRetiredSources()

        val rawJson = fixture.dataStore.data.first()[stringPreferencesKey("saved_sources_json")]
        assertNotNull(rawJson)
        assertFalse(
            "La fila retirada debe desaparecer físicamente del JSON",
            rawJson!!.contains("custom_lelouch")
        )
        assertTrue(rawJson.contains("xtream_real"))

        assertEquals("xtream_real", fixture.prefs.activeSource.first()?.id)
        assertEquals(1, fixture.prefs.allSources.first().size)
    }

    // ── M: purgeRetiredSources no cambia una fuente activa válida ──
    @Test
    fun testM_purgeRetiredSources_keepsValidActiveSource() = runBlocking {
        val active = realSource("xtream_active")
        val other = realSource("xtream_other").copy(
            serverUrl = "http://otra.example:8080",
            username = "otro"
        )
        val fixture = createLegacyPreferences(
            retired = retiredSource(),
            kept = listOf(active, other),
            activeId = "xtream_active"
        )

        fixture.prefs.purgeRetiredSources()

        val all = fixture.prefs.allSources.first()
        assertEquals(2, all.size)
        assertEquals("xtream_active", all.first { it.isActive }.id)
        assertEquals("xtream_active", fixture.prefs.activeSource.first()?.id)
        assertFalse(
            fixture.dataStore.data.first()[stringPreferencesKey("saved_sources_json")]
                .orEmpty().contains("custom_lelouch")
        )
    }
}
