package com.lelouch.core.network.anime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Cliente ligero y asíncrono para enriquecer series de Anime y saltar intros:
 * 1. AniList GraphQL API (pública y gratuita) para carátulas HD, sinopsis y MAL ID.
 * 2. AniSkip API (api.aniskip.com, abierta) para timestamps precisos de Opening y Ending.
 *
 * Filosofía Ponytail: sin librerías pesadas, consultas directas y seguras con timeouts breves.
 */
object AnimeEnricher {

    private const val ANILIST_URL = "https://graphql.anilist.co"
    private const val ANISKIP_URL = "https://api.aniskip.com/v2/skip-times"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val animeCache = ConcurrentHashMap<String, AnimeMeta?>()
    private val skipCache = ConcurrentHashMap<String, List<SkipInterval>>()

    private val ANIME_KEYWORDS = listOf(
        "anime", "manga", "animaci", "otaku", "shonen", "seinen", "shojo",
        "dragon ball", "naruto", "one piece", "demon slayer", "shingeki",
        "japon", "japón", "ghibli", "boku no hero", "jujutsu", "bleach",
        "death note", "hunter x hunter", "attack on titan", "tokyo",
        "chainsaw", "solo leveling", "kaiju", "dandadan", "dan da dan",
        "kimetsu", "pokemon", "pokémon", "digimon", "sailor moon", "evangelion",
        "inuyasha", "ranma", "fairy tail", "black clover", "haikyuu", "dr stone"
    )

    data class AnimeMeta(
        val anilistId: Int,
        val malId: Int?,
        val titleRomaji: String,
        val titleEnglish: String?,
        val posterUrl: String?,
        val bannerUrl: String?,
        val overview: String?,
        val rating: Double?,
        val episodesCount: Int?
    )

    data class SkipInterval(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val type: String // "op" = Opening, "ed" = Ending
    ) {
        val label: String
            get() = if (type == "ed") "Saltar Ending" else "Saltar Intro"
    }

