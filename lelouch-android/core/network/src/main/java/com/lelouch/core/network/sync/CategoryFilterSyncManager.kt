package com.lelouch.core.network.sync

import android.content.Context
import android.util.Log
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * FASE 36: Sincronización bidireccional de categorías ocultas por lista en Supabase.
 * Permite que cada lista guarde y restaure de manera aislada sus propios filtros:
 * "lo que oculto y no quiero que se vea" en En Vivo, Películas y Series.
 */
object CategoryFilterSyncManager {
    private const val TAG = "CategoryFilterSync"
    private const val PREFS_NAME = "iptv_hidden_categories"
    private const val SUPABASE_BASE_URL = "https://rotupbdeljgfddywryhk.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class FilterSets(
        val live: Set<String>,
        val movies: Set<String>,
        val series: Set<String>
    )

    fun loadFilters(context: Context, sourceId: String): FilterSets {
        val src = sourceId.ifBlank { "default" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return FilterSets(
            live = prefs.getStringSet("${src}_live", emptySet())?.toSet() ?: emptySet(),
            movies = prefs.getStringSet("${src}_movies", emptySet())?.toSet() ?: emptySet(),
            series = prefs.getStringSet("${src}_series", emptySet())?.toSet() ?: emptySet()
        )
    }

    fun saveLocalFilters(
        context: Context,
        sourceId: String,
        live: Set<String>? = null,
        movies: Set<String>? = null,
        series: Set<String>? = null
    ) {
        val src = sourceId.ifBlank { "default" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (live != null) editor.putStringSet("${src}_live", live)
        if (movies != null) editor.putStringSet("${src}_movies", movies)
        if (series != null) editor.putStringSet("${src}_series", series)
        editor.apply()
    }

    fun saveCloudFiltersLocally(
        context: Context,
        sourceId: String,
        cloudCategoriesJson: JsonObject
    ) {
        val liveList = try {
            cloudCategoriesJson["live"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        } catch (_: Exception) { emptySet() }

        val moviesList = try {
            cloudCategoriesJson["movies"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        } catch (_: Exception) { emptySet() }

        val seriesList = try {
            cloudCategoriesJson["series"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        } catch (_: Exception) { emptySet() }

        saveLocalFilters(
            context = context,
            sourceId = sourceId,
            live = liveList,
            movies = moviesList,
            series = seriesList
        )
    }

    fun persistAndSyncFilters(
        context: Context,
        source: SourceConfig?,
        live: Set<String>? = null,
        movies: Set<String>? = null,
        series: Set<String>? = null,
        coroutineScope: CoroutineScope
    ) {
        val srcId = source?.id.orEmpty().ifBlank { "default" }
        saveLocalFilters(context, srcId, live, movies, series)

        if (source == null) return

        val current = loadFilters(context, srcId)
        val finalLive = live ?: current.live
        val finalMovies = movies ?: current.movies
        val finalSeries = series ?: current.series

        coroutineScope.launch(Dispatchers.IO) {
            pushToCloud(source, finalLive, finalMovies, finalSeries)
        }
    }

    private fun pushToCloud(
        source: SourceConfig,
        live: Set<String>,
        movies: Set<String>,
        series: Set<String>
    ) {
        try {
            val payload = buildJsonObject {
                put("hidden_categories", buildJsonObject {
                    put("live", JsonArray(live.map { JsonPrimitive(it) }))
                    put("movies", JsonArray(movies.map { JsonPrimitive(it) }))
                    put("series", JsonArray(series.map { JsonPrimitive(it) }))
                })
            }.toString()

            val isUuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(source.id)
            val filter = when {
                isUuid -> "id=eq.${encode(source.id)}"
                source.type == SourceType.M3U || source.username.isBlank() -> "url=eq.${encode(source.serverUrl)}"
                else -> "server_url=eq.${encode(source.serverUrl.trimEnd('/'))}&username=eq.${encode(source.username.trim())}"
            }

            val req = Request.Builder()
                .url("$SUPABASE_BASE_URL/rest/v1/playlists?$filter")
                .patch(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .build()

            val res = httpClient.newCall(req).execute()
            if (res.isSuccessful) {
                Log.d(TAG, "Filtros de lista sincronizados exitosamente en Supabase: ${source.name}")
            } else {
                Log.w(TAG, "No se pudo sincronizar filtros en Supabase (HTTP ${res.code}): ${res.message}")
            }
            res.close()
        } catch (e: Exception) {
            Log.w(TAG, "Excepción sincronizando filtros con Supabase: ${e.message}")
        }
    }

    private fun encode(s: String): String = try {
        URLEncoder.encode(s, "UTF-8")
    } catch (_: Exception) { s }
}

