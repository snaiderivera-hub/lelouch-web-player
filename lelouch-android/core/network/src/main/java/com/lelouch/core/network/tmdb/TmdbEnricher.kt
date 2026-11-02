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

    private const val TMDB_API_KEY = "15d2ea6d0dc1d476efbca3eba2b9bbfb"
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
        val firstAirDate: String?
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
        val releaseDate: String?
    )

    fun cleanTitle(rawName: String): String {
        var title = rawName
        title = title.replace(Regex("""\[[^\]]*\]"""), " ")
        title = title.replace(Regex("""\(\s*\d{4}\s*\)"""), " ")
        title = title.replace(Regex("""\((LATINO|CASTELLANO|SUBTITULADO|SUB|DUAL|4K|UHD|FHD|1080P|720P|HD|HEVC|H264|H265)[^)]*\)""", RegexOption.IGNORE_CASE), " ")
        title = title.replace(Regex("""\b(LATINO|CASTELLANO|SUBTITULADO|SUB|DUAL|4K|UHD|FHD|1080P|720P|HD|HEVC|H264|H265)\b""", RegexOption.IGNORE_CASE), " ")
        title = title.replace(Regex("""\b(S\d+|T\d+|TEMP\s*\d+|TEMPORADA\s*\d+)\b""", RegexOption.IGNORE_CASE), " ")
        title = title.replace(Regex("""[|•\-_/\\:]+"""), " ")
        title = title.trim().replace(Regex("""\s+"""), " ")
        return title.ifBlank { rawName.trim() }
    }

    suspend fun searchTvShow(name: String): TmdbShowData? = withContext(Dispatchers.IO) {
        val query = cleanTitle(name)
        if (query.isBlank()) return@withContext null

        tvShowCache[query]?.let { return@withContext it }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search/tv?api_key=$TMDB_API_KEY&query=$encodedQuery&language=es-MX"
            val request = Request.Builder().url(url).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val root = JSONObject(bodyStr)
                val results = root.optJSONArray("results") ?: return@withContext null
                if (results.length() == 0) return@withContext null

                val first = results.getJSONObject(0)
                val id = first.optInt("id")
                val showName = first.optString("name", query)
                val overview = first.optString("overview", "")
                val posterPath = first.optString("poster_path").takeIf { it.isNotBlank() }
                val backdropPath = first.optString("backdrop_path").takeIf { it.isNotBlank() }
                val voteAverage = first.optDouble("vote_average", 0.0).takeIf { it > 0 }
                val firstAirDate = first.optString("first_air_date").takeIf { it.isNotBlank() }

                val data = TmdbShowData(
                    id = id,
                    name = showName,
                    overview = overview,
                    posterUrl = posterPath?.let { "$IMAGE_BASE_W500$it" },
                    backdropUrl = backdropPath?.let { "$IMAGE_BASE_W780$it" },
                    rating = voteAverage,
                    firstAirDate = firstAirDate
                )
                tvShowCache[query] = data
                data
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int): Map<Int, TmdbEpisodeData> = withContext(Dispatchers.IO) {
        val cacheKey = "$tvId-$seasonNumber"
        seasonEpisodesCache[cacheKey]?.let { return@withContext it }

        try {
            val url = "$BASE_URL/tv/$tvId/season/$seasonNumber?api_key=$TMDB_API_KEY&language=es-MX"
            val request = Request.Builder().url(url).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyMap()
                val bodyStr = response.body?.string() ?: return@withContext emptyMap()
                val root = JSONObject(bodyStr)
                val episodesArr = root.optJSONArray("episodes") ?: return@withContext emptyMap()

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
                seasonEpisodesCache[cacheKey] = map
                map
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun searchMovie(name: String): TmdbMovieData? = withContext(Dispatchers.IO) {
        val query = cleanTitle(name)
        if (query.isBlank()) return@withContext null

        movieCache[query]?.let { return@withContext it }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search/movie?api_key=$TMDB_API_KEY&query=$encodedQuery&language=es-MX"
            val request = Request.Builder().url(url).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val root = JSONObject(bodyStr)
                val results = root.optJSONArray("results") ?: return@withContext null
                if (results.length() == 0) return@withContext null

                val first = results.getJSONObject(0)
                val id = first.optInt("id")
                val movieTitle = first.optString("title", query)
                val overview = first.optString("overview", "")
                val posterPath = first.optString("poster_path").takeIf { it.isNotBlank() }
                val backdropPath = first.optString("backdrop_path").takeIf { it.isNotBlank() }
                val voteAverage = first.optDouble("vote_average", 0.0).takeIf { it > 0 }
                val releaseDate = first.optString("release_date").takeIf { it.isNotBlank() }

                val data = TmdbMovieData(
                    id = id,
                    title = movieTitle,
                    overview = overview,
                    posterUrl = posterPath?.let { "$IMAGE_BASE_W500$it" },
                    backdropUrl = backdropPath?.let { "$IMAGE_BASE_W780$it" },
                    rating = voteAverage,
                    releaseDate = releaseDate
                )
                movieCache[query] = data
                data
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Enriquece la lista de episodios con sinopsis real, nombres y miniaturas 16:9 oficiales de TMDB
     */
    suspend fun enrichEpisodes(seriesName: String, episodes: List<Episode>): List<Episode> = withContext(Dispatchers.IO) {
        if (episodes.isEmpty()) return@withContext episodes

        val show = searchTvShow(seriesName) ?: return@withContext episodes

        // Temporadas presentes en los episodios
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

    /**
     * Enriquece una película con sinopsis oficial, fondo HD, calificación y poster de TMDB si faltan
     */
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

        movie.copy(
            plot = newPlot,
            backdropPath = newBackdrop,
            streamIcon = newIcon,
            rating = newRating
        )
    }

    /**
     * Enriquece una serie con sinopsis de TMDB, fondo HD y calificación
     */
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

        series.copy(
            plot = newPlot,
            backdropPath = newBackdrop,
            cover = newCover,
            rating = newRating
        )
    }
}