    /**
     * Comprueba si el título contiene indicios claros de anime antes de saturar endpoints.
     */
    fun isLikelyAnime(title: String): Boolean {
        val lower = title.lowercase()
        return ANIME_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Limpia nombres comunes de listas IPTV para maximizar coincidencias en AniList.
     * Ej: "[LAT] Demon Slayer: Kimetsu no Yaiba Temporada 2 (Dual)" -> "Demon Slayer Kimetsu no Yaiba"
     */
    fun cleanAnimeTitle(raw: String): String {
        return raw
            .replace(Regex("\\[[^\\]]*\\]|\\([^\\)]*\\)"), " ")
            .replace(Regex("(?i)\\b(temporada|season|temp|t|s)\\s*\\d+\\b"), " ")
            .replace(Regex("(?i)\\b(cap|capítulo|capitulo|ep|episodio)\\.?\\s*\\d+\\b"), " ")
            .replace(Regex("(?i)\\b(latino|dual|sub|subtitulado|castellano|1080p|720p|4k|fhd|hd|h264|h265|hevc)\\b"), " ")
            .replace(Regex("[-_:|•]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Busca el anime en AniList mediante GraphQL y retorna metadatos + malId.
     */
    suspend fun searchAnime(rawTitle: String): AnimeMeta? = withContext(Dispatchers.IO) {
        val cleaned = cleanAnimeTitle(rawTitle)
        if (cleaned.length < 2) return@withContext null

        val cacheKey = cleaned.lowercase()
        if (animeCache.containsKey(cacheKey)) {
            return@withContext animeCache[cacheKey]
        }

        runCatching {
            val query = """
                query (${'$'}search: String) {
                  Media (search: ${'$'}search, type: ANIME) {
                    id
                    idMal
                    title {
                      romaji
                      english
                    }
                    coverImage {
                      large
                      extraLarge
                    }
                    bannerImage
                    description
                    averageScore
                    episodes
                  }
                }
            """.trimIndent()

            val variables = JSONObject().apply {
                put("search", cleaned)
            }
            val requestJson = JSONObject().apply {
                put("query", query)
                put("variables", variables)
            }

            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(ANILIST_URL)
                .post(body)
                .header("User-Agent", "LelouchIPTV/1.0 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    animeCache[cacheKey] = null
                    return@withContext null
                }

                val respBody = response.body?.string().orEmpty()
                val root = JSONObject(respBody)
                val data = root.optJSONObject("data") ?: return@withContext null
                val media = data.optJSONObject("Media") ?: return@withContext null

                val anilistId = media.optInt("id", 0)
                if (anilistId == 0) return@withContext null

                val malId = if (media.has("idMal") && !media.isNull("idMal")) media.getInt("idMal") else null
                val titleObj = media.optJSONObject("title")
                val romaji = titleObj?.optString("romaji").orEmpty().ifBlank { cleaned }
                val english = titleObj?.optString("english")

                val coverObj = media.optJSONObject("coverImage")
                val posterUrl = coverObj?.optString("extraLarge")
                    ?.ifBlank { coverObj.optString("large") }
                    ?.takeIf { it.isNotBlank() }

                val bannerUrl = media.optString("bannerImage").takeIf { it.isNotBlank() }
                val rawDesc = media.optString("description")
                val cleanDesc = rawDesc.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                    .takeIf { it.isNotBlank() }

                val score = if (media.has("averageScore") && !media.isNull("averageScore")) {
                    media.getDouble("averageScore") / 10.0
                } else null

                val eps = if (media.has("episodes") && !media.isNull("episodes")) media.getInt("episodes") else null

                val meta = AnimeMeta(
                    anilistId = anilistId,
                    malId = malId,
                    titleRomaji = romaji,
                    titleEnglish = english,
                    posterUrl = posterUrl,
                    bannerUrl = bannerUrl,
                    overview = cleanDesc,
                    rating = score,
                    episodesCount = eps
                )

                animeCache[cacheKey] = meta
                meta
            }
        }.getOrElse {
            animeCache[cacheKey] = null
            null
        }
    }

    /**
     * Consulta AniSkip API para obtener marcas de tiempo de Opening y Ending.
     * AniSkip v2 requiere obligatoriamente `episodeLength` (en segundos).
     */
    suspend fun getSkipIntervals(
        malId: Int,
        episodeNumber: Int,
        episodeLengthSec: Double = 1440.0
    ): List<SkipInterval> = withContext(Dispatchers.IO) {
        val cacheKey = "$malId:$episodeNumber"
        if (skipCache.containsKey(cacheKey)) {
            return@withContext skipCache[cacheKey].orEmpty()
        }

        runCatching {
            val lengthParam = if (episodeLengthSec > 60.0) episodeLengthSec else 1440.0
            val url = "$ANISKIP_URL/$malId/$episodeNumber?types=op&types=ed&episodeLength=$lengthParam"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "LelouchIPTV/1.0 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    skipCache[cacheKey] = emptyList()
                    return@withContext emptyList()
                }

                val respBody = response.body?.string().orEmpty()
                val root = JSONObject(respBody)
                if (!root.optBoolean("found", false)) {
                    skipCache[cacheKey] = emptyList()
                    return@withContext emptyList()
                }

                val resultsArr = root.optJSONArray("results") ?: run {
                    skipCache[cacheKey] = emptyList()
                    return@withContext emptyList()
                }

                val intervals = mutableListOf<SkipInterval>()
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.optJSONObject(i) ?: continue
                    val intervalObj = item.optJSONObject("interval") ?: continue
                    val startSec = intervalObj.optDouble("startTime", -1.0)
                    val endSec = intervalObj.optDouble("endTime", -1.0)
                    val skipType = item.optString("skipType", "op")

                    if (startSec >= 0 && endSec > startSec) {
                        intervals.add(
                            SkipInterval(
                                startTimeMs = (startSec * 1000).toLong(),
                                endTimeMs = (endSec * 1000).toLong(),
                                type = skipType
                            )
                        )
                    }
                }

                skipCache[cacheKey] = intervals
                intervals
            }
        }.getOrElse {
            skipCache[cacheKey] = emptyList()
            emptyList()
        }
    }

    /**
     * Método de conveniencia de un solo paso: resuelve AniList + AniSkip para una serie y episodio.
     * Si la serie no tiene indicios de anime o ocurre cualquier error, devuelve lista vacía de forma 100% segura.
     */
    suspend fun resolveSkipForEpisode(
        seriesTitle: String,
        episodeNumber: Int,
        durationMs: Long = 0L
    ): List<SkipInterval> {
        return runCatching {
            if (!isLikelyAnime(seriesTitle)) return emptyList()
            val anime = searchAnime(seriesTitle) ?: return emptyList()
            val malId = anime.malId ?: return emptyList()
            val durSec = if (durationMs > 0L) durationMs / 1000.0 else 1440.0
            getSkipIntervals(malId, episodeNumber, durSec)
        }.getOrDefault(emptyList())
    }
}
