package com.lelouch.core.model

/**
 * FASE 35: Modelos de datos para el feed curado y desacoplado del Portal Dashboard.
 * Permite calcular las secciones en segundo plano (Dispatchers.Default) sin bloquear el Main Thread
 * y conservarlas instantáneamente al navegar entre pestañas.
 */
data class CuratedMediaItem(
    val title: String,
    val imageUrl: String?,
    val rating: Double?,
    val year: String?,
    val movie: VodMovie? = null,
    val series: Series? = null
)

data class PortalFeedData(
    val sportsCount: Int = 0,
    val topTenMovies: List<VodMovie> = emptyList(),
    val liveMatches: List<LiveStream> = emptyList(),
    val recentMovies: List<VodMovie> = emptyList(),
    val mostViewedItems: List<CuratedMediaItem> = emptyList(),
    val terrorItems: List<CuratedMediaItem> = emptyList(),
    val sciFiItems: List<CuratedMediaItem> = emptyList(),
    val realHistoryItems: List<CuratedMediaItem> = emptyList(),
    val actionItems: List<CuratedMediaItem> = emptyList(),
    val animItems: List<CuratedMediaItem> = emptyList()
)

object PortalFeedCalculator {

    fun isAnimation(name: String, category: String, genre: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = category.lowercase()
        val n = name.lowercase()
        return g.contains("animaci") || g.contains("animation") || g.contains("anime") || g.contains("manga") ||
               c.contains("animaci") || c.contains("anime") || c.contains("caricatura") || c.contains("cartoon") ||
               n.contains("anime") || n.contains("dragon ball") || n.contains("naruto") || n.contains("one piece") || n.contains("demon slayer")
    }

    private val terrorKeywords = listOf("terror", "horror", "miedo", "suspenso", "thriller", "psicologico", "paranormal", "pesadilla", "gore", "fantasma", "demonio", "siniestro", "misterio", "slasher")
    private val terrorExcludes = listOf("animaci", "anime", "infantil", "kids", "disney", "cartoon", "comedia")
    fun isTerror(name: String, cat: String, genre: String?, plot: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = cat.lowercase()
        val p = (plot ?: "").lowercase()
        val n = name.lowercase()
        val hasGenreMatch = terrorKeywords.any { g.contains(it) || c.contains(it) }
        val hasPlotMatch = g.isBlank() && terrorKeywords.any { p.contains(it) || n.contains(it) }
        val isExcluded = terrorExcludes.any { g.contains(it) || c.contains(it) }
        return (hasGenreMatch || hasPlotMatch) && !isExcluded
    }

    private val sciFiKeywords = listOf("ciencia ficci", "sci-fi", "scifi", "espacio", "galax", "extraterrestre", "alien", "cyber", "robot", "apocalip", "distop", "mutante", "universo", "interestelar", "matrix", "star wars", "star trek", "marte")
    private val sciFiExcludes = listOf("animaci", "anime", "infantil", "kids", "disney")
    fun isSciFi(name: String, cat: String, genre: String?, plot: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = cat.lowercase()
        val p = (plot ?: "").lowercase()
        val n = name.lowercase()
        val hasGenreMatch = sciFiKeywords.any { g.contains(it) || c.contains(it) }
        val hasPlotMatch = g.isBlank() && sciFiKeywords.any { p.contains(it) || n.contains(it) }
        val isExcluded = sciFiExcludes.any { g.contains(it) || c.contains(it) }
        return (hasGenreMatch || hasPlotMatch) && !isExcluded
    }

