package com.lelouch.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lelouch.core.data.preferences.UserPreferencesDataSource
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

class AuthRepositorySyncSourcesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createPreferencesDataSource(): UserPreferencesDataSource {
        val testFile = tempFolder.newFile("test_user_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { testFile }
        return UserPreferencesDataSource(dataStore)
    }

    private fun createMockHttpClient(
        handler: (url: String) -> Response
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                handler(url)
            }
            .build()
    }

    private fun jsonResponse(chainRequest: okhttp3.Request, code: Int, json: String): Response {
        return Response.Builder()
            .request(chainRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error $code")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    // ── TEST A: legacy falla, custom válida -> cargar custom ──
    @Test
    fun testA_legacyFails_customValid_loadsCustom() = runBlocking {
        val prefs = createPreferencesDataSource()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.contains("/rest/v1/playlists") -> {
                        jsonResponse(chain.request(), 500, "{\"error\": \"Internal Server Error\"}")
                    }
                    url.contains("/rest/v1/custom_playlists") -> {
                        val body = """
                            [{"id":"c9da4a22-534c-41f9-af70-42ee9f6682df","name":"Mi Lista Personalizada LELOUCH","enabled":true,"is_active":true}]
                        """.trimIndent()
                        jsonResponse(chain.request(), 200, body)
                    }
                    url.contains("/rest/v1/playlist_access_tokens") -> {
                        val body = """
                            [{"playlist_id":"c9da4a22-534c-41f9-af70-42ee9f6682df","token_preview":"pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R","is_active":true,"enabled":true}]
                        """.trimIndent()
                        jsonResponse(chain.request(), 200, body)
                    }
                    else -> jsonResponse(chain.request(), 404, "Not Found")
                }
            }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        assertEquals(1, sources.size)
        val source = sources.first()
        assertEquals("custom_lelouch", source.id)
        assertTrue(source.serverUrl.contains("pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R"))
        assertTrue("La fuente debe estar activa", source.isActive)

        val activeSource = prefs.activeSource.first()
        assertNotNull("Debe existir activeSource", activeSource)
        assertEquals("custom_lelouch", activeSource?.id)
    }

    // ── TEST B: legacy válida, custom válida -> no duplicar fuentes ──
    @Test
    fun testB_legacyValid_customValid_noDuplicates() = runBlocking {
        val prefs = createPreferencesDataSource()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.contains("/rest/v1/playlists") -> {
                        val body = """
                            [
                                {"id":"e8748a42","name":"Mi Lista Personalizada LELOUCH","url":"https://lelouch-web-player.vercel.app/api/playlist?token=pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R","server_url":"https://lelouch-web-player.vercel.app","username":"LELOUCH","password":"","is_active":true},
                                {"id":"8cac2593","name":"LionTV","url":"http://liontv.es:8080/get.php?username=user1&password=pass1","server_url":"http://liontv.es:8080","username":"user1","password":"        ","is_active":false}
                            ]
                        """.trimIndent()
                        jsonResponse(chain.request(), 200, body)
                    }
                    url.contains("/rest/v1/custom_playlists") -> {
                        val body = """
                            [{"id":"c9da4a22-534c-41f9-af70-42ee9f6682df","name":"Mi Lista Personalizada LELOUCH","enabled":true,"is_active":true}]
                        """.trimIndent()
                        jsonResponse(chain.request(), 200, body)
                    }
                    url.contains("/rest/v1/playlist_access_tokens") -> {
                        val body = """
                            [{"playlist_id":"c9da4a22-534c-41f9-af70-42ee9f6682df","token_preview":"pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R","is_active":true,"enabled":true}]
                        """.trimIndent()
                        jsonResponse(chain.request(), 200, body)
                    }
                    else -> jsonResponse(chain.request(), 404, "Not Found")
                }
            }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        // 1 personalizada consolidada + 1 LionTV = exactamente 2 fuentes, sin duplicar la personalizada
        assertEquals(2, sources.size)
        val customSources = sources.filter { it.id == "custom_lelouch" }
        assertEquals("Debe existir exactamente 1 fuente personalizada consolidada", 1, customSources.size)
    }

    // ── TEST C: legacy falla, custom vacía -> SUCCESS_EMPTY ──
    @Test
    fun testC_legacyFails_customEmpty_successEmpty() = runBlocking {
        val prefs = createPreferencesDataSource()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.contains("/rest/v1/playlists") -> {
                        jsonResponse(chain.request(), 404, "Not Found")
                    }
                    url.contains("/rest/v1/custom_playlists") -> {
                        jsonResponse(chain.request(), 200, "[]")
                    }
                    else -> jsonResponse(chain.request(), 200, "[]")
                }
            }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        // Nube respondió 200 pero vacía -> SUCCESS_EMPTY
        assertTrue("Debe retornar lista vacía", sources.isEmpty())
        assertNull("No debe haber activeSource", prefs.activeSource.first())
    }

    // ── TEST D: custom endpoint timeout -> ERROR_LOADING_SOURCES ──
    @Test
    fun testD_customEndpointTimeout_throwsErrorLoadingSources() = runBlocking {
        val prefs = createPreferencesDataSource()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("/rest/v1/playlists")) {
                    jsonResponse(chain.request(), 500, "Server Error")
                } else {
                    throw SocketTimeoutException("Connection timed out")
                }
            }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        try {
            repository.syncCloudSources()
            fail("Debe lanzar IOException con ERROR_LOADING_SOURCES")
        } catch (e: IOException) {
            assertTrue(
                "El mensaje debe indicar ERROR_LOADING_SOURCES",
                e.message?.contains("ERROR_LOADING_SOURCES") == true
            )
        }
    }

    // ── TEST E: token revocado -> no activar playlist ──
    @Test
    fun testE_tokenRevoked_doesNotActivatePlaylist() = runBlocking {
        val prefs = createPreferencesDataSource()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.contains("/rest/v1/playlists") -> jsonResponse(chain.request(), 404, "Not Found")
                    url.contains("/rest/v1/custom_playlists") -> {
                        val body = """
                            [{"id":"revoked-playlist-id","name":"Revoked Playlist","enabled":true,"is_active":true}]
                        """.trimIndent()
                        jsonResponse(chain.request(), 200, body)
                    }
                    url.contains("/rest/v1/playlist_access_tokens") -> {
                        // Token con is_active=false y enabled=false (revocado)
                        val body = """
                            [{"playlist_id":"revoked-playlist-id","token_preview":"revoked_tok","is_active":false,"enabled":false}]
                        """.trimIndent()
                        jsonResponse(chain.request(), 200, body)
                    }
                    else -> jsonResponse(chain.request(), 404, "Not Found")
                }
            }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        val source = sources.firstOrNull()
        assertNotNull(source)
        assertFalse("La fuente con token revocado NO debe estar activa", source!!.isActive)
        assertNull("No debe haber activeSource configurado en preferencias", prefs.activeSource.first())
    }

    // ── TEST F: token válido -> activar correctamente ──
    @Test
    fun testF_tokenValid_activatesCorrectly() = runBlocking {
        val prefs = createPreferencesDataSource()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.contains("/rest/v1/playlists") -> jsonResponse(chain.request(), 404, "Not Found")
                    url.contains("/rest/v1/custom_playlists") -> {
                        val body = """
                            [{"id":"valid-pl-id","name":"Valid Playlist","enabled":true,"is_active":true}]
                        """.trimIndent()
                        jsonResponse(chain.request(), 200, body)
                    }
                    url.contains("/rest/v1/playlist_access_tokens") -> {
                        val body = """
                            [{"playlist_id":"valid-pl-id","token_preview":"valid_tok_abc123","is_active":true,"enabled":true}]
                        """.trimIndent()
                        jsonResponse(chain.request(), 200, body)
                    }
                    else -> jsonResponse(chain.request(), 404, "Not Found")
                }
            }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        assertEquals(1, sources.size)
        val source = sources.first()
        assertTrue("La fuente con token válido debe estar activa", source.isActive)
        assertEquals("custom_lelouch", prefs.activeSource.first()?.id)
    }

    // ── TEST G: ya existe source local, red falla -> conservar source local usable ──
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
            .addInterceptor {
                throw SocketTimeoutException("Network down")
            }
            .build()

        val repository = AuthRepositoryImpl(prefs, httpClient)
        val sources = repository.syncCloudSources()

        assertEquals("Debe conservar la fuente local", 1, sources.size)
        assertEquals("local_existing", sources.first().id)
        assertEquals("local_existing", prefs.activeSource.first()?.id)
    }
}
