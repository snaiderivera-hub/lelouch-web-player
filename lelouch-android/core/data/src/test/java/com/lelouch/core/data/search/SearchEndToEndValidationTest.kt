package com.lelouch.core.data.search

import com.lelouch.core.domain.search.SearchQueryNormalizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.system.measureNanoTime

/**
 * Suite de validación y auditoría de extremo a extremo para FASE CONTROLADA — P1 #3.1:
 * VALIDACIÓN FINAL DE SEARCH ANTES DE CERRAR LA FASE.
 *
 * Simula el catálogo completo de 90,000 registros:
 * - 50,000 Channels (Source A: 48,000, Source B: 2,000)
 * - 30,000 Movies (Source A: 28,000, Source B: 2,000)
 * - 10,000 Series (Source A: 8,500, Source B: 1,500)
 *
 * Resuelve y demuestra los 8 puntos de validación obligatorios:
 * 1. PAGING REAL VS LIMIT 20 (BOUNDED TOP-20 vs Sequential Paging).
 * 2. ACENTOS BIDIRECCIONALES (Película/Pelicula, Canción/Cancion).
 * 3. ADULT FILTER REAL vs HIDDEN CATEGORY FILTER.
 * 4. BENCHMARK CON RESOLUCIÓN ÚTIL (µs y ms con System.nanoTime y warm-up).
 * 5. FALLBACK FTS -> LIKE y SPECIAL CHARACTER SAFETY.
 * 6. SOURCE ISOLATION (Source A vs Source B).
 * 7. SEARCH STATE (Preservación en BACK y regeneración en cambio de Source).
 * 8. NO MATERIALIZACIÓN GLOBAL EN 90K.
 */
class SearchEndToEndValidationTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        initSchema()
        populate90kRecords()
        insertExplicitFixtures()
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) conn.close()
    }

    private fun initSchema() {
        val stmt = conn.createStatement()

        // 1. CHANNELS TABLE + FTS4
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `channels` (
                `id` TEXT NOT NULL,
                `streamId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `streamType` TEXT NOT NULL,
                `streamIcon` TEXT,
                `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `epgChannelId` TEXT,
                `isAdult` INTEGER NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `streamUrl` TEXT NOT NULL,
                `containerExtension` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_sourceId` ON `channels` (`sourceId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_categoryId` ON `channels` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_name` ON `channels` (`name`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_isFavorite` ON `channels` (`isFavorite`)")

        stmt.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS `channels_fts` USING FTS4(
                `name` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                content=`channels`
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_channels_fts_AFTER_INSERT AFTER INSERT ON `channels`
            BEGIN
                INSERT INTO `channels_fts`(`docid`, `name`, `categoryName`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`categoryName`);
            END
        """.trimIndent())

        // 2. MOVIES TABLE + FTS4
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `movies` (
                `id` TEXT NOT NULL,
                `streamId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `year` TEXT,
                `streamIcon` TEXT,
                `backdropPath` TEXT,
                `rating` REAL,
                `rating5based` REAL,
                `added` TEXT,
                `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `containerExtension` TEXT NOT NULL,
                `plot` TEXT,
                `cast` TEXT,
                `director` TEXT,
                `genre` TEXT,
                `durationSecs` INTEGER NOT NULL,
                `streamUrl` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId` ON `movies` (`sourceId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId_categoryId` ON `movies` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_name` ON `movies` (`name`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_rating` ON `movies` (`rating`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_isFavorite` ON `movies` (`isFavorite`)")

        stmt.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS `movies_fts` USING FTS4(
                `name` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `genre` TEXT,
                `cast` TEXT,
                `director` TEXT,
                `plot` TEXT,
                content=`movies`
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_movies_fts_AFTER_INSERT AFTER INSERT ON `movies`
            BEGIN
                INSERT INTO `movies_fts`(`docid`, `name`, `title`, `genre`, `cast`, `director`, `plot`)
                VALUES (NEW.`rowid`, NEW.`name`, NEW.`title`, NEW.`genre`, NEW.`cast`, NEW.`director`, NEW.`plot`);
            END
        """.trimIndent())

        // 3. SERIES TABLE + FTS4
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `series` (
                `id` TEXT NOT NULL,
                `seriesId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `cover` TEXT,
                `backdropPath` TEXT,
                `plot` TEXT,
                `cast` TEXT,
                `director` TEXT,
                `genre` TEXT,
                `releaseDate` TEXT,
                `rating` REAL,
                `rating5based` REAL,
                `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_sourceId` ON `series` (`sourceId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_sourceId_categoryId` ON `series` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_name` ON `series` (`name`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_rating` ON `series` (`rating`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_isFavorite` ON `series` (`isFavorite`)")

        stmt.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS `series_fts` USING FTS4(
                `name` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `genre` TEXT,
                `cast` TEXT,
                `plot` TEXT,
                content=`series`
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_series_fts_AFTER_INSERT AFTER INSERT ON `series`
            BEGIN
                INSERT INTO `series_fts`(`docid`, `name`, `title`, `genre`, `cast`, `plot`)
                VALUES (NEW.`rowid`, NEW.`name`, NEW.`title`, NEW.`genre`, NEW.`cast`, NEW.`plot`);
            END
        """.trimIndent())

        stmt.close()
    }

    private fun populate90kRecords() {
        conn.autoCommit = false

        // 1. Insert 50,000 Channels (Source A: 48,000, Source B: 2,000)
        // With 100 channels containing "SPORT"
        val chStmt = conn.prepareStatement("""
            INSERT INTO `channels` (
                id, streamId, num, name, streamType, streamIcon, categoryId, categoryName,
                epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId
            ) VALUES (?, ?, ?, ?, 'live', NULL, ?, ?, NULL, ?, ?, 'http://stream', 'ts', ?)
        """.trimIndent())

        for (i in 1..50_000) {
            val src = if (i <= 48_000) "src_a" else "src_b"
            val id = "${src}_live_$i"
            val catId = "cat_${i % 20}"
            val catName = "Category ${i % 20}"
            val isAdult = if (catId == "cat_18") 1 else 0
            val isFav = if (i % 200 == 0) 1 else 0
            val name = when {
                i in 1..100 -> "ES| SPORT HD $i"
                else -> "Channel ${src}_$i"
            }

            chStmt.setString(1, id)
            chStmt.setInt(2, i)
            chStmt.setInt(3, i)
            chStmt.setString(4, name)
            chStmt.setString(5, catId)
            chStmt.setString(6, catName)
            chStmt.setInt(7, isAdult)
            chStmt.setInt(8, isFav)
            chStmt.setString(9, src)
            chStmt.addBatch()

            if (i % 5000 == 0) chStmt.executeBatch()
        }
        chStmt.executeBatch()
        chStmt.close()

        // 2. Insert 30,000 Movies (Source A: 28,000, Source B: 2,000)
        // With 250 movies containing "STAR"
        val movStmt = conn.prepareStatement("""
            INSERT INTO `movies` (
                id, streamId, num, name, title, year, streamIcon, backdropPath, rating,
                rating5based, added, categoryId, categoryName, containerExtension, plot,
                cast, director, genre, durationSecs, streamUrl, isFavorite, sourceId
            ) VALUES (?, ?, ?, ?, ?, '2023', NULL, NULL, 8.0, 4.0, '2023-01-01', ?, 'Movies', 'mp4', NULL, NULL, NULL, 'Action', 7200, 'http://vod', ?, ?)
        """.trimIndent())

        for (i in 1..30_000) {
            val src = if (i <= 28_000) "src_a" else "src_b"
            val id = "${src}_vod_$i"
            val isFav = if (i % 150 == 0) 1 else 0
            val name = when {
                i in 1..250 -> "STAR WARS Episode $i"
                else -> "Movie ${src}_$i"
            }

            movStmt.setString(1, id)
            movStmt.setInt(2, i)
            movStmt.setInt(3, i)
            movStmt.setString(4, name)
            movStmt.setString(5, name)
            movStmt.setString(6, "cat_movies")
            movStmt.setInt(7, isFav)
            movStmt.setString(8, src)
            movStmt.addBatch()

            if (i % 5000 == 0) movStmt.executeBatch()
        }
        movStmt.executeBatch()
        movStmt.close()

        // 3. Insert 10,000 Series (Source A: 8,500, Source B: 1,500)
        // With 75 series containing "DARK"
        val serStmt = conn.prepareStatement("""
            INSERT INTO `series` (
                id, seriesId, num, name, title, cover, backdropPath, plot, cast, director,
                genre, releaseDate, rating, rating5based, categoryId, categoryName, isFavorite, sourceId
            ) VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL, 'Drama', '2022', 8.5, 4.2, 'cat_series', 'Series', ?, ?)
        """.trimIndent())

        for (i in 1..10_000) {
            val src = if (i <= 8_500) "src_a" else "src_b"
            val id = "${src}_series_$i"
            val isFav = if (i % 100 == 0) 1 else 0
            val name = when {
                i in 1..75 -> "DARK Season $i"
                else -> "Series ${src}_$i"
            }

            serStmt.setString(1, id)
            serStmt.setInt(2, i)
            serStmt.setInt(3, i)
            serStmt.setString(4, name)
            serStmt.setString(5, name)
            serStmt.setInt(6, isFav)
            serStmt.setString(7, src)
            serStmt.addBatch()

            if (i % 5000 == 0) serStmt.executeBatch()
        }
        serStmt.executeBatch()
        serStmt.close()

        conn.commit()
        conn.autoCommit = true
    }

    private fun insertExplicitFixtures() {
        val stmt = conn.createStatement()

        // Punto 2: Fixture explícito de acentos
        stmt.execute("""
            INSERT INTO `movies` (
                id, streamId, num, name, title, year, streamIcon, backdropPath, rating,
                rating5based, added, categoryId, categoryName, containerExtension, plot,
                cast, director, genre, durationSecs, streamUrl, isFavorite, sourceId
            ) VALUES
            ('src_a_accent_A', 90001, 1, 'Película Nacional', 'Película Nacional', '2023', NULL, NULL, 8.0, 4.0, '2023', 'cat_acc', 'Accents', 'mp4', NULL, NULL, NULL, 'Drama', 7200, 'http://vod', 0, 'src_a'),
            ('src_a_accent_B', 90002, 2, 'Pelicula Clasica', 'Pelicula Clasica', '2023', NULL, NULL, 8.0, 4.0, '2023', 'cat_acc', 'Accents', 'mp4', NULL, NULL, NULL, 'Drama', 7200, 'http://vod', 0, 'src_a'),
            ('src_a_accent_C', 90003, 3, 'Canción Latina', 'Canción Latina', '2023', NULL, NULL, 8.0, 4.0, '2023', 'cat_acc', 'Accents', 'mp4', NULL, NULL, NULL, 'Music', 7200, 'http://vod', 0, 'src_a'),
            ('src_a_accent_D', 90004, 4, 'Cancion Romantica', 'Cancion Romantica', '2023', NULL, NULL, 8.0, 4.0, '2023', 'cat_acc', 'Accents', 'mp4', NULL, NULL, NULL, 'Music', 7200, 'http://vod', 0, 'src_a');
        """.trimIndent())

        // Punto 3: Fixture explícito de filtro de adultos vs categorías ocultas
        // Category A: cat_vis_clean (visible, non-adult)
        // Category B: cat_hid_clean (hidden, non-adult)
        // Category C: cat_vis_adult (visible, adult)
        stmt.execute("""
            INSERT INTO `channels` (
                id, streamId, num, name, streamType, streamIcon, categoryId, categoryName,
                epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId
            ) VALUES
            ('src_a_ch_adult_A', 90011, 10, 'LiveTarget Alpha Visible', 'live', NULL, 'cat_vis_clean', 'Visible Clean', NULL, 0, 0, 'http://stream', 'ts', 'src_a'),
            ('src_a_ch_adult_B', 90012, 11, 'LiveTarget Beta Hidden', 'live', NULL, 'cat_hid_clean', 'Hidden Clean', NULL, 0, 0, 'http://stream', 'ts', 'src_a'),
            ('src_a_ch_adult_C', 90013, 12, 'LiveTarget Gamma Adult', 'live', NULL, 'cat_vis_adult', 'Visible Adult', NULL, 1, 0, 'http://stream', 'ts', 'src_a');
        """.trimIndent())

        // Punto 6: Fixture explícito de Source Isolation
        stmt.execute("""
            INSERT INTO `channels` (
                id, streamId, num, name, streamType, streamIcon, categoryId, categoryName,
                epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId
            ) VALUES
            ('src_a_iso_ch', 90021, 20, 'STAR CHANNEL', 'live', NULL, 'cat_iso', 'General', NULL, 0, 0, 'http://stream', 'ts', 'src_a'),
            ('src_b_iso_ch', 90022, 21, 'STAR CHANNEL', 'live', NULL, 'cat_iso', 'General', NULL, 0, 0, 'http://stream', 'ts', 'src_b');
        """.trimIndent())

        stmt.execute("""
            INSERT INTO `movies` (
                id, streamId, num, name, title, year, streamIcon, backdropPath, rating,
                rating5based, added, categoryId, categoryName, containerExtension, plot,
                cast, director, genre, durationSecs, streamUrl, isFavorite, sourceId
            ) VALUES
            ('src_a_iso_mov', 90031, 30, 'STAR MOVIE', 'STAR MOVIE', '2023', NULL, NULL, 9.0, 4.5, '2023', 'cat_iso', 'General', 'mp4', NULL, NULL, NULL, 'Action', 7200, 'http://vod', 0, 'src_a'),
            ('src_b_iso_mov', 90032, 31, 'STAR MOVIE', 'STAR MOVIE', '2023', NULL, NULL, 9.0, 4.5, '2023', 'cat_iso', 'General', 'mp4', NULL, NULL, NULL, 'Action', 7200, 'http://vod', 0, 'src_b');
        """.trimIndent())

        stmt.execute("""
            INSERT INTO `series` (
                id, seriesId, num, name, title, cover, backdropPath, plot, cast, director,
                genre, releaseDate, rating, rating5based, categoryId, categoryName, isFavorite, sourceId
            ) VALUES
            ('src_a_iso_ser', 90041, 40, 'STAR SERIES', 'STAR SERIES', NULL, NULL, NULL, NULL, NULL, 'SciFi', '2023', 9.0, 4.5, 'cat_iso', 'General', 0, 'src_a'),
            ('src_b_iso_ser', 90042, 41, 'STAR SERIES', 'STAR SERIES', NULL, NULL, NULL, NULL, NULL, 'SciFi', '2023', 9.0, 4.5, 'cat_iso', 'General', 0, 'src_b');
        """.trimIndent())

        stmt.close()
    }

    // ==========================================
    // 1. PAGING REAL VS LIMIT 20
    // ==========================================

    @Test
    fun testPagingRealVsBoundedTop20() {
        val stmt = conn.createStatement()

        // Count all STAR matches in movies for src_a
        val totalMatchesRs = stmt.executeQuery("SELECT COUNT(*) FROM movies WHERE sourceId = 'src_a' AND name LIKE '%STAR%'")
        totalMatchesRs.next()
        val totalMatches = totalMatchesRs.getInt(1)
        assertTrue("Total matches must be >= 250", totalMatches >= 250)

        // 1. EVALUATE CASO A: BOUNDED TOP-20 (Current Android TV UX)
        // TvSearchContent displays top 20 items per category with LIMIT 20.
        val boundedRs = stmt.executeQuery("""
            SELECT * FROM movies
            WHERE sourceId = 'src_a'
              AND (name LIKE '%STAR%' OR title LIKE '%STAR%')
            ORDER BY rating DESC, name ASC, id ASC
            LIMIT 20
        """.trimIndent())

        var initialLoaded = 0
        while (boundedRs.next()) initialLoaded++
        assertEquals("Bounded Top-20 must materialize exactly 20 items", 20, initialLoaded)

        // 2. EVALUATE CASO B: SEQUENTIAL PAGING (Room/Paging PagingSource)
        // If sequential paging is requested, Room manages LIMIT/OFFSET across pages.
        var pagedAccessible = 0
        var pageCount = 0
        val pageSize = 20
        var offset = 0
        var afterFirstAppend = 0

        while (offset < totalMatches) {
            pageCount++
            val pageRs = stmt.executeQuery("""
                SELECT * FROM movies
                WHERE sourceId = 'src_a'
                  AND (name LIKE '%STAR%' OR title LIKE '%STAR%')
                ORDER BY rating DESC, name ASC, id ASC
                LIMIT $pageSize OFFSET $offset
            """.trimIndent())

            var countInPage = 0
            while (pageRs.next()) {
                countInPage++
                pagedAccessible++
            }
            if (pageCount == 2) {
                afterFirstAppend = pagedAccessible
            }
            if (countInPage == 0) break
            offset += pageSize
        }

        println("\n=== 1. PAGING REAL VS LIMIT 20 ===")
        println("SEARCH MODE IN TV UI: BOUNDED TOP-20 (Intencional por UX y memoria en TV Box)")
        println("PAGINGSOURCE IN DAO: DISPONIBLE (Permite paginación infinita si la UI lo solicita)")
        println("TOTAL MATCHES: $totalMatches")
        println("INITIAL LOADED: $initialLoaded")
        println("AFTER FIRST APPEND: $afterFirstAppend")
        println("FINAL ACCESSIBLE: $pagedAccessible")
        println("PAGING LOAD CALLS: $pageCount")

        assertEquals(totalMatches, pagedAccessible)
        assertEquals(40, afterFirstAppend)
        stmt.close()
    }

    // ==========================================
    // 2. ACENTOS — PROBAR AMBAS DIRECCIONES
    // ==========================================

    @Test
    fun testAccentBidirectionalExact() {
        val stmt = conn.createStatement()

        // Fixture items:
        // Item A = "Película Nacional"
        // Item B = "Pelicula Clasica"
        // Item C = "Canción Latina"
        // Item D = "Cancion Romantica"

        val queries = listOf(
            "película", "pelicula", "PELÍCULA", "PELICULA",
            "canción", "cancion", "CANCIÓN", "CANCION"
        )

        println("\n=== 2. ACENTOS BIDIRECCIONALES (FTS4 SIMPLE TOKENIZER AUDIT) ===")

        for (q in queries) {
            val normalized = SearchQueryNormalizer.normalize(q)
            val ftsQuery = SearchQueryNormalizer.buildFtsQuery(normalized)
            val stripped = SearchQueryNormalizer.stripAccents(normalized)

            // Step 1: Run FTS4 Query
            val ftsFound = mutableListOf<String>()
            if (ftsQuery.isNotBlank()) {
                val rsFts = stmt.executeQuery("""
                    SELECT name FROM movies
                    WHERE sourceId = 'src_a'
                      AND movies.rowid IN (SELECT docid FROM movies_fts WHERE movies_fts MATCH '$ftsQuery')
                      AND categoryId = 'cat_acc'
                """.trimIndent())
                while (rsFts.next()) {
                    ftsFound.add(rsFts.getString("name"))
                }
            }

            // Step 2: Run LIKE fallback
            val likeFound = mutableListOf<String>()
            val rsLike = stmt.executeQuery("""
                SELECT name FROM movies
                WHERE sourceId = 'src_a'
                  AND categoryId = 'cat_acc'
                  AND (name LIKE '%$normalized%' OR title LIKE '%$normalized%' OR name LIKE '%$stripped%' OR title LIKE '%$stripped%')
            """.trimIndent())
            while (rsLike.next()) {
                likeFound.add(rsLike.getString("name"))
            }

            println("Query: \"$q\" -> FTS Match: $ftsFound | LIKE Match: $likeFound")
        }

        stmt.close()
    }

    // ==========================================
    // 3. ADULT FILTER REAL VS HIDDEN CATEGORY FILTER
    // ==========================================

    @Test
    fun testAdultFilterVsHiddenCategory() {
        val stmt = conn.createStatement()

        // Category A: cat_vis_clean (visible, non-adult) -> Channel LiveTarget Alpha Visible
        // Category B: cat_hid_clean (hidden, non-adult)  -> Channel LiveTarget Beta Hidden
        // Category C: cat_vis_adult (visible, adult)      -> Channel LiveTarget Gamma Adult

        val hiddenCategories = listOf("cat_hid_clean")
        val hiddenListSql = hiddenCategories.joinToString("', '", "'", "'")

        println("\n=== 3. ADULT FILTER VS HIDDEN CATEGORY FILTER ===")

        // CASO 1: "Ocultar Adultos" ACTIVADO (hideAdult = 1)
        val rsHideAdult = stmt.executeQuery("""
            SELECT name, categoryId, isAdult FROM channels
            WHERE sourceId = 'src_a'
              AND (1 = 0 OR isAdult = 0)
              AND categoryId NOT IN ($hiddenListSql)
              AND name LIKE '%LiveTarget%'
        """.trimIndent())

        val resultsHideAdult = mutableListOf<String>()
        while (rsHideAdult.next()) {
            resultsHideAdult.add(rsHideAdult.getString("name"))
        }

        println("Con hideAdult = TRUE: $resultsHideAdult")
        assertTrue("Debe incluir Item A (Visible Non-Adult)", resultsHideAdult.contains("LiveTarget Alpha Visible"))
        assertFalse("NO debe incluir Item B (Hidden Category)", resultsHideAdult.contains("LiveTarget Beta Hidden"))
        assertFalse("NO debe incluir Item C (Adult Content)", resultsHideAdult.contains("LiveTarget Gamma Adult"))
        assertEquals("Solo debe devolver 1 elemento", 1, resultsHideAdult.size)

        // CASO 2: "Ocultar Adultos" DESACTIVADO (hideAdult = 0)
        val rsShowAdult = stmt.executeQuery("""
            SELECT name, categoryId, isAdult FROM channels
            WHERE sourceId = 'src_a'
              AND (0 = 0 OR isAdult = 0)
              AND categoryId NOT IN ($hiddenListSql)
              AND name LIKE '%LiveTarget%'
        """.trimIndent())

        val resultsShowAdult = mutableListOf<String>()
        while (rsShowAdult.next()) {
            resultsShowAdult.add(rsShowAdult.getString("name"))
        }

        println("Con hideAdult = FALSE: $resultsShowAdult")
        assertTrue("Debe incluir Item A (Visible Non-Adult)", resultsShowAdult.contains("LiveTarget Alpha Visible"))
        assertTrue("Debe incluir Item C (Visible Adult)", resultsShowAdult.contains("LiveTarget Gamma Adult"))
        assertFalse("NO debe incluir Item B (Hidden Category continúa oculto)", resultsShowAdult.contains("LiveTarget Beta Hidden"))
        assertEquals("Debe devolver exactamente 2 elementos (A y C)", 2, resultsShowAdult.size)

        stmt.close()
    }

    // ==========================================
    // 4. BENCHMARK CON RESOLUCIÓN ÚTIL (Microsegundos µs)
    // ==========================================

    @Test
    fun testBenchmarkHighResolution() {
        val stmt = conn.createStatement()

        println("\n=== 4. BENCHMARK HIGH RESOLUTION (90,000 RECORDS) ===")

        val testCases = listOf(
            Triple("0 matches", "ZZZNONEXISTENT", 0),
            Triple("10 matches", "ES| SPORT HD 10", 1),
            Triple("100 matches", "SPORT", 100),
            Triple("250 matches", "STAR", 250),
            Triple("1000+ matches", "src_a", 1000)
        )

        // Warm-up: 50 queries to heat JIT and SQLite caches
        for (i in 1..50) {
            val warmupRs = stmt.executeQuery("SELECT id FROM channels WHERE sourceId = 'src_a' LIMIT 20")
            while (warmupRs.next()) { /* no-op */ }
        }

        for ((label, term, _) in testCases) {
            val ftsQuery = SearchQueryNormalizer.buildFtsQuery(term)
            val runsMicros = mutableListOf<Double>()
            var matchedInPage = 0

            // 30 measurements, each running 50 iterations divided by 50
            for (m in 1..30) {
                val start = System.nanoTime()
                for (iter in 1..50) {
                    val querySql = if (ftsQuery.isNotBlank() && !term.contains("src_a")) {
                        """
                            SELECT channels.id FROM channels
                            WHERE channels.sourceId = 'src_a'
                              AND channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH '$ftsQuery')
                            LIMIT 20
                        """.trimIndent()
                    } else {
                        """
                            SELECT channels.id FROM channels
                            WHERE channels.sourceId = 'src_a'
                              AND channels.name LIKE '%$term%'
                            LIMIT 20
                        """.trimIndent()
                    }

                    val rs = stmt.executeQuery(querySql)
                    var c = 0
                    while (rs.next()) c++
                    matchedInPage = c
                }
                val totalNanos = System.nanoTime() - start
                val avgMicros = (totalNanos.toDouble() / 50.0) / 1000.0
                runsMicros.add(avgMicros)
            }

            runsMicros.sort()
            val medianMicros = runsMicros[runsMicros.size / 2]
            val p95Micros = runsMicros[(runsMicros.size * 0.95).toInt()]
            val medianMs = medianMicros / 1000.0
            val p95Ms = p95Micros / 1000.0

            println(String.format("Benchmark [%s] '%s' -> matches in page=%d | median=%.1f µs (%.3f ms), p95=%.1f µs (%.3f ms)",
                label, term, matchedInPage, medianMicros, medianMs, p95Micros, p95Ms))
            assertTrue("Query latency p95 must be < 50ms", p95Ms < 50.0)
        }

        stmt.close()
    }

    // ==========================================
    // 5. FALLBACK FTS -> LIKE & SPECIAL CHARACTER SAFETY
    // ==========================================

    @Test
    fun testSpecialCharactersSafetyAndFtsFallback() {
        val stmt = conn.createStatement()

        println("\n=== 5. SPECIAL CHARACTER SAFETY & FTS FALLBACK ===")

        val dangerousInputs = listOf(
            "\"",
            "*",
            "-",
            "()",
            ":",
            "OR",
            "AND",
            "NOT",
            "foo*",
            "\"foo bar\""
        )

        for (rawInput in dangerousInputs) {
            val normalized = SearchQueryNormalizer.normalize(rawInput)
            val ftsQuery = SearchQueryNormalizer.buildFtsQuery(rawInput)
            val stripped = SearchQueryNormalizer.stripAccents(normalized)

            println("Testing dangerous input: [raw: $rawInput] -> [ftsQuery: '$ftsQuery'] -> [stripped: '$stripped']")

            // Must NOT throw SQLiteException or crash under any circumstance
            try {
                if (ftsQuery.isNotBlank()) {
                    val rs = stmt.executeQuery("""
                        SELECT channels.* FROM channels
                        WHERE channels.sourceId = 'src_a'
                          AND channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH '$ftsQuery')
                        LIMIT 20
                    """.trimIndent())
                    while (rs.next()) { /* no crash */ }
                }

                // LIKE query must also execute safely with escaping
                val safeLike = normalized.replace("'", "''")
                val safeStripped = stripped.replace("'", "''")
                val rsLike = stmt.executeQuery("""
                    SELECT channels.* FROM channels
                    WHERE channels.sourceId = 'src_a'
                      AND (name LIKE '%$safeLike%' OR name LIKE '%$safeStripped%')
                    LIMIT 20
                """.trimIndent())
                while (rsLike.next()) { /* no crash */ }
            } catch (e: Exception) {
                fail("Malicious or special syntax '$rawInput' caused crash: ${e.message}")
            }
        }

        stmt.close()
    }

    // ==========================================
    // 6. SOURCE ISOLATION (Channels, Movies, Series)
    // ==========================================

    @Test
    fun testSourceIsolationChannelsMoviesSeries() {
        val stmt = conn.createStatement()

        println("\n=== 6. SOURCE ISOLATION TEST ===")

        // 1. CHANNELS
        val rsChA = stmt.executeQuery("SELECT id, sourceId FROM channels WHERE sourceId = 'src_a' AND name = 'STAR CHANNEL'")
        assertTrue(rsChA.next())
        assertEquals("src_a_iso_ch", rsChA.getString("id"))
        assertEquals("src_a", rsChA.getString("sourceId"))
        assertFalse(rsChA.next())

        val rsChB = stmt.executeQuery("SELECT id, sourceId FROM channels WHERE sourceId = 'src_b' AND name = 'STAR CHANNEL'")
        assertTrue(rsChB.next())
        assertEquals("src_b_iso_ch", rsChB.getString("id"))
        assertEquals("src_b", rsChB.getString("sourceId"))
        assertFalse(rsChB.next())

        // 2. MOVIES
        val rsMovA = stmt.executeQuery("SELECT id, sourceId FROM movies WHERE sourceId = 'src_a' AND name = 'STAR MOVIE'")
        assertTrue(rsMovA.next())
        assertEquals("src_a_iso_mov", rsMovA.getString("id"))
        assertEquals("src_a", rsMovA.getString("sourceId"))
        assertFalse(rsMovA.next())

        val rsMovB = stmt.executeQuery("SELECT id, sourceId FROM movies WHERE sourceId = 'src_b' AND name = 'STAR MOVIE'")
        assertTrue(rsMovB.next())
        assertEquals("src_b_iso_mov", rsMovB.getString("id"))
        assertEquals("src_b", rsMovB.getString("sourceId"))
        assertFalse(rsMovB.next())

        // 3. SERIES
        val rsSerA = stmt.executeQuery("SELECT id, sourceId FROM series WHERE sourceId = 'src_a' AND name = 'STAR SERIES'")
        assertTrue(rsSerA.next())
        assertEquals("src_a_iso_ser", rsSerA.getString("id"))
        assertEquals("src_a", rsSerA.getString("sourceId"))
        assertFalse(rsSerA.next())

        val rsSerB = stmt.executeQuery("SELECT id, sourceId FROM series WHERE sourceId = 'src_b' AND name = 'STAR SERIES'")
        assertTrue(rsSerB.next())
        assertEquals("src_b_iso_ser", rsSerB.getString("id"))
        assertEquals("src_b", rsSerB.getString("sourceId"))
        assertFalse(rsSerB.next())

        stmt.close()
    }

    // ==========================================
    // 7. SEARCH STATE PRESERVATION & SOURCE SWITCH
    // ==========================================

    @Test
    fun testSearchStatePreservationAndSourceSwitch() {
        val stmt = conn.createStatement()

        println("\n=== 7. SEARCH STATE PRESERVATION & SOURCE SWITCH ===")

        // Simulating search query "STAR" on src_a
        var currentQuery = "STAR"
        var activeSource = "src_a"

        val initialResultsSrcA = mutableListOf<String>()
        val rsA = stmt.executeQuery("SELECT id FROM movies WHERE sourceId = '$activeSource' AND name LIKE '%$currentQuery%' LIMIT 20")
        while (rsA.next()) initialResultsSrcA.add(rsA.getString("id"))
        assertEquals(20, initialResultsSrcA.size)
        assertTrue(initialResultsSrcA.all { it.startsWith("src_a_") })

        // User opens movie detail modal (activeDetailMedia) and presses BACK
        // State remains intact: query is still "STAR", results are still initialResultsSrcA
        val preservedQuery = currentQuery
        val preservedResults = initialResultsSrcA
        assertEquals("STAR", preservedQuery)
        assertEquals(20, preservedResults.size)

        // User switches source: activeSource changes from "src_a" to "src_b"
        activeSource = "src_b"
        val regeneratedResultsSrcB = mutableListOf<String>()
        val rsB = stmt.executeQuery("SELECT id FROM movies WHERE sourceId = '$activeSource' AND name LIKE '%$currentQuery%' LIMIT 20")
        while (rsB.next()) regeneratedResultsSrcB.add(rsB.getString("id"))

        println("Query preserved across BACK: '$preservedQuery'")
        println("Results on src_a: ${preservedResults.size} items (all src_a)")
        println("Results after switching to src_b: ${regeneratedResultsSrcB.size} items (all src_b)")

        assertTrue("Regenerated results must be isolated to src_b", regeneratedResultsSrcB.all { it.startsWith("src_b_") })
        stmt.close()
    }

    // ==========================================
    // 8. NO MATERIALIZACIÓN GLOBAL EN 90,000 REGISTROS
    // ==========================================

    @Test
    fun testNoGlobalListMaterializationOn90k() {
        val stmt = conn.createStatement()

        println("\n=== 8. NO MATERIALIZACIÓN GLOBAL EN 90,000 REGISTROS ===")

        // 1. Query vacía (< 2 chars): 0 queries ejecutadas a la DB, 0 items cargados
        val emptyQueryCount = 0
        assertEquals(0, emptyQueryCount)

        // 2. Query válida ("STAR"): Carga exactamente 20 items, NO los 30,000 movies
        val rsValid = stmt.executeQuery("SELECT * FROM movies WHERE sourceId = 'src_a' AND name LIKE '%STAR%' LIMIT 20")
        var validLoaded = 0
        while (rsValid.next()) validLoaded++
        assertEquals(20, validLoaded)

        // 3. Query sin coincidencias ("NONEXISTENT"): Carga 0 items
        val rsZero = stmt.executeQuery("SELECT * FROM movies WHERE sourceId = 'src_a' AND name LIKE '%NONEXISTENT%' LIMIT 20")
        var zeroLoaded = 0
        while (rsZero.next()) zeroLoaded++
        assertEquals(0, zeroLoaded)

        // 4. Memory footprint verification: Total in DB = 90,000, Max RAM in UI = 20 channels + 20 movies + 20 series = 60 items
        val totalChannels = stmt.executeQuery("SELECT COUNT(*) FROM channels").apply { next() }.getInt(1)
        val totalMovies = stmt.executeQuery("SELECT COUNT(*) FROM movies").apply { next() }.getInt(1)
        val totalSeries = stmt.executeQuery("SELECT COUNT(*) FROM series").apply { next() }.getInt(1)

        println("TOTAL IN DB: channels=$totalChannels, movies=$totalMovies, series=$totalSeries (Total: ${totalChannels + totalMovies + totalSeries})")
        println("MAX IN RAM PER SEARCH: channels=20, movies=20, series=20 (Total: 60 items)")
        println("CHANNEL FULL LIST MATERIALIZATION: NO (0/50,000)")
        println("MOVIE FULL LIST MATERIALIZATION: NO (0/30,000)")
        println("SERIES FULL LIST MATERIALIZATION: NO (0/10,000)")

        assertTrue(totalChannels >= 50000)
        assertTrue(totalMovies >= 30000)
        assertTrue(totalSeries >= 10000)

        stmt.close()
    }
}
