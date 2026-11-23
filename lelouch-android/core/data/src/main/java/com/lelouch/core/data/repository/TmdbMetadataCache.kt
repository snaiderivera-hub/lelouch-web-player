package com.lelouch.core.data.repository

import com.lelouch.core.database.dao.TmdbMatchCacheDao
import com.lelouch.core.database.entity.TmdbMatchCacheEntity
import com.lelouch.core.network.tmdb.TmdbEnricher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caché persistente en Room para metadatos TMDB HD (backdrop, póster, sinopsis y nota).
 * Evita repetir consultas de red entre sesiones y mantiene los banners nítidos.
 */
class TmdbMetadataCache(
    private val dao: TmdbMatchCacheDao
) {
    companion object {
        private const val TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }

    suspend fun getOrFetchMovie(
        title: String,
        year: String? = null
    ): TmdbMatchCacheEntity? = withContext(Dispatchers.IO) {
        val clean = TmdbEnricher.cleanTitle(title)
        if (clean.isBlank()) return@withContext null
        val key = "movie:${clean.lowercase()}:${year?.take(4).orEmpty()}"
        val now = System.currentTimeMillis()
        val cached = dao.getByKey(key)
        if (cached != null && (now - cached.updatedAt) < TTL_MS) {
            return@withContext cached
        }
        val fetched = TmdbEnricher.searchMovie(clean) ?: return@withContext cached
        val entity = TmdbMatchCacheEntity(
            cacheKey = key,
            tmdbId = fetched.id,
            mediaType = "movie",
            title = fetched.title,
            overview = fetched.overview,
            posterUrl = fetched.posterUrl,
            backdropUrl = fetched.backdropUrl,
            rating = fetched.rating,
            releaseYear = fetched.releaseDate?.take(4),
            updatedAt = now
        )
        dao.upsert(entity)
        entity
    }

    suspend fun getOrFetchSeries(
        title: String
    ): TmdbMatchCacheEntity? = withContext(Dispatchers.IO) {
        val clean = TmdbEnricher.cleanTitle(title)
        if (clean.isBlank()) return@withContext null
        val key = "tv:${clean.lowercase()}"
        val now = System.currentTimeMillis()
        val cached = dao.getByKey(key)
        if (cached != null && (now - cached.updatedAt) < TTL_MS) {
            return@withContext cached
        }
        val fetched = TmdbEnricher.searchTvShow(clean) ?: return@withContext cached
        val entity = TmdbMatchCacheEntity(
            cacheKey = key,
            tmdbId = fetched.id,
            mediaType = "series",
            title = fetched.name,
            overview = fetched.overview,
            posterUrl = fetched.posterUrl,
            backdropUrl = fetched.backdropUrl,
            rating = fetched.rating,
            releaseYear = fetched.firstAirDate?.take(4),
            updatedAt = now
        )
        dao.upsert(entity)
        entity
    }
}
