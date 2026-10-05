package com.lelouch.core.database

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.roundToLong

class MultiSourceIdentityTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) conn.close()
    }

    private fun initSchemaV3() {
        val stmt = conn.createStatement()
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `channels` (
                `id` TEXT NOT NULL, `streamId` INTEGER NOT NULL, `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL, `streamType` TEXT NOT NULL, `streamIcon` TEXT,
                `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `epgChannelId` TEXT,
                `isAdult` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL,
                `containerExtension` TEXT NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_streamId` ON `channels` (`streamId`)")

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `movies` (
                `id` TEXT NOT NULL, `streamId` INTEGER NOT NULL, `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL, `title` TEXT NOT NULL, `year` TEXT, `streamIcon` TEXT,
                `backdropPath` TEXT, `rating` REAL, `rating5based` REAL, `added` TEXT,
                `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `containerExtension` TEXT NOT NULL,
                `plot` TEXT, `cast` TEXT, `director` TEXT, `genre` TEXT, `durationSecs` INTEGER NOT NULL,
                `streamUrl` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_streamId` ON `movies` (`streamId`)")

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `series` (
                `id` TEXT NOT NULL, `seriesId` INTEGER NOT NULL, `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL, `title` TEXT NOT NULL, `cover` TEXT, `backdropPath` TEXT,
                `plot` TEXT, `cast` TEXT, `director` TEXT, `genre` TEXT, `releaseDate` TEXT,
                `rating` REAL, `rating5based` REAL, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        // V3 global UNIQUE index on seriesId
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_seriesId` ON `series` (`seriesId`)")

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL,
                `parentId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemCount` INTEGER NOT NULL,
                `isAdult` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_sourceId_type_categoryId` ON `categories` (`sourceId`, `type`, `categoryId`)")

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `favorites` (
                `id` TEXT NOT NULL, `contentId` TEXT NOT NULL, `title` TEXT NOT NULL,
                `posterUrl` TEXT, `contentType` TEXT NOT NULL, `categoryId` TEXT,
                `addedAt` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorites_sourceId_contentType_contentId` ON `favorites` (`sourceId`, `contentType`, `contentId`)")

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `watch_history` (
                `id` TEXT NOT NULL, `contentId` TEXT NOT NULL, `title` TEXT NOT NULL,
                `posterUrl` TEXT, `backdropUrl` TEXT, `contentType` TEXT NOT NULL,
                `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL,
                `seasonNumber` INTEGER, `episodeNumber` INTEGER,
                `lastWatchedTimestamp` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_watch_history_sourceId_contentType_contentId` ON `watch_history` (`sourceId`, `contentType`, `contentId`)")

        stmt.close()
    }

    private fun applyMigration3To4() {
        val stmt = conn.createStatement()
        // 1. Reemplazar índice UNIQUE global de series por (sourceId, seriesId)
        stmt.execute("DROP INDEX IF EXISTS `index_series_seriesId`")
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_sourceId_seriesId` ON `series` (`sourceId`, `seriesId`)")

        // 2. Normalizar IDs huérfanos o sin sourceId
        stmt.execute("UPDATE `channels` SET `id` = `sourceId` || '_' || `id` WHERE `sourceId` != '' AND `id` LIKE 'live_%'")
        stmt.execute("UPDATE `movies` SET `id` = `sourceId` || '_' || `id` WHERE `sourceId` != '' AND `id` LIKE 'vod_%'")
        stmt.execute("UPDATE `series` SET `id` = `sourceId` || '_' || `id` WHERE `sourceId` != '' AND `id` LIKE 'series_%'")

        // 3. Crear índices de consulta reales
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_sourceId` ON `channels` (`sourceId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_categoryId` ON `channels` (`sourceId`, `categoryId`)")

        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId` ON `movies` (`sourceId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId_categoryId` ON `movies` (`sourceId`, `categoryId`)")

        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_sourceId_categoryId` ON `series` (`sourceId`, `categoryId`)")

        stmt.execute("CREATE INDEX IF NOT EXISTS `index_categories_sourceId_type` ON `categories` (`sourceId`, `type`)")
        stmt.close()
    }

    @Test
    fun testMultiSourceCoexistence_sameIdsDoNotCollide() {
        initSchemaV3()
        applyMigration3To4()

        val stmt = conn.createStatement()

        // 1. Insert Source A: streamId=10, movie streamId=20, series seriesId=30, categoryId=40
        stmt.execute("""
            INSERT INTO channels VALUES ('src_a_live_10', 10, 1, 'Channel 10 A', 'live', null, '40', 'Cat 40', null, 0, 0, 'http://a/10.ts', 'ts', 'src_a')
        """.trimIndent())
        stmt.execute("""
            INSERT INTO movies VALUES ('src_a_vod_20', 20, 1, 'Movie 20 A', 'Movie 20 A', '2024', null, null, 8.0, 4.0, null, '40', 'Cat 40', 'mp4', null, null, null, null, 7200, 'http://a/20.mp4', 0, 'src_a')
        """.trimIndent())
        stmt.execute("""
            INSERT INTO series VALUES ('src_a_series_30', 30, 1, 'Series 30 A', 'Series 30 A', null, null, null, null, null, null, '2024', 9.0, 4.5, '40', 'Cat 40', 0, 'src_a')
        """.trimIndent())
        stmt.execute("""
            INSERT INTO categories VALUES ('src_a-LIVE-40', '40', 'News A', 0, 'LIVE', 1, 0, 'src_a')
        """.trimIndent())

        // 2. Insert Source B with exact same streamId=10, movie streamId=20, series seriesId=30, categoryId=40
        stmt.execute("""
            INSERT INTO channels VALUES ('src_b_live_10', 10, 1, 'Channel 10 B', 'live', null, '40', 'Cat 40', null, 0, 0, 'http://b/10.ts', 'ts', 'src_b')
        """.trimIndent())
        stmt.execute("""
            INSERT INTO movies VALUES ('src_b_vod_20', 20, 1, 'Movie 20 B', 'Movie 20 B', '2024', null, null, 7.0, 3.5, null, '40', 'Cat 40', 'mp4', null, null, null, null, 6000, 'http://b/20.mp4', 0, 'src_b')
        """.trimIndent())
        stmt.execute("""
            INSERT INTO series VALUES ('src_b_series_30', 30, 1, 'Series 30 B', 'Series 30 B', null, null, null, null, null, null, '2024', 8.5, 4.25, '40', 'Cat 40', 0, 'src_b')
        """.trimIndent())
        stmt.execute("""
            INSERT INTO categories VALUES ('src_b-LIVE-40', '40', 'News B', 0, 'LIVE', 1, 0, 'src_b')
        """.trimIndent())

        // Verify coexistence
        val rsChannels = stmt.executeQuery("SELECT COUNT(*) FROM channels WHERE streamId = 10")
        assertTrue(rsChannels.next())
        assertEquals(2, rsChannels.getInt(1))

        val rsMovies = stmt.executeQuery("SELECT COUNT(*) FROM movies WHERE streamId = 20")
        assertTrue(rsMovies.next())
        assertEquals(2, rsMovies.getInt(1))

        val rsSeries = stmt.executeQuery("SELECT COUNT(*) FROM series WHERE seriesId = 30")
        assertTrue(rsSeries.next())
        assertEquals(2, rsSeries.getInt(1))

        val rsCategories = stmt.executeQuery("SELECT COUNT(*) FROM categories WHERE categoryId = '40'")
        assertTrue(rsCategories.next())
        assertEquals(2, rsCategories.getInt(1))

        // Query Source A exclusively
        val rsA = stmt.executeQuery("SELECT name FROM series WHERE sourceId = 'src_a' AND seriesId = 30")
        assertTrue(rsA.next())
        assertEquals("Series 30 A", rsA.getString(1))

        // Query Source B exclusively
        val rsB = stmt.executeQuery("SELECT name FROM series WHERE sourceId = 'src_b' AND seriesId = 30")
        assertTrue(rsB.next())
        assertEquals("Series 30 B", rsB.getString(1))

        // Delete Source A -> Source B remains intact
        stmt.execute("DELETE FROM channels WHERE sourceId = 'src_a'")
        stmt.execute("DELETE FROM movies WHERE sourceId = 'src_a'")
        stmt.execute("DELETE FROM series WHERE sourceId = 'src_a'")
        stmt.execute("DELETE FROM categories WHERE sourceId = 'src_a'")

        val rsBAfter = stmt.executeQuery("SELECT name FROM series WHERE seriesId = 30")
        assertTrue(rsBAfter.next())
        assertEquals("Series 30 B", rsBAfter.getString(1))
        assertFalse(rsBAfter.next())

        stmt.close()
    }

    @Test
    fun testFavoriteIsolationBetweenSources() {
        initSchemaV3()
        applyMigration3To4()

        val stmt = conn.createStatement()

        // Insert channels with same streamId=55 in both sources
        stmt.execute("INSERT INTO channels VALUES ('src_a_live_55', 55, 1, 'Ch A', 'live', null, 'c1', 'Cat', null, 0, 0, 'urlA', 'ts', 'src_a')")
        stmt.execute("INSERT INTO channels VALUES ('src_b_live_55', 55, 1, 'Ch B', 'live', null, 'c1', 'Cat', null, 0, 0, 'urlB', 'ts', 'src_b')")

        // Source-aware update: only mark Source A as favorite
        val updated = stmt.executeUpdate("UPDATE channels SET isFavorite = 1 WHERE sourceId = 'src_a' AND streamId = 55")
        assertEquals(1, updated)

        // Check Source A is favorite
        val rsA = stmt.executeQuery("SELECT isFavorite FROM channels WHERE sourceId = 'src_a' AND streamId = 55")
        assertTrue(rsA.next())
        assertEquals(1, rsA.getInt(1))

        // Check Source B is NOT favorite
        val rsB = stmt.executeQuery("SELECT isFavorite FROM channels WHERE sourceId = 'src_b' AND streamId = 55")
        assertTrue(rsB.next())
        assertEquals(0, rsB.getInt(1))

        stmt.close()
    }

    @Test
    fun testExplainQueryPlan_beforeAndAfterIndexes() {
        initSchemaV3()

        val stmt = conn.createStatement()

        // 1. BEFORE migration (v3 schema):
        // Channels query by sourceId
        val planBeforeChannels = getExplainPlan("SELECT * FROM channels WHERE sourceId = 'src_1'")
        assertTrue(
            "Expected SCAN before index on channels sourceId, was: $planBeforeChannels",
            planBeforeChannels.contains("SCAN")
        )

        // Channels query by sourceId and categoryId
        val planBeforeCategory = getExplainPlan("SELECT * FROM channels WHERE sourceId = 'src_1' AND categoryId = 'cat_1'")
        assertTrue(
            "Expected SCAN before index on channels (sourceId, categoryId), was: $planBeforeCategory",
            planBeforeCategory.contains("SCAN")
        )

        // 2. APPLY MIGRATION 3->4
        applyMigration3To4()

        // 3. AFTER migration (v4 schema):
        val planAfterChannels = getExplainPlan("SELECT * FROM channels WHERE sourceId = 'src_1'")
        assertTrue(
            "Expected SEARCH USING INDEX index_channels_sourceId, was: $planAfterChannels",
            planAfterChannels.contains("SEARCH") && planAfterChannels.contains("index_channels_sourceId")
        )

        val planAfterCategory = getExplainPlan("SELECT * FROM channels WHERE sourceId = 'src_1' AND categoryId = 'cat_1'")
        assertTrue(
            "Expected SEARCH USING INDEX index_channels_sourceId_categoryId, was: $planAfterCategory",
            planAfterCategory.contains("SEARCH") && planAfterCategory.contains("index_channels_sourceId_categoryId")
        )

        val planAfterMovies = getExplainPlan("SELECT * FROM movies WHERE sourceId = 'src_1' AND categoryId = 'cat_1'")
        assertTrue(
            "Expected SEARCH USING INDEX index_movies_sourceId_categoryId, was: $planAfterMovies",
            planAfterMovies.contains("SEARCH") && planAfterMovies.contains("index_movies_sourceId_categoryId")
        )

        val planAfterSeries = getExplainPlan("SELECT * FROM series WHERE sourceId = 'src_1' AND categoryId = 'cat_1'")
        assertTrue(
            "Expected SEARCH USING INDEX index_series_sourceId_categoryId, was: $planAfterSeries",
            planAfterSeries.contains("SEARCH") && planAfterSeries.contains("index_series_sourceId_categoryId")
        )

        val planAfterCat = getExplainPlan("SELECT * FROM categories WHERE sourceId = 'src_1' AND type = 'LIVE'")
        assertTrue(
            "Expected SEARCH USING INDEX index_categories_sourceId_type, was: $planAfterCat",
            planAfterCat.contains("SEARCH") && planAfterCat.contains("index_categories_sourceId_type")
        )

        stmt.close()
    }

    @Test
    fun testPragmaIntegrityAndForeignKeyCheck() {
        initSchemaV3()
        applyMigration3To4()

        val stmt = conn.createStatement()
        val rsIntegrity = stmt.executeQuery("PRAGMA integrity_check")
        assertTrue(rsIntegrity.next())
        assertEquals("ok", rsIntegrity.getString(1))

        val rsFk = stmt.executeQuery("PRAGMA foreign_key_check")
        assertFalse("Foreign key check should return 0 violations", rsFk.next())

        stmt.close()
    }

    @Test
    fun testSqlBenchmarkFixture_largeVolume() {
        initSchemaV3()
        applyMigration3To4()

        conn.autoCommit = false
        val psChannel = conn.prepareStatement(
            "INSERT INTO channels VALUES (?, ?, ?, ?, 'live', null, ?, 'Cat', null, 0, 0, 'http://test', 'ts', ?)"
        )
        val psMovie = conn.prepareStatement(
            "INSERT INTO movies VALUES (?, ?, ?, ?, ?, '2024', null, null, 7.0, 3.5, null, ?, 'Cat', 'mp4', null, null, null, null, 7200, 'http://test', 0, ?)"
        )
        val psSeries = conn.prepareStatement(
            "INSERT INTO series VALUES (?, ?, ?, ?, ?, null, null, null, null, null, null, '2024', 8.0, 4.0, ?, 'Cat', 0, ?)"
        )

        // Insert 50,000 channels across 2 sources
        for (i in 1..50_000) {
            val src = if (i % 2 == 0) "src_a" else "src_b"
            val cat = "cat_${i % 20}"
            psChannel.setString(1, "${src}_live_$i")
            psChannel.setInt(2, i)
            psChannel.setInt(3, i)
            psChannel.setString(4, "Channel $i")
            psChannel.setString(5, cat)
            psChannel.setString(6, src)
            psChannel.addBatch()
            if (i % 5000 == 0) psChannel.executeBatch()
        }
        psChannel.executeBatch()

        // Insert 30,000 movies
        for (i in 1..30_000) {
            val src = if (i % 2 == 0) "src_a" else "src_b"
            val cat = "cat_${i % 15}"
            psMovie.setString(1, "${src}_vod_$i")
            psMovie.setInt(2, i)
            psMovie.setInt(3, i)
            psMovie.setString(4, "Movie $i")
            psMovie.setString(5, "Movie $i")
            psMovie.setString(6, cat)
            psMovie.setString(7, src)
            psMovie.addBatch()
            if (i % 5000 == 0) psMovie.executeBatch()
        }
        psMovie.executeBatch()

        // Insert 5,000 series
        for (i in 1..5_000) {
            val src = if (i % 2 == 0) "src_a" else "src_b"
            val cat = "cat_${i % 10}"
            psSeries.setString(1, "${src}_series_$i")
            psSeries.setInt(2, i)
            psSeries.setInt(3, i)
            psSeries.setString(4, "Series $i")
            psSeries.setString(5, "Series $i")
            psSeries.setString(6, cat)
            psSeries.setString(7, src)
            psSeries.addBatch()
            if (i % 2500 == 0) psSeries.executeBatch()
        }
        psSeries.executeBatch()
        conn.commit()
        conn.autoCommit = true

        psChannel.close()
        psMovie.close()
        psSeries.close()

        // Benchmark queries
        val iterations = 50

        // 1. Query channels by source (expected ~25,000 rows)
        val latenciesBySource = measureLatencies(iterations) {
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM channels WHERE sourceId = 'src_a'")
            rs.next()
            rs.close()
            stmt.close()
        }

        // 2. Query channels by source + category (expected ~1,250 rows)
        val latenciesByCat = measureLatencies(iterations) {
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM channels WHERE sourceId = 'src_a' AND categoryId = 'cat_5'")
            rs.next()
            rs.close()
            stmt.close()
        }

        // 3. Query series by source + seriesId (1 row via UNIQUE index)
        val latenciesSeries = measureLatencies(iterations) {
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery("SELECT name FROM series WHERE sourceId = 'src_a' AND seriesId = 100")
            rs.next()
            rs.close()
            stmt.close()
        }

        println("=== JVM SQLITE BENCHMARK (50K Channels, 30K Movies, 5K Series) ===")
        println("Query channels by source (25k rows): median=${latenciesBySource.first}ms, p95=${latenciesBySource.second}ms ($iterations iter)")
        println("Query channels by source+cat (1.2k rows): median=${latenciesByCat.first}ms, p95=${latenciesByCat.second}ms ($iterations iter)")
        println("Query series by source+seriesId (1 row): median=${latenciesSeries.first}ms, p95=${latenciesSeries.second}ms ($iterations iter)")

        assertTrue(latenciesByCat.first < 20.0) // Indexed lookup should be sub-20ms in memory
    }

    private fun measureLatencies(iterations: Int, block: () -> Unit): Pair<Double, Double> {
        val times = ArrayList<Double>(iterations)
        repeat(iterations) {
            val t0 = System.nanoTime()
            block()
            val t1 = System.nanoTime()
            times.add((t1 - t0) / 1_000_000.0)
        }
        times.sort()
        val median = times[times.size / 2]
        val p95Index = (times.size * 0.95).toInt().coerceAtMost(times.size - 1)
        val p95 = times[p95Index]
        return Pair(median, p95)
    }

    private fun getExplainPlan(sql: String): String {
        val stmt = conn.createStatement()
        val rs = stmt.executeQuery("EXPLAIN QUERY PLAN $sql")
        val sb = StringBuilder()
        while (rs.next()) {
            sb.append(rs.getString("detail")).append("\n")
        }
        rs.close()
        stmt.close()
        return sb.toString()
    }
}
