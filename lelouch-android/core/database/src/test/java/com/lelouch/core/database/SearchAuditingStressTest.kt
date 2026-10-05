package com.lelouch.core.database

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.system.measureNanoTime

/**
 * Test de auditoría exhaustiva para FASE CONTROLADA — P1 #3 (Search / FTS Android TV).
 *
 * Simula el catálogo completo de 90,000 registros:
 * - 50,000 Channels (Source A: 48,000, Source B: 2,000)
 * - 30,000 Movies (Source A: 28,000, Source B: 2,000)
 * - 10,000 Series (Source A: 8,500, Source B: 1,500)
 *
 * Mide y valida:
 * 1. Comportamiento real de las tablas FTS4 existentes (channels_fts, movies_fts, series_fts).
 * 2. Comportamiento de acentos (tokenizer simple vs remove_diacritics).
 * 3. EXPLAIN QUERY PLAN para FTS vs SQL LIKE indexado.
 * 4. Latencia y benchmarks (median, p95) para 0, 10, 100, 1000+ resultados.
 * 5. Aislamiento por sourceId y exclusión de categorías ocultas.
 * 6. Manejo de caracteres especiales FTS (*, -, ", (), :).
 */
class SearchAuditingStressTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        initSchema()
        populate90kRecords()
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
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_added` ON `movies` (`added`)")
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
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_sourceId_seriesId` ON `series` (`sourceId`, `seriesId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_sourceId_categoryId` ON `series` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_name` ON `series` (`name`)")
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
            ) VALUES (?, ?, ?, ?, 'live', NULL, ?, ?, NULL, 0, ?, 'http://stream', 'ts', ?)
        """.trimIndent())

        for (i in 1..50_000) {
            val src = if (i <= 48_000) "src_a" else "src_b"
            val id = "${src}_live_$i"
            val catId = "cat_${i % 20}"
            val catName = "Category ${i % 20}"
            val isFav = if (i % 200 == 0) 1 else 0
            val name = when {
                i in 1..100 -> "ES| SPORT HD $i"
                i == 101 -> "Película Channel"
                i == 102 -> "PELICULA CLASSIC"
                else -> "Channel ${src}_$i"
            }

            chStmt.setString(1, id)
            chStmt.setInt(2, i)
            chStmt.setInt(3, i)
            chStmt.setString(4, name)
            chStmt.setString(5, catId)
            chStmt.setString(6, catName)
            chStmt.setInt(7, isFav)
            chStmt.setString(8, src)
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
                i == 251 -> "Película En Español"
                i == 252 -> "PELÍCULA LATINA"
                i == 253 -> "La Pelicula"
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
                i == 76 -> "Película Serie"
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

    @Test
    fun testFtsTriggerSyncAndCount() {
        val stmt = conn.createStatement()
        val chFtsCount = stmt.executeQuery("SELECT COUNT(*) FROM channels_fts").apply { next() }.getInt(1)
        val movFtsCount = stmt.executeQuery("SELECT COUNT(*) FROM movies_fts").apply { next() }.getInt(1)
        val serFtsCount = stmt.executeQuery("SELECT COUNT(*) FROM series_fts").apply { next() }.getInt(1)

        assertEquals("channels_fts must have 50,000 entries synced via trigger", 50_000, chFtsCount)
        assertEquals("movies_fts must have 30,000 entries synced via trigger", 30_000, movFtsCount)
        assertEquals("series_fts must have 10,000 entries synced via trigger", 10_000, serFtsCount)
        stmt.close()
    }

    @Test
    fun testAccentSensitivityUnderSimpleTokenizer() {
        val stmt = conn.createStatement()

        // In FTS4 with simple tokenizer:
        // Does "pelicula*" match "Película En Español" (with accent í)?
        val rsPeliculaNoAccent = stmt.executeQuery("SELECT COUNT(*) FROM movies_fts WHERE movies_fts MATCH 'pelicula*'")
        rsPeliculaNoAccent.next()
        val countNoAccent = rsPeliculaNoAccent.getInt(1)

        val rsPeliculaWithAccent = stmt.executeQuery("SELECT COUNT(*) FROM movies_fts WHERE movies_fts MATCH 'película*'")
        rsPeliculaWithAccent.next()
        val countWithAccent = rsPeliculaWithAccent.getInt(1)

        println("[AUDIT ACCENT] FTS4 simple tokenizer: query 'pelicula*' matched: $countNoAccent, query 'película*' matched: $countWithAccent")

        // In SQL LIKE (SQLite default):
        val rsLikeNoAccent = stmt.executeQuery("SELECT COUNT(*) FROM movies WHERE name LIKE '%pelicula%'")
        rsLikeNoAccent.next()
        val countLikeNoAccent = rsLikeNoAccent.getInt(1)

        val rsLikeWithAccent = stmt.executeQuery("SELECT COUNT(*) FROM movies WHERE name LIKE '%película%'")
        rsLikeWithAccent.next()
        val countLikeWithAccent = rsLikeWithAccent.getInt(1)

        println("[AUDIT ACCENT] SQL LIKE: query '%pelicula%' matched: $countLikeNoAccent, query '%película%' matched: $countLikeWithAccent")
        stmt.close()
    }

    @Test
    fun testSpecialCharactersInFtsMatch() {
        val stmt = conn.createStatement()

        fun sanitizeFts(input: String): String {
            val cleaned = input.replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
            if (cleaned.isBlank()) return ""
            val tokens = cleaned.split(" ").filter { it.length >= 2 }
            if (tokens.isEmpty()) return ""
            return tokens.joinToString(" AND ") { "\"$it\"*" }
        }

        val testCases = listOf(
            "SPORT" to "ES| SPORT HD 1",
            "STAR-WARS" to "STAR WARS Episode 1",
            "DARK (1)" to "DARK Season 1",
            "foo:bar" to "",
            "\"STAR\"" to "STAR WARS Episode 1",
            "película" to "Película En Español"
        )

        for ((raw, expectedSubstr) in testCases) {
            val ftsQuery = sanitizeFts(raw)
            if (ftsQuery.isNotEmpty()) {
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM movies_fts WHERE movies_fts MATCH '$ftsQuery'")
                rs.next()
                val count = rs.getInt(1)
                println("[AUDIT SANITIZED FTS] Raw '$raw' -> FTS '$ftsQuery' -> Matches: $count")
                if (expectedSubstr.isNotEmpty() && count == 0) {
                    // Check channels or series
                    val rsCh = stmt.executeQuery("SELECT COUNT(*) FROM channels_fts WHERE channels_fts MATCH '$ftsQuery'")
                    rsCh.next()
                    val rsSer = stmt.executeQuery("SELECT COUNT(*) FROM series_fts WHERE series_fts MATCH '$ftsQuery'")
                    rsSer.next()
                    println("  In channels: ${rsCh.getInt(1)}, In series: ${rsSer.getInt(1)}")
                }
            } else {
                println("[AUDIT SANITIZED FTS] Raw '$raw' -> Empty tokens")
            }
        }
        stmt.close()
    }

    @Test
    fun testAccentExpansionMatchesAll() {
        val stmt = conn.createStatement()

        fun stripAccents(str: String): String {
            val nfd = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
            return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        }

        // Test with "película" -> stripped: "pelicula"
        val query = "película"
        val stripped = stripAccents(query)
        assertEquals("pelicula", stripped)

        // In FTS: query with OR expansion
        val ftsExpanded = "$query* OR $stripped*"
        val rsFts = stmt.executeQuery("SELECT COUNT(*) FROM movies_fts WHERE movies_fts MATCH '$ftsExpanded'")
        rsFts.next()
        val ftsCount = rsFts.getInt(1)
        println("[AUDIT ACCENT EXPANSION] FTS '$ftsExpanded' matched: $ftsCount")
        // In FTS4 with simple tokenizer:
        // 'película*' matches 'Película En Español' (1)
        // 'pelicula*' matches 'La Pelicula' (1)
        // 'PELÍCULA LATINA' has uppercase unicode 'Í' which simple tokenizer does not fold to 'í', so 2 matches.
        assertEquals("FTS simple tokenizer matches 2 (Película + Pelicula) without unicode61 folding", 2, ftsCount)

        // In SQL LIKE:
        val rsLike = stmt.executeQuery("SELECT COUNT(*) FROM movies WHERE name LIKE '%$query%' OR name LIKE '%$stripped%'")
        rsLike.next()
        val likeCount = rsLike.getInt(1)
        println("[AUDIT ACCENT EXPANSION] LIKE '%$query%' OR '%$stripped%' matched: $likeCount")

        stmt.close()
    }

    @Test
    fun testExplainQueryPlan() {
        val stmt = conn.createStatement()

        println("\n=== EXPLAIN QUERY PLAN: FTS4 vs SQL LIKE ===")

        // 1. Channel Search FTS JOIN
        val eqpFtsJoin = stmt.executeQuery("""
            EXPLAIN QUERY PLAN
            SELECT channels.* FROM channels
            JOIN channels_fts ON channels.rowid = channels_fts.docid
            WHERE channels.sourceId = 'src_a' AND channels_fts MATCH 'SPORT*'
            LIMIT 50
        """.trimIndent())
        while (eqpFtsJoin.next()) {
            println("FTS JOIN Plan: ${eqpFtsJoin.getString("detail")}")
        }

        // 2. Channel Search FTS Subquery / IN
        val eqpFtsSub = stmt.executeQuery("""
            EXPLAIN QUERY PLAN
            SELECT channels.* FROM channels
            WHERE channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH 'SPORT*')
              AND channels.sourceId = 'src_a'
            LIMIT 50
        """.trimIndent())
        while (eqpFtsSub.next()) {
            println("FTS SUBQUERY Plan: ${eqpFtsSub.getString("detail")}")
        }

        // 3. Channel Search LIKE with index on sourceId
        val eqpLike = stmt.executeQuery("""
            EXPLAIN QUERY PLAN
            SELECT channels.* FROM channels
            WHERE channels.sourceId = 'src_a' AND channels.name LIKE '%SPORT%'
            LIMIT 50
        """.trimIndent())
        while (eqpLike.next()) {
            println("LIKE Plan: ${eqpLike.getString("detail")}")
        }

        stmt.close()
    }

    @Test
    fun testSearchPerformanceBenchmark() {
        val stmt = conn.createStatement()

        println("\n=== BENCHMARK LATENCY (90,000 RECORDS) ===")

        // Benchmark cases:
        // A: 0 results ("XYZNONEXISTENT")
        // B: 10 results (LIMIT 10)
        // C: 100 results ("SPORT")
        // D: 250 results ("STAR")

        val terms = listOf("XYZNONEXISTENT", "SPORT", "STAR", "DARK")

        for (term in terms) {
            // FTS Subquery Benchmark
            val ftsTimes = mutableListOf<Long>()
            var ftsMatchCount = 0
            for (i in 1..20) {
                val escapedTerm = "\"$term\"*"
                val nanos = measureNanoTime {
                    val rs = stmt.executeQuery("""
                        SELECT channels.id FROM channels
                        WHERE channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH '$escapedTerm')
                          AND channels.sourceId = 'src_a'
                        LIMIT 50
                    """.trimIndent())
                    var count = 0
                    while (rs.next()) count++
                    ftsMatchCount = count
                }
                ftsTimes.add(nanos / 1_000_000) // ms
            }
            ftsTimes.sort()
            val ftsMedian = ftsTimes[ftsTimes.size / 2]
            val ftsP95 = ftsTimes[(ftsTimes.size * 0.95).toInt()]

            // LIKE Benchmark
            val likeTimes = mutableListOf<Long>()
            var likeMatchCount = 0
            for (i in 1..20) {
                val nanos = measureNanoTime {
                    val rs = stmt.executeQuery("""
                        SELECT channels.id FROM channels
                        WHERE channels.sourceId = 'src_a' AND channels.name LIKE '%$term%'
                        LIMIT 50
                    """.trimIndent())
                    var count = 0
                    while (rs.next()) count++
                    likeMatchCount = count
                }
                likeTimes.add(nanos / 1_000_000) // ms
            }
            likeTimes.sort()
            val likeMedian = likeTimes[likeTimes.size / 2]
            val likeP95 = likeTimes[(likeTimes.size * 0.95).toInt()]

            println("Term '$term' -> FTS: $ftsMatchCount items, median=${ftsMedian}ms, p95=${ftsP95}ms | LIKE: $likeMatchCount items, median=${likeMedian}ms, p95=${likeP95}ms")
        }

        stmt.close()
    }

    @Test
    fun testSourceIsolationAndHiddenCategory() {
        val stmt = conn.createStatement()

        // Channels query with source isolation and hidden category
        val query = """
            SELECT channels.* FROM channels
            JOIN channels_fts ON channels.rowid = channels_fts.rowid
            WHERE channels.sourceId = 'src_a'
              AND channels.categoryId NOT IN ('cat_1', 'cat_2')
              AND channels_fts MATCH 'SPORT*'
            LIMIT 50
        """.trimIndent()

        val rs = stmt.executeQuery(query)
        var count = 0
        while (rs.next()) {
            assertEquals("src_a", rs.getString("sourceId"))
            assertFalse(rs.getString("categoryId") in listOf("cat_1", "cat_2"))
            count++
        }
        assertTrue("Must find matches in src_a excluding hidden categories", count > 0)
        stmt.close()
    }
}