    private val realKeywords = listOf("hechos reales", "historia real", "true story", "biograf", "biopic", "documental", "vida real", "veridica", "crimen real", "testimonio")
    private val realExcludes = listOf("novela", "libro", "comic", "superhero", "marvel", "dc comics", "fantasia", "sci-fi", "animaci", "anime")
    fun isRealHistory(name: String, cat: String, genre: String?, plot: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = cat.lowercase()
        val p = (plot ?: "").lowercase()
        val n = name.lowercase()
        val hasExactMatch = realKeywords.any { n.contains(it) || g.contains(it) || c.contains(it) } ||
                            p.contains("basada en hechos reales") || p.contains("basado en hechos reales") ||
                            p.contains("basada en la historia real") || p.contains("basado en la historia real")
        val isExcluded = realExcludes.any { g.contains(it) || c.contains(it) } || p.contains("basada en la novela") || p.contains("basado en el libro")
        return hasExactMatch && !isExcluded
    }

    private val actionKeywords = listOf("acci", "action", "aventura", "adventure", "belic", "guerra", "marcial", "policiac", "espia", "mision", "combate", "venganza", "fast & furious", "john wick")
    private val actionExcludes = listOf("animaci", "anime", "infantil", "kids", "disney", "terror", "horror")
    fun isAction(name: String, cat: String, genre: String?, plot: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = cat.lowercase()
        val p = (plot ?: "").lowercase()
        val n = name.lowercase()
        val hasGenreMatch = actionKeywords.any { g.contains(it) || c.contains(it) }
        val hasPlotMatch = g.isBlank() && actionKeywords.any { p.contains(it) || n.contains(it) }
        val isExcluded = actionExcludes.any { g.contains(it) || c.contains(it) }
        return (hasGenreMatch || hasPlotMatch) && !isExcluded
    }

