package com.lelouch.feature.mobile

import android.content.Context
import com.lelouch.core.model.CuratedMediaItem
import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.PortalFeedData
import com.lelouch.core.model.Series
import com.lelouch.core.model.VodMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Snapshot local del Inicio Mobile por sourceId (expiración 24h) para renderizado instantáneo
 * al abrir la app o cambiar de lista sin esperar el recálculo de PortalFeedCalculator.
 */
object MobileHomeCacheManager {

    private const val CACHE_EXPIRY_MS = 24L * 60L * 60L * 1000L

    private fun cacheFile(context: Context, sourceId: String): File {
        val safeId = sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(context.filesDir, "mobile_home_cache_$safeId.json")
    }

    suspend fun load(context: Context, sourceId: String): PortalFeedData? = withContext(Dispatchers.IO) {
        if (sourceId.isBlank()) return@withContext null
        runCatching {
            val file = cacheFile(context, sourceId)
            if (!file.exists()) return@runCatching null
            val root = JSONObject(file.readText())
            val savedAt = root.optLong("savedAt", 0L)
            if (System.currentTimeMillis() - savedAt > CACHE_EXPIRY_MS) return@runCatching null
            PortalFeedData(
                sportsCount = root.optInt("sportsCount", 0),
                topTenMovies = root.optJSONArray("topTenMovies")?.toMovieList() ?: emptyList(),
                liveMatches = root.optJSONArray("liveMatches")?.toLiveList() ?: emptyList(),
                recentMovies = root.optJSONArray("recentMovies")?.toMovieList() ?: emptyList(),
                mostViewedItems = root.optJSONArray("mostViewedItems")?.toCuratedList() ?: emptyList(),
                terrorItems = root.optJSONArray("terrorItems")?.toCuratedList() ?: emptyList(),
                sciFiItems = root.optJSONArray("sciFiItems")?.toCuratedList() ?: emptyList(),
                realHistoryItems = root.optJSONArray("realHistoryItems")?.toCuratedList() ?: emptyList(),
                actionItems = root.optJSONArray("actionItems")?.toCuratedList() ?: emptyList(),
                animItems = root.optJSONArray("animItems")?.toCuratedList() ?: emptyList()
            )
        }.getOrNull()
    }

    suspend fun save(context: Context, sourceId: String, feed: PortalFeedData) = withContext(Dispatchers.IO) {
        if (sourceId.isBlank()) return@withContext
        runCatching {
            val root = JSONObject().apply {
                put("savedAt", System.currentTimeMillis())
                put("sportsCount", feed.sportsCount)
                put("topTenMovies", feed.topTenMovies.toMovieJsonArray())
                put("liveMatches", feed.liveMatches.toLiveJsonArray())
                put("recentMovies", feed.recentMovies.toMovieJsonArray())
                put("mostViewedItems", feed.mostViewedItems.toCuratedJsonArray())
                put("terrorItems", feed.terrorItems.toCuratedJsonArray())
                put("sciFiItems", feed.sciFiItems.toCuratedJsonArray())
                put("realHistoryItems", feed.realHistoryItems.toCuratedJsonArray())
                put("actionItems", feed.actionItems.toCuratedJsonArray())
                put("animItems", feed.animItems.toCuratedJsonArray())
            }
            cacheFile(context, sourceId).writeText(root.toString())
        }
    }

    private fun List<VodMovie>.toMovieJsonArray(): JSONArray = JSONArray().apply {
        forEach { put(it.toJson()) }
    }

    private fun List<LiveStream>.toLiveJsonArray(): JSONArray = JSONArray().apply {
        forEach { put(it.toJson()) }
    }

    private fun List<CuratedMediaItem>.toCuratedJsonArray(): JSONArray = JSONArray().apply {
        forEach { item ->
            put(
                JSONObject().apply {
                    put("title", item.title)
                    put("imageUrl", item.imageUrl ?: JSONObject.NULL)
                    put("rating", item.rating ?: JSONObject.NULL)
                    put("year", item.year ?: JSONObject.NULL)
                    item.movie?.let { put("movie", it.toJson()) }
                    item.series?.let { put("series", it.toJson()) }
                }
            )
        }
    }

