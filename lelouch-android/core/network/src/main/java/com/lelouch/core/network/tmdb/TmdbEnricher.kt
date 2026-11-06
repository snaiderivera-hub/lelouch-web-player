package com.lelouch.core.network.tmdb

import com.lelouch.core.model.Episode
import com.lelouch.core.model.VodMovie
import com.lelouch.core.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object TmdbEnricher {

    private const val TMDB_API_KEY = "d10dcabb94ff39a850b212ec0bcb845d"
    private const val TMDB_BEARER_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJkMTBkY2FiYjk0ZmYzOWE4NTBiMjEyZWMwYmNiODQ1ZCIsIm5iZiI6MTc5MzU5MjMwMy4xMzkwMDAyLCJzdWIiOiI2YWU4MGJlZjEwZDhiNDViMGZmMWM2OWQiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.cc0EDwLmlZmu4KGiig39H6AiqlVgjzfKOsDybFtJtE8"
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500"
    private const val IMAGE_BASE_W780 = "https://image.tmdb.org/t/p/w780"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    // Caché en memoria para evitar llamadas redundantes durante la sesión
    private val tvShowCache = ConcurrentHashMap<String, TmdbShowData>()
    private val seasonEpisodesCache = ConcurrentHashMap<String, Map<Int, TmdbEpisodeData>>()
    private val movieCache = ConcurrentHashMap<String, TmdbMovieData>()

    data class TmdbShowData(
        val id: Int,
        val name: String,
        val overview: String,
        val posterUrl: String?,
        val backdropUrl: String?,
        val rating: Double?,
        val firstAirDate: String?,
        val cast: String? = null,
        val director: String? = null
    )

    data class TmdbEpisodeData(
        val episodeNumber: Int,
        val name: String,
        val overview: String,
        val stillUrl: String?,
        val rating: Double?,
        val airDate: String?
    )

    data class TmdbMovieData(
        val id: Int,
        val title: String,
        val overview: String,
        val posterUrl: String?,
        val backdropUrl: String?,
        val rating: Double?,
        val releaseDate: String?,
        val cast: String? = null,
        val director: String? = null
    )

    fun cleanTitle(rawName: String): String {
        var title = rawName
        // Eliminar prefijos de idioma / calidad / servidor IPTV comunes
        title = title.replace(Regex("""^(?i)\s*(\[[^\]]*\]|\([^\)]*\)|[A-Z0-9]{2,4}\s*[:|•\-_/])+\s*"""), " ")
        title = title.replace(Regex("""^(?i)\s*(ES|LAT|LATINO|ESP|SPA|EN|ENG|MX|USA|VOD|CINE|PELICULA|PELICULAS|PELIS|ESTRENO|ESTRENOS|4K|FHD|HD)\s*[:|•\-_/]\s*"""), " ")
        // Eliminar corchetes como [1080p], [4K], etc.
        title = title.replace(Regex("""\[[^\]]*\]"""), " ")
        // Eliminar año como (2021)
        title = title.replace(Regex("""\(\s*\d{4}\s*\)"""), " ")
        // Eliminar paréntesis con etiquetas de idioma/calidad (ej: (CAST), (LATINO), etc.)
        title = title.replace(Regex("""\((LATINO|CASTELLANO|SUBTITULADO|SUB|DUAL|4K|UHD|FHD|1080P|720P|HD|HEVC|H264|H265|CAST|ORIGINAL|AUDIO)[^)]*\)""", RegexOption.IGNORE_CASE), " ")
        title = title.replace(Regex("""\b(LATINO|CASTELLANO|SUBTITULADO|SUB|DUAL|4K|UHD|FHD|1080P|720P|HD|HEVC|H264|H265|CAST|WEB-?DL|WEBRIP|BLURAY|HDRIP|CAM|TS)\b""", RegexOption.IGNORE_CASE), " ")
        title = title.replace(Regex("""\b(S\d+|T\d+|TEMP\s*\d+|TEMPORADA\s*\d+)\b""", RegexOption.IGNORE_CASE), " ")
        // Eliminar comillas y caracteres especiales que arruinan la búsqueda en TMDB
        title = title.replace(Regex("""["'“”«»|•\-_/\\:]+"""), " ")
        title = title.trim().replace(Regex("""\s+"""), " ")
        return title.ifBlank { rawName.trim() }
    }

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $TMDB_BEARER_TOKEN")
            .header("Accept", "application/json")
            .build()
    }

    /**
     * Consulta créditos (/credits) en TMDB para extraer actores principales y director.
     */
    private suspend fun fetchCredits(mediaType: String, id: Int, language: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$mediaType/$id/credits?api_key=$TMDB_API_KEY&language=$language"
        val request = buildRequest(url)
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use Pair(null, null)
                val bodyStr = response.body?.string() ?: return@use Pair(null, null)
                val root = JSONObject(bodyStr)

                // Cast (top 8 actores destacados)
                val castArr = root.optJSONArray("cast")
                val castList = mutableListOf<String>()
                if (castArr != null) {
                    val limit = minOf(castArr.length(), 8)
                    for (i in 0 until limit) {
                        val actorObj = castArr.optJSONObject(i) ?: continue
                        val actorName = actorObj.optString("name").trim()
                        if (actorName.isNotBlank() && !castList.contains(actorName)) {
                            castList.add(actorName)
                        }
                    }
                }
                val castStr = if (castList.isNotEmpty()) castList.joinToString(", ") else null

                // Director / Showrunner
                val crewArr = root.optJSONArray("crew")
                var directorName: String? = null
                if (crewArr != null) {
                    for (i in 0 until crewArr.length()) {
                        val crewObj = crewArr.optJSONObject(i) ?: continue
                        val job = crewObj.optString("job")
                        if (job.equals("Director", ignoreCase = true) || job.equals("Creator", ignoreCase = true)) {
                            directorName = crewObj.optString("name").takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }

                Pair(castStr, directorName)
            }
        }.getOrDefault(Pair(null, null))
    }

    suspend fun searchTvShow(name: String): TmdbShowData? = withContext(Dispatchers.IO) {
        val query = cleanTitle(name)
        if (query.isBlank()) return@withContext null

        tvShowCache[query]?.let { return@withContext it }

        val languages = listOf("es-ES", "es-MX", "en-US")
        var bestMatch: TmdbShowData? = null

        for (lang in languages) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "$BASE_URL/search/tv?api_key=$TMDB_API_KEY&query=$encodedQuery&language=$lang"
                val request = buildRequest(url)

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use
                    val root = JSONObject(bodyStr)
                    val results = root.optJSONArray("results") ?: return@use
                    if (results.length() == 0) return@use

                    val first = results.getJSONObject(0)
                    val id = first.optInt("id")
                    val showName = first.optString("name", query)
                    val overview = first.optString("overview", "")
                    val posterPath = first.optString("poster_path").takeIf { it.isNotBlank() }
                    val backdropPath = first.optString("backdrop_path").takeIf { it.isNotBlank() }
                    val voteAverage = first.optDouble("vote_average", 0.0).takeIf { it > 0 }
                    val firstAirDate = first.optString("first_air_date").takeIf { it.isNotBlank() }

                    // Obtener reparto y director en segundo paso ligero
                    val (cast, director) = fetchCredits("tv", id, lang)

                    val data = TmdbShowData(
                        id = id,
                        name = showName,
                        overview = overview,
                        posterUrl = posterPath?.let { "$IMAGE_BASE_W500$it" },
                        backdropUrl = backdropPath?.let { "$IMAGE_BASE_W780$it" },
                        rating = voteAverage,
                        firstAirDate = firstAirDate,
                        cast = cast,
                        director = director
                    )

                    if (bestMatch == null || (bestMatch?.overview.isNullOrBlank() && overview.isNotBlank())) {
                        bestMatch = data
                    }

                    if (overview.isNotBlank()) {
                        return@withContext data.also { tvShowCache[query] = it }
                    }
                }
            } catch (_: Exception) {}
        }

        bestMatch?.also { tvShowCache[query] = it }
    }

    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int): Map<Int, TmdbEpisodeData> = withContext(Dispatchers.IO) {
        val cacheKey = "$tvId-$seasonNumber"
        seasonEpisodesCache[cacheKey]?.let { return@withContext it }

        val languages = listOf("es-ES", "es-MX", "en-US")
        var bestMap = mapOf<Int, TmdbEpisodeData>()

        for (lang in languages) {
            try {
                val url = "$BASE_URL/tv/$tvId/season/$seasonNumber?api_key=$TMDB_API_KEY&language=$lang"
                val request = buildRequest(url)

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use
                    val root = JSONObject(bodyStr)
                    val episodesArr = root.optJSONArray("episodes") ?: return@use

                    val map = mutableMapOf<Int, TmdbEpisodeData>()
                    for (i in 0 until episodesArr.length()) {
                        val epObj = episodesArr.optJSONObject(i) ?: continue
                        val epNum = epObj.optInt("episode_number", i + 1)
                        val epName = epObj.optString("name", "")
                        val epOverview = epObj.optString("overview", "")
                        val stillPath = epObj.optString("still_path").takeIf { it.isNotBlank() }
                        val voteAverage = epObj.optDouble("vote_average", 0.0).takeIf { it > 0 }
                        val airDate = epObj.optString("air_date").takeIf { it.isNotBlank() }

                        map[epNum] = TmdbEpisodeData(
                            episodeNumber = epNum,
                            name = epName,
                            overview = epOverview,
                            stillUrl = stillPath?.let { "$IMAGE_BASE_W500$it" },
                            rating = voteAverage,
                            airDate = airDate
                        )
                    }

                    if (map.values.any { it.overview.isNotBlank() }) {
                        bestMap = map
                        return@withContext map.also { seasonEpisodesCache[cacheKey] = it }
                    } else if (bestMap.isEmpty()) {
                        bestMap = map
                    }
                }
            } catch (_: Exception) {}
        }

        bestMap.also { if (it.isNotEmpty()) seasonEpisodesCache[cacheKey] = it }
    }

    suspend fun searchMovie(name: String): TmdbMovieData? = withContext(Dispatchers.IO) {
        val query = cleanTitle(name)
        if (query.isBlank()) return@withContext null

        movieCache[query]?.let { return@withContext it }

        val languages = listOf("es-ES", "es-MX", "en-US")
        var bestMatch: TmdbMovieData? = null

        for (lang in languages) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "$BASE_URL/search/movie?api_key=$TMDB_API_KEY&query=$encodedQuery&language=$lang"
                val request = buildRequest(url)

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use
                    val root = JSONObject(bodyStr)
                    val results = root.optJSONArray("results") ?: return@use
                    if (results.length() == 0) return@use

                    val first = results.getJSONObject(0)
                    val id = first.optInt("id")
                    val movieTitle = first.optString("title", query)
                    val overview = first.optString("overview", "")
                    val posterPath = first.optString("poster_path").takeIf { it.isNotBlank() }
                    val backdropPath = first.optString("backdrop_path").takeIf { it.isNotBlank() }
                    val voteAverage = first.optDouble("vote_average", 0.0).takeIf { it > 0 }
                    val releaseDate = first.optString("release_date").takeIf { it.isNotBlank() }

                    // Obtener reparto y director en segundo paso ligero
                    val (cast, director) = fetchCredits("movie", id, lang)

                    val data = TmdbMovieData(
                        id = id,
                        title = movieTitle,
                        overview = overview,
                        posterUrl = posterPath?.let { "$IMAGE_BASE_W500$it" },
                        backdropUrl = backdropPath?.let { "$IMAGE_BASE_W780$it" },
                        rating = voteAverage,
                        releaseDate = releaseDate,
                        cast = cast,
                        director = director
                    )

                    if (bestMatch == null || (bestMatch?.overview.isNullOrBlank() && overview.isNotBlank())) {
                        bestMatch = data
                    }

                    if (overview.isNotBlank()) {
                        return@withContext data.also { movieCache[query] = it }
                    }
                }
            } catch (_: Exception) {}
        }

        if (bestMatch == null) {
            val simplified = query.replace(Regex("""^\d+\s*[-_.]?\s*"""), "").trim()
            if (simplified != query && simplified.length >= 2) {
                for (lang in languages) {
                    try {
                        val encodedQuery = URLEncoder.encode(simplified, "UTF-8")
                        val url = "$BASE_URL/search/movie?api_key=$TMDB_API_KEY&query=$encodedQuery&language=$lang"
                        val request = buildRequest(url)
                        httpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use
                            val bodyStr = response.body?.string() ?: return@use
                            val root = JSONObject(bodyStr)
                            val results = root.optJSONArray("results") ?: return@use
                            if (results.length() == 0) return@use
                            val first = results.getJSONObject(0)
                            val id = first.optInt("id")
                            val movieTitle = first.optString("title", simplified)
                            val overview = first.optString("overview", "")
                            val posterPath = first.optString("poster_path").takeIf { it.isNotBlank() }
                            val backdropPath = first.optString("backdrop_path").takeIf { it.isNotBlank() }
                            val voteAverage = first.optDouble("vote_average", 0.0).takeIf { it > 0 }
                            val releaseDate = first.optString("release_date").takeIf { it.isNotBlank() }
                            val (cast, director) = fetchCredits("movie", id, lang)
                            val data = TmdbMovieData(
                                id = id,
                                title = movieTitle,
                                overview = overview,
                                posterUrl = posterPath?.let { "$IMAGE_BASE_W500$it" },
                                backdropUrl = backdropPath?.let { "$IMAGE_BASE_W780$it" },
                                rating = voteAverage,
                                releaseDate = releaseDate,
                                cast = cast,
                                director = director
                            )
                            bestMatch = data
                            if (overview.isNotBlank()) {
                                return@withContext data.also { movieCache[query] = it }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        bestMatch?.also { movieCache[query] = it }
    }

    suspend fun enrichEpisodes(seriesName: String, episodes: List<Episode>): List<Episode> = withContext(Dispatchers.IO) {
        if (episodes.isEmpty()) return@withContext episodes

        val show = searchTvShow(seriesName) ?: return@withContext episodes

        val seasonsPresent = episodes.map { it.seasonNumber }.distinct()
        val seasonMap = mutableMapOf<Int, Map<Int, TmdbEpisodeData>>()

        for (sNum in seasonsPresent) {
            val epMap = getSeasonEpisodes(show.id, sNum)
            if (epMap.isNotEmpty()) {
                seasonMap[sNum] = epMap
            }
        }

        if (seasonMap.isEmpty()) return@withContext episodes

        episodes.map { ep ->
            val tmdbEp = seasonMap[ep.seasonNumber]?.get(ep.episodeNumber)
            if (tmdbEp == null) {
                ep
            } else {
                val newTitle = if (ep.title.isBlank() || ep.title.matches(Regex("""(?i)Episodio\s*\d+"""))) {
                    tmdbEp.name.ifBlank { ep.title }
                } else {
                    ep.title
                }

                val newPlot = if (tmdbEp.overview.isNotBlank()) {
                    tmdbEp.overview
                } else {
                    ep.plot
                }

                val newCover = ep.cover?.takeIf { it.isNotBlank() } ?: tmdbEp.stillUrl

                ep.copy(
                    title = newTitle,
                    plot = newPlot,
                    cover = newCover
                )
            }
        }
    }

    suspend fun enrichMovie(movie: VodMovie): VodMovie = withContext(Dispatchers.IO) {
        val tmdb = searchMovie(movie.name) ?: return@withContext movie

        val curPlot = movie.plot
        val newPlot = if (curPlot.isNullOrBlank() || curPlot.length < 15) {
            tmdb.overview.ifBlank { curPlot }
        } else {
            curPlot
        }

        val newBackdrop = movie.backdropPath?.takeIf { it.isNotBlank() } ?: tmdb.backdropUrl
        val newIcon = movie.streamIcon?.takeIf { it.isNotBlank() } ?: tmdb.posterUrl
        val newRating = movie.rating?.takeIf { it > 0 } ?: tmdb.rating
        val newCast = movie.cast?.takeIf { it.isNotBlank() } ?: tmdb.cast
        val newDirector = movie.director?.takeIf { it.isNotBlank() } ?: tmdb.director

        movie.copy(
            plot = newPlot,
            backdropPath = newBackdrop,
            streamIcon = newIcon,
            rating = newRating,
            cast = newCast,
            director = newDirector
        )
    }

    suspend fun enrichSeries(series: Series): Series = withContext(Dispatchers.IO) {
        val tmdb = searchTvShow(series.name) ?: return@withContext series

        val curPlot = series.plot
        val newPlot = if (curPlot.isNullOrBlank() || curPlot.length < 15) {
            tmdb.overview.ifBlank { curPlot }
        } else {
            curPlot
        }

        val newBackdrop = series.backdropPath?.takeIf { it.isNotBlank() } ?: tmdb.backdropUrl
        val newCover = series.cover?.takeIf { it.isNotBlank() } ?: tmdb.posterUrl
        val newRating = series.rating?.takeIf { it > 0 } ?: tmdb.rating
        val newCast = series.cast?.takeIf { it.isNotBlank() } ?: tmdb.cast
        val newDirector = series.director?.takeIf { it.isNotBlank() } ?: tmdb.director

        series.copy(
            plot = newPlot,
            backdropPath = newBackdrop,
            cover = newCover,
            rating = newRating,
            cast = newCast,
            director = newDirector
        )
    }
}