    fun calculate(
        liveChannels: List<LiveStream>,
        movies: List<VodMovie>,
        seriesList: List<Series>
    ): PortalFeedData {
        val sportsKeywords = listOf(
            "sport", "deporte", "futbol", "fútbol", "liga", "champions", "premier",
            "laliga", "nba", "mlb", "nfl", "ufc", "box", "f1", "formula 1",
            "motogp", "espn", "fox sport", "dazn", "tudn", "directv sport",
            "win sport", "gol", "partido", "event", "cricket", "beisbol", "tenis", "tennis"
        )
        val sportsCount = liveChannels.count { ch ->
            val cat = ch.categoryName.lowercase()
            val name = ch.name.lowercase()
            sportsKeywords.any { cat.contains(it) || name.contains(it) }
        }

        val liveMatches = liveChannels.asSequence().filter { ch ->
            if (ch.isAdult || AdultContentFilter.isAdult(name = ch.name, categoryName = ch.categoryName, streamIcon = ch.streamIcon, streamUrl = ch.streamUrl)) return@filter false
            val cat = ch.categoryName.lowercase()
            val name = ch.name.lowercase()
            val isSportCat = sportsKeywords.any { cat.contains(it) || name.contains(it) }
            val hasVs = name.contains(" vs ") || name.contains(" vs. ") || name.contains(" v ")
            isSportCat || hasVs
        }.take(20).toList()

        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentYearStr = currentYear.toString()
        val previousYearStr = (currentYear - 1).toString()

        fun isMovieFromYear(movie: VodMovie, year: String): Boolean {
            return movie.year?.contains(year) == true ||
                   movie.added?.startsWith(year) == true ||
                   movie.name.contains(year)
        }

        val yearMovies = movies.asSequence().filter { m ->
            (isMovieFromYear(m, currentYearStr) || isMovieFromYear(m, previousYearStr)) &&
            !AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.sortedByDescending { it.rating ?: 0.0 }.take(10).toList()

        val topTenMovies = if (yearMovies.isNotEmpty()) yearMovies else movies.take(10)

        // 1. RECIÉN AGREGADAS: Ordenadas por fecha de añadido (added) desc, excluyendo anime y adultos
        val recentMovies = movies.asSequence().filter { m ->
            !isAnimation(m.name, m.categoryName, m.genre) &&
            !AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.sortedWith(
            compareByDescending<VodMovie> { m -> m.added?.toLongOrNull() ?: 0L }.thenByDescending { it.streamId }
        ).take(15).toList()

        // 2. MEJOR VALORADAS: Películas y series con rating >= 6.5 ordenadas globalmente por nota, balanceadas
        val highMovies = movies.asSequence().filter { m ->
            !isAnimation(m.name, m.categoryName, m.genre) &&
            (m.rating ?: 0.0) >= 6.5 &&
            !AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.map {
            CuratedMediaItem(
                title = it.name,
                imageUrl = it.streamIcon,
                rating = it.rating,
                year = it.year,
                movie = it
            )
        }.toList().sortedByDescending { it.rating ?: 0.0 }

        val highSeries = seriesList.asSequence().filter { s ->
            !isAnimation(s.name, s.categoryName, s.genre) &&
            ((s.rating5based?.times(2.0)) ?: (s.rating ?: 0.0)) >= 6.5 &&
            !AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.map {
            CuratedMediaItem(
                title = it.name,
                imageUrl = it.cover,
                rating = it.rating5based?.times(2.0) ?: (it.rating ?: 0.0),
                year = it.releaseDate?.take(4),
                series = it
            )
        }.toList().sortedByDescending { it.rating ?: 0.0 }

        val mostViewedItems = (highMovies.take(8) + highSeries.take(7)).sortedByDescending { it.rating ?: 0.0 }

        // 3. TERROR Y SUSPENSO: Prioridad de género oficial
        val terrorMovies = movies.asSequence().filter { m ->
            isTerror(m.name, m.categoryName, m.genre, m.plot) &&
            !AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val terrorSeries = seriesList.asSequence().filter { s ->
            isTerror(s.name, s.categoryName, s.genre, s.plot) &&
            !AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val terrorItems = (terrorMovies.take(8) + terrorSeries.take(7)).take(15)

        // 4. CIENCIA FICCIÓN: Prioridad de género oficial
        val sciFiMovies = movies.asSequence().filter { m ->
            isSciFi(m.name, m.categoryName, m.genre, m.plot) &&
            !AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val sciFiSeries = seriesList.asSequence().filter { s ->
            isSciFi(s.name, s.categoryName, s.genre, s.plot) &&
            !AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val sciFiItems = (sciFiMovies.take(8) + sciFiSeries.take(7)).take(15)

        // 5. BASADO EN HECHOS REALES: Biopics, historia real, documentales
        val realMovies = movies.asSequence().filter { m ->
            isRealHistory(m.name, m.categoryName, m.genre, m.plot) &&
            !AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val realSeries = seriesList.asSequence().filter { s ->
            isRealHistory(s.name, s.categoryName, s.genre, s.plot) &&
            !AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val realHistoryItems = (realMovies.take(8) + realSeries.take(7)).take(15)

        // 6. ACCIÓN Y AVENTURA: Prioridad de género oficial
        val actionMovies = movies.asSequence().filter { m ->
            isAction(m.name, m.categoryName, m.genre, m.plot) &&
            !AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val actionSeries = seriesList.asSequence().filter { s ->
            isAction(s.name, s.categoryName, s.genre, s.plot) &&
            !AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val actionItems = (actionMovies.take(8) + actionSeries.take(7)).take(15)

        // 7. MUNDO ANIMADO: Animación, Anime, Películas y Series de animación
        val animMovies = movies.asSequence().filter { m ->
            isAnimation(m.name, m.categoryName, m.genre) &&
            !AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val animSeries = seriesList.asSequence().filter { s ->
            isAnimation(s.name, s.categoryName, s.genre) &&
            !AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val animItems = (animMovies.take(8) + animSeries.take(7)).take(15)

        return PortalFeedData(
            sportsCount = sportsCount,
            topTenMovies = topTenMovies,
            liveMatches = liveMatches,
            recentMovies = recentMovies,
            mostViewedItems = mostViewedItems,
            terrorItems = terrorItems,
            sciFiItems = sciFiItems,
            realHistoryItems = realHistoryItems,
            actionItems = actionItems,
            animItems = animItems
        )
    }
}