    private fun VodMovie.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("streamId", streamId)
        put("num", num)
        put("name", name)
        put("title", title)
        put("year", year ?: JSONObject.NULL)
        put("streamIcon", streamIcon ?: JSONObject.NULL)
        put("backdropPath", backdropPath ?: JSONObject.NULL)
        put("rating", rating ?: JSONObject.NULL)
        put("rating5based", rating5based ?: JSONObject.NULL)
        put("added", added ?: JSONObject.NULL)
        put("categoryId", categoryId)
        put("categoryName", categoryName)
        put("containerExtension", containerExtension)
        put("plot", plot ?: JSONObject.NULL)
        put("cast", cast ?: JSONObject.NULL)
        put("director", director ?: JSONObject.NULL)
        put("genre", genre ?: JSONObject.NULL)
        put("durationSecs", durationSecs)
        put("durationFormatted", durationFormatted)
        put("streamUrl", streamUrl)
        put("isFavorite", isFavorite)
    }

    private fun Series.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("seriesId", seriesId)
        put("num", num)
        put("name", name)
        put("title", title)
        put("cover", cover ?: JSONObject.NULL)
        put("backdropPath", backdropPath ?: JSONObject.NULL)
        put("plot", plot ?: JSONObject.NULL)
        put("cast", cast ?: JSONObject.NULL)
        put("director", director ?: JSONObject.NULL)
        put("genre", genre ?: JSONObject.NULL)
        put("releaseDate", releaseDate ?: JSONObject.NULL)
        put("rating", rating ?: JSONObject.NULL)
        put("rating5based", rating5based ?: JSONObject.NULL)
        put("categoryId", categoryId)
        put("categoryName", categoryName)
        put("seasonsCount", seasonsCount)
        put("episodeCount", episodeCount)
        put("isFavorite", isFavorite)
    }

    private fun LiveStream.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("streamId", streamId)
        put("num", num)
        put("name", name)
        put("streamType", streamType)
        put("streamIcon", streamIcon ?: JSONObject.NULL)
        put("categoryId", categoryId)
        put("categoryName", categoryName)
        put("epgChannelId", epgChannelId ?: JSONObject.NULL)
        put("isAdult", isAdult)
        put("isFavorite", isFavorite)
        put("streamUrl", streamUrl)
        put("containerExtension", containerExtension)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun JSONArray.toMovieList(): List<VodMovie> = buildList(length()) {
        for (i in 0 until length()) {
            optJSONObject(i)?.let { add(it.toVodMovie()) }
        }
    }

    private fun JSONArray.toLiveList(): List<LiveStream> = buildList(length()) {
        for (i in 0 until length()) {
            optJSONObject(i)?.let { add(it.toLiveStream()) }
        }
    }

    private fun JSONArray.toCuratedList(): List<CuratedMediaItem> = buildList(length()) {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            add(
                CuratedMediaItem(
                    title = obj.optString("title"),
                    imageUrl = obj.optStringOrNull("imageUrl"),
                    rating = obj.optDoubleOrNull("rating"),
                    year = obj.optStringOrNull("year"),
                    movie = obj.optJSONObject("movie")?.toVodMovie(),
                    series = obj.optJSONObject("series")?.toSeries()
                )
            )
        }
    }

    private fun JSONObject.toVodMovie(): VodMovie = VodMovie(
        id = optString("id"),
        streamId = optInt("streamId"),
        num = optInt("num"),
        name = optString("name"),
        title = optString("title", optString("name")),
        year = optStringOrNull("year"),
        streamIcon = optStringOrNull("streamIcon"),
        backdropPath = optStringOrNull("backdropPath"),
        rating = optDoubleOrNull("rating"),
        rating5based = optDoubleOrNull("rating5based"),
        added = optStringOrNull("added"),
        categoryId = optString("categoryId"),
        categoryName = optString("categoryName"),
        containerExtension = optString("containerExtension", "mp4"),
        plot = optStringOrNull("plot"),
        cast = optStringOrNull("cast"),
        director = optStringOrNull("director"),
        genre = optStringOrNull("genre"),
        durationSecs = optInt("durationSecs"),
        durationFormatted = optString("durationFormatted"),
        streamUrl = optString("streamUrl"),
        isFavorite = optBoolean("isFavorite")
    )

    private fun JSONObject.toSeries(): Series = Series(
        id = optString("id"),
        seriesId = optInt("seriesId"),
        num = optInt("num"),
        name = optString("name"),
        title = optString("title", optString("name")),
        cover = optStringOrNull("cover"),
        backdropPath = optStringOrNull("backdropPath"),
        plot = optStringOrNull("plot"),
        cast = optStringOrNull("cast"),
        director = optStringOrNull("director"),
        genre = optStringOrNull("genre"),
        releaseDate = optStringOrNull("releaseDate"),
        rating = optDoubleOrNull("rating"),
        rating5based = optDoubleOrNull("rating5based"),
        categoryId = optString("categoryId"),
        categoryName = optString("categoryName"),
        seasonsCount = optInt("seasonsCount"),
        episodeCount = optInt("episodeCount"),
        isFavorite = optBoolean("isFavorite")
    )

    private fun JSONObject.toLiveStream(): LiveStream = LiveStream(
        id = optString("id"),
        streamId = optInt("streamId"),
        num = optInt("num"),
        name = optString("name"),
        streamType = optString("streamType", "live"),
        streamIcon = optStringOrNull("streamIcon"),
        categoryId = optString("categoryId"),
        categoryName = optString("categoryName"),
        epgChannelId = optStringOrNull("epgChannelId"),
        isAdult = optBoolean("isAdult"),
        isFavorite = optBoolean("isFavorite"),
        streamUrl = optString("streamUrl"),
        containerExtension = optString("containerExtension", "ts")
    )
}
