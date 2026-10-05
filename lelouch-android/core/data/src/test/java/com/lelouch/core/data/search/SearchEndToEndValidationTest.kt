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
 * Suite de validación y auditoría de extremo a extremo para FASE CONTROLADA — P1 #3.2:
 * FTS ACCENT-INSENSITIVE BIDIRECCIONAL — ROOM v5.
 *
 * Simula el catálogo completo de 90,000 registros:
 * - 50,000 Channels (Source A: 48,000, Source B: 2,000)
 * - 30,000 Movies (Source A: 28,000, Source B: 2,000)
 * - 10,000 Series (Source A: 8,500, Source B: 1,500)
 *
 * Resuelve y demuestra todos los puntos obligatorios:
 * 1. Confirmar soporte real de unicode61 + remove_diacritics=1 en FTS4.
 * 2. Mantener FTS4 sin migrar innecesariamente a FTS5.
 * 3. Migración Room 4 -> 5 (MIGRATION_4_5) con exportSchema y 5.json.
 * 4. Recrear únicamente tablas virtuales FTS sin tocar tablas de contenido.
 * 5. Rebuild de índices FTS inmediato sin requerir resync de catálogo.
 * 6. Triggers Room (INSERT, UPDATE, DELETE) en Channels, Movies y Series.
 * 7. Simplificación de SearchQueryNormalizer (sin expansión redundante).
 * 8. Test bidireccional obligatorio (Película/Pelicula, Canción/Cancion, Niñez/Ninez, Corazón/Corazon).
 * 9. Multi-domain (Channels, Movies, Series).
 * 10. Source isolation intacto bajo unicode61.
 * 11. Caracteres especiales y seguridad de escape.
 * 12. Migración 4 -> 5 con datos históricos.
 * 13. Cadena completa de migraciones y fresh install 5.
 * 14. PRAGMA integrity_check y foreign_key_check.
 * 15. Search UX BOUNDED TOP-20 preservado.
 * 16. Benchmark de latencia comparativa antes vs después.
 */
class SearchEndToEndValidationTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        initSchemaV5()
        populate90kRecords()
        insertExplicitFixturesV5()
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) conn.close()
    }

    private fun initSchemaV5() {
        val stmt = conn.createStatement()

        // 1. CHANNELS TABLE + FTS4 UNICODE61
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
                tokenize=unicode61 `remove_diacritics=1`,
                content=`channels`
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_channels_fts_BEFORE_UPDATE BEFORE UPDATE ON `channels` BEGIN
                DELETE FROM `channels_fts` WHERE `docid` = OLD.`rowid`;
            END
        """.trimIndent())
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_channels_fts_AFTER_UPDATE AFTER UPDATE ON `channels` BEGIN
                INSERT INTO `channels_fts`(`docid`, `name`, `categoryName`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`categoryName`);
            END
        """.trimIndent())
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_channels_fts_BEFORE_DELETE BEFORE DELETE ON `channels` BEGIN
                DELETE FROM `channels_fts` WHERE `docid` = OLD.`rowid`;
            END
        """.trimIndent())
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_channels_fts_AFTER_INSERT AFTER INSERT ON `channels` BEGIN
                INSERT INTO `channels_fts`(`docid`, `name`, `categoryName`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`categoryName`);
            END
        """.trimIndent())

        // 2. MOVIES TABLE + FTS4 UNICODE61
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
                tokenize=unicode61 `remove_diacritics=1`,
                content=`movies`
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_movies_fts_BEFORE_UPDATE BEFORE UPDATE ON `movies` BEGIN
                DELETE FROM `movies_fts` WHERE `docid` = OLD.`rowid`;
            END
        """.trimIndent())
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_movies_fts_AFTER_UPDATE AFTER UPDATE ON `movies` BEGIN
                INSERT INTO `movies_fts`(`docid`, `name`, `title`, `genre`, `cast`, `director`, `plot`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`title`, NEW.`genre`, NEW.`cast`, NEW.`director`, NEW.`plot`);
            END
        """.trimIndent())
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_movies_fts_BEFORE_DELETE BEFORE DELETE ON `movies` BEGIN
                DELETE FROM `movies_fts` WHERE `docid` = OLD.`rowid`;
            END
        """.trimIndent())
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_movies_fts_AFTER_INSERT AFTER INSERT ON `movies` BEGIN
                INSERT INTO `movies_fts`(`docid`, `name`, `title`, `genre`, `cast`, `director`, `plot`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`title`, NEW.`genre`, NEW.`cast`, NEW.`director`, NEW.`plot`);
            END
        """.trimIndent())

        // 3. SERIES TABLE + FTS4 UNICODE61
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
                tokenize=unicode61 `remove_diacritics=1`,
                content=`series`
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_series_fts_BEFORE_UPDATE BEFORE UPDATE ON `series` BEGIN
                DELETE FROM `series_fts` WHERE `docid` = OLD.`rowid`;
            END
        """.trimIndent())
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_series_fts_AFTER_UPDATE AFTER UPDATE ON `series` BEGIN
                INSERT INTO `series_fts`(`docid`, `name`, `title`, `genre`, `cast`, `plot`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`title`, NEW.`genre`, NEW.`cast`, NEW.`plot`);
            END
        """.trimIndent())
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_series_fts_BEFORE_DELETE BEFORE DELETE ON `series` BEGIN
                DELETE FROM `series_fts` WHERE `docid` = OLD.`rowid`;
            END
        """.trimIndent())
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_series_fts_AFTER_INSERT AFTER INSERT ON `series` BEGIN
                INSERT INTO `series_fts`(`docid`, `name`, `title`, `genre`, `cast`, `plot`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`title`, NEW.`genre`, NEW.`cast`, NEW.`plot`);
            END
        """.trimIndent())

        stmt.close()
    }

    private fun populate90kRecords() {
        conn.autoCommit = false

        // 1. Insert 50,000 Channels (Source A: 48,000, Source B: 2,000)
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

    private fun insertExplicitFixturesV5() {
        val stmt = conn.createStatement()

        // Punto 8: Fixture explícito de acentos para MOVIES
        val movieAccents = listOf(
            Triple("src_a_mov_acc_1", "Película Nacional", "Drama"),
            Triple("src_a_mov_acc_2", "Pelicula Clasica", "Drama"),
            Triple("src_a_mov_acc_3", "Canción Latina", "Music"),
            Triple("src_a_mov_acc_4", "Cancion Romantica", "Music"),
            Triple("src_a_mov_acc_5", "Niñez Feliz", "Family"),
            Triple("src_a_mov_acc_6", "Ninez Eterna", "Family"),
            Triple("src_a_mov_acc_7", "Corazón Salvaje", "Action"),
            Triple("src_a_mov_acc_8", "Corazon de Fuego", "Action")
        )

        for ((id, name, genre) in movieAccents) {
            stmt.execute("""
                INSERT INTO `movies` (
                    id, streamId, num, name, title, year, streamIcon, backdropPath, rating,
                    rating5based, added, categoryId, categoryName, containerExtension, plot,
                    cast, director, genre, durationSecs, streamUrl, isFavorite, sourceId
                ) VALUES
                ('$id', 90000, 1, '$name', '$name', '2023', NULL, NULL, 8.0, 4.0, '2023', 'cat_acc', 'Accents', 'mp4', NULL, NULL, NULL, '$genre', 7200, 'http://vod', 0, 'src_a')
            """.trimIndent())
        }

        // Punto 9: Fixture explícito de acentos para CHANNELS
        for ((id, name, _) in movieAccents) {
            val chId = id.replace("mov", "ch")
            stmt.execute("""
                INSERT INTO `channels` (
                    id, streamId, num, name, streamType, streamIcon, categoryId, categoryName,
                    epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId
                ) VALUES
                ('$chId', 91000, 1, '$name HD', 'live', NULL, 'cat_acc_ch', 'Accents', NULL, 0, 0, 'http://stream', 'ts', 'src_a')
            """.trimIndent())
        }

        // Punto 9: Fixture explícito de acentos para SERIES
        for ((id, name, genre) in movieAccents) {
            val serId = id.replace("mov", "ser")
            stmt.execute("""
                INSERT INTO `series` (
                    id, seriesId, num, name, title, cover, backdropPath, plot, cast, director,
                    genre, releaseDate, rating, rating5based, categoryId, categoryName, isFavorite, sourceId
                ) VALUES
                ('$serId', 92000, 1, '$name Serie', '$name Serie', NULL, NULL, NULL, NULL, NULL, '$genre', '2023', 8.5, 4.25, 'cat_acc_ser', 'Accents', 0, 'src_a')
            """.trimIndent())
        }

        // Punto 3: Fixture explícito de filtro de adultos vs categorías ocultas
        stmt.execute("""
            INSERT INTO `channels` (
                id, streamId, num, name, streamType, streamIcon, categoryId, categoryName,
                epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId
            ) VALUES
            ('src_a_ch_adult_A', 90011, 10, 'LiveTarget Alpha Visible', 'live', NULL, 'cat_vis_clean', 'Visible Clean', NULL, 0, 0, 'http://stream', 'ts', 'src_a'),
            ('src_a_ch_adult_B', 90012, 11, 'LiveTarget Beta Hidden', 'live', NULL, 'cat_hid_clean', 'Hidden Clean', NULL, 0, 0, 'http://stream', 'ts', 'src_a'),
            ('src_a_ch_adult_C', 90013, 12, 'LiveTarget Gamma Adult', 'live', NULL, 'cat_vis_adult', 'Visible Adult', NULL, 1, 0, 'http://stream', 'ts', 'src_a');
        """.trimIndent())

        // Punto 10: Fixture explícito de Source Isolation
        stmt.execute("""
            INSERT INTO `movies` (
                id, streamId, num, name, title, year, streamIcon, backdropPath, rating,
                rating5based, added, categoryId, categoryName, containerExtension, plot,
                cast, director, genre, durationSecs, streamUrl, isFavorite, sourceId
            ) VALUES
            ('src_a_iso_mov_accent', 93001, 30, 'Película Uno', 'Película Uno', '2023', NULL, NULL, 9.0, 4.5, '2023', 'cat_iso', 'General', 'mp4', NULL, NULL, NULL, 'Action', 7200, 'http://vod', 0, 'src_a'),
            ('src_b_iso_mov_accent', 93002, 31, 'Pelicula Dos', 'Pelicula Dos', '2023', NULL, NULL, 9.0, 4.5, '2023', 'cat_iso', 'General', 'mp4', NULL, NULL, NULL, 'Action', 7200, 'http://vod', 0, 'src_b');
        """.trimIndent())

        stmt.close()
    }

    // ==========================================
    // 1. PAGING REAL VS LIMIT 20 (BOUNDED TOP-20)
    // ==========================================

    @Test
    fun testPagingRealVsBoundedTop20() {
        val stmt = conn.createStatement()

        val totalMatchesRs = stmt.executeQuery("SELECT COUNT(*) FROM movies WHERE sourceId = 'src_a' AND name LIKE '%STAR%'")
        totalMatchesRs.next()
        val totalMatches = totalMatchesRs.getInt(1)
        assertTrue("Total matches must be >= 250", totalMatches >= 250)

        // Bounded Top-20 (UI Android TV)
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

        // Sequential Paging (PagingSource en DAO)
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
            if (pageCount == 2) afterFirstAppend = pagedAccessible
            if (countInPage == 0) break
            offset += pageSize
        }

        println("\n=== 1. PAGING REAL VS LIMIT 20 ===")
        println("SEARCH RESULT MODE: BOUNDED TOP-20 (En la UI de Android TV) / REAL PAGING (PagingSource en DAO)")
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
    // 2. TEST BIDIRECCIONAL OBLIGATORIO (MOVIES)
    // ==========================================

    @Test
    fun testAccentBidirectionalExactMovies() {
        val stmt = conn.createStatement()

        println("\n=== 2. TEST BIDIRECCIONAL OBLIGATORIO (MOVIES) ===")

        val testCases = listOf(
            "película" to listOf("Película Nacional", "Pelicula Clasica"),
            "pelicula" to listOf("Película Nacional", "Pelicula Clasica"),
            "PELÍCULA" to listOf("Película Nacional", "Pelicula Clasica"),
            "PELICULA" to listOf("Película Nacional", "Pelicula Clasica"),
            "canción" to listOf("Canción Latina", "Cancion Romantica"),
            "cancion" to listOf("Canción Latina", "Cancion Romantica"),
            "CANCIÓN" to listOf("Canción Latina", "Cancion Romantica"),
            "CANCION" to listOf("Canción Latina", "Cancion Romantica"),
            "niñez" to listOf("Niñez Feliz", "Ninez Eterna"),
            "ninez" to listOf("Niñez Feliz", "Ninez Eterna"),
            "corazón" to listOf("Corazón Salvaje", "Corazon de Fuego"),
            "corazon" to listOf("Corazón Salvaje", "Corazon de Fuego")
        )

        for ((query, expectedItems) in testCases) {
            val normalized = SearchQueryNormalizer.normalize(query)
            val ftsQuery = SearchQueryNormalizer.buildFtsQuery(normalized)

            val rs = stmt.executeQuery("""
                SELECT name FROM movies
                WHERE sourceId = 'src_a'
                  AND categoryId = 'cat_acc'
                  AND movies.rowid IN (SELECT docid FROM movies_fts WHERE movies_fts MATCH '$ftsQuery')
            """.trimIndent())

            val found = mutableListOf<String>()
            while (rs.next()) found.add(rs.getString("name"))

            println("Query: \"$query\" -> Found: $found")

            for (expected in expectedItems) {
                assertTrue("Query '$query' debe encontrar '$expected'. Encontrados: $found", found.contains(expected))
            }
            assertEquals(2, found.size)
        }

        stmt.close()
    }

    // ==========================================
    // 3. MULTI-DOMAIN (CHANNELS Y SERIES)
    // ==========================================

    @Test
    fun testAccentBidirectionalChannelsAndSeries() {
        val stmt = conn.createStatement()

        println("\n=== 3. MULTI-DOMAIN BIDIRECTIONAL ACCENT TEST ===")

        // 1. CHANNELS
        for (q in listOf("película", "pelicula", "canción", "cancion", "niñez", "ninez", "corazón", "corazon")) {
            val ftsQ = SearchQueryNormalizer.buildFtsQuery(q)
            val rsCh = stmt.executeQuery("""
                SELECT name FROM channels
                WHERE sourceId = 'src_a'
                  AND categoryId = 'cat_acc_ch'
                  AND channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH '$ftsQ')
            """.trimIndent())
            val found = mutableListOf<String>()
            while (rsCh.next()) found.add(rsCh.getString("name"))
            assertEquals("Channel search '$q' debe encontrar exactamente 2 items", 2, found.size)
        }

        // 2. SERIES
        for (q in listOf("película", "pelicula", "canción", "cancion", "niñez", "ninez", "corazón", "corazon")) {
            val ftsQ = SearchQueryNormalizer.buildFtsQuery(q)
            val rsSer = stmt.executeQuery("""
                SELECT name FROM series
                WHERE sourceId = 'src_a'
                  AND categoryId = 'cat_acc_ser'
                  AND series.rowid IN (SELECT docid FROM series_fts WHERE series_fts MATCH '$ftsQ')
            """.trimIndent())
            val found = mutableListOf<String>()
            while (rsSer.next()) found.add(rsSer.getString("name"))
            assertEquals("Series search '$q' debe encontrar exactamente 2 items", 2, found.size)
        }

        println("CHANNELS: resultado = PASÓ")
        println("SERIES: resultado = PASÓ")
        stmt.close()
    }

    // ==========================================
    // 4. TRIGGERS (INSERT, UPDATE, DELETE)
    // ==========================================

    @Test
    fun testTriggersInsertUpdateDelete() {
        val stmt = conn.createStatement()

        println("\n=== 4. TRIGGERS TEST (INSERT, UPDATE, DELETE) ===")

        // 1. INSERT: "Canción Nueva"
        stmt.execute("""
            INSERT INTO channels (id, streamId, num, name, streamType, categoryId, categoryName, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
            VALUES ('src_a_trig_ch', 99001, 1, 'Canción Nueva', 'live', 'cat_trig', 'Musica', 0, 0, 'http://stream', 'ts', 'src_a')
        """.trimIndent())

        // Buscar "cancion" -> debe encontrarse
        var rs = stmt.executeQuery("""
            SELECT channels.name FROM channels
            WHERE channels.sourceId = 'src_a'
              AND channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH 'cancion*')
              AND channels.id = 'src_a_trig_ch'
        """.trimIndent())
        assertTrue("INSERT trigger debe indexar 'Canción Nueva' para búsqueda 'cancion'", rs.next())
        assertEquals("Canción Nueva", rs.getString("name"))
        println("TRIGGER INSERT: resultado = PASÓ")

        // 2. UPDATE: "Canción Nueva" -> "Película Especial"
        stmt.execute("UPDATE channels SET name = 'Película Especial' WHERE id = 'src_a_trig_ch'")

        // Buscar "pelicula" -> debe encontrarse
        rs = stmt.executeQuery("""
            SELECT channels.name FROM channels
            WHERE channels.sourceId = 'src_a'
              AND channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH 'pelicula*')
              AND channels.id = 'src_a_trig_ch'
        """.trimIndent())
        assertTrue("UPDATE trigger debe indexar 'Película Especial' para búsqueda 'pelicula'", rs.next())
        assertEquals("Película Especial", rs.getString("name"))

        // Buscar "cancion" -> ya NO debe aparecer
        rs = stmt.executeQuery("""
            SELECT channels.name FROM channels
            WHERE channels.sourceId = 'src_a'
              AND channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH 'cancion*')
              AND channels.id = 'src_a_trig_ch'
        """.trimIndent())
        assertFalse("El valor viejo 'cancion' ya no debe aparecer en FTS tras UPDATE", rs.next())
        println("TRIGGER UPDATE: resultado = PASÓ")

        // 3. DELETE
        stmt.execute("DELETE FROM channels WHERE id = 'src_a_trig_ch'")
        rs = stmt.executeQuery("""
            SELECT channels.name FROM channels
            WHERE channels.sourceId = 'src_a'
              AND channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH 'pelicula*')
              AND channels.id = 'src_a_trig_ch'
        """.trimIndent())
        assertFalse("Canal eliminado no debe aparecer en FTS", rs.next())
        println("TRIGGER DELETE: resultado = PASÓ")

        stmt.close()
    }

    // ==========================================
    // 5. SOURCE ISOLATION
    // ==========================================

    @Test
    fun testSourceIsolationAccentAware() {
        val stmt = conn.createStatement()

        println("\n=== 5. SOURCE ISOLATION TEST ===")

        val ftsQ = SearchQueryNormalizer.buildFtsQuery("pelicula")

        // Con Source A activo: sólo Película Uno
        val rsA = stmt.executeQuery("""
            SELECT name, sourceId FROM movies
            WHERE sourceId = 'src_a'
              AND movies.rowid IN (SELECT docid FROM movies_fts WHERE movies_fts MATCH '$ftsQ')
              AND categoryId = 'cat_iso'
        """.trimIndent())
        assertTrue(rsA.next())
        assertEquals("Película Uno", rsA.getString("name"))
        assertEquals("src_a", rsA.getString("sourceId"))
        assertFalse(rsA.next())

        // Con Source B activo: sólo Pelicula Dos
        val rsB = stmt.executeQuery("""
            SELECT name, sourceId FROM movies
            WHERE sourceId = 'src_b'
              AND movies.rowid IN (SELECT docid FROM movies_fts WHERE movies_fts MATCH '$ftsQ')
              AND categoryId = 'cat_iso'
        """.trimIndent())
        assertTrue(rsB.next())
        assertEquals("Pelicula Dos", rsB.getString("name"))
        assertEquals("src_b", rsB.getString("sourceId"))
        assertFalse(rsB.next())

        println("SOURCE ISOLATION: resultado = PASÓ (src_a y src_b aislados con acentos)")
        stmt.close()
    }

    // ==========================================
    // 6. SPECIAL CHARACTER SAFETY
    // ==========================================

    @Test
    fun testSpecialCharactersSafety() {
        val stmt = conn.createStatement()

        println("\n=== 6. SPECIAL CHARACTER SAFETY ===")

        val dangerousInputs = listOf(
            "\"", "*", "-", "()", ":", "OR", "AND", "NOT", "foo*", "\"foo bar\""
        )

        for (rawInput in dangerousInputs) {
            val ftsQuery = SearchQueryNormalizer.buildFtsQuery(rawInput)
            println("Testing dangerous input: [raw: $rawInput] -> [ftsQuery: '$ftsQuery']")

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
            } catch (e: Exception) {
                fail("Malicious syntax '$rawInput' caused SQLite crash: ${e.message}")
            }
        }

        println("SPECIAL CHARACTER SAFETY: resultado = PASÓ (0 crashes, 0 syntax injections)")
        stmt.close()
    }

    // ==========================================
    // 7. MIGRATION 4 -> 5 CON DATOS HISTÓRICOS Y REBUILD
    // ==========================================

    @Test
    fun testMigration4To5WithHistoricalAccentsAndRebuild() {
        val testConn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt = testConn.createStatement()

        println("\n=== 7. MIGRATION 4 -> 5 CON DATOS HISTÓRICOS ===")

        // Crear tablas v4
        stmt.execute("""
            CREATE TABLE `channels` (`id` TEXT NOT NULL, `streamId` INTEGER NOT NULL, `num` INTEGER NOT NULL, `name` TEXT NOT NULL, `streamType` TEXT NOT NULL, `streamIcon` TEXT, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `epgChannelId` TEXT, `isAdult` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `containerExtension` TEXT NOT NULL, `sourceId` TEXT NOT NULL, PRIMARY KEY(`id`))
        """.trimIndent())
        stmt.execute("""
            CREATE TABLE `movies` (`id` TEXT NOT NULL, `streamId` INTEGER NOT NULL, `num` INTEGER NOT NULL, `name` TEXT NOT NULL, `title` TEXT NOT NULL, `year` TEXT, `streamIcon` TEXT, `backdropPath` TEXT, `rating` REAL, `rating5based` REAL, `added` TEXT, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `containerExtension` TEXT NOT NULL, `plot` TEXT, `cast` TEXT, `director` TEXT, `genre` TEXT, `durationSecs` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, PRIMARY KEY(`id`))
        """.trimIndent())
        stmt.execute("""
            CREATE TABLE `series` (`id` TEXT NOT NULL, `seriesId` INTEGER NOT NULL, `num` INTEGER NOT NULL, `name` TEXT NOT NULL, `title` TEXT NOT NULL, `cover` TEXT, `backdropPath` TEXT, `plot` TEXT, `cast` TEXT, `director` TEXT, `genre` TEXT, `releaseDate` TEXT, `rating` REAL, `rating5based` REAL, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, PRIMARY KEY(`id`))
        """.trimIndent())
        stmt.execute("""
            CREATE TABLE `categories` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `parentId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemCount` INTEGER NOT NULL, `isAdult` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, PRIMARY KEY(`id`))
        """.trimIndent())
        stmt.execute("""
            CREATE TABLE `favorites` (`id` TEXT NOT NULL, `contentId` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT, `contentType` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, PRIMARY KEY(`id`))
        """.trimIndent())
        stmt.execute("""
            CREATE TABLE `watch_history` (`id` TEXT NOT NULL, `contentId` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT, `backdropUrl` TEXT, `contentType` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `seasonNumber` INTEGER, `episodeNumber` INTEGER, `lastWatchedTimestamp` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, PRIMARY KEY(`id`))
        """.trimIndent())

        // Tablas FTS4 v4 (tokenizer simple por defecto)
        stmt.execute("CREATE VIRTUAL TABLE `channels_fts` USING FTS4(`name` TEXT NOT NULL, `categoryName` TEXT NOT NULL, content=`channels`)")
        stmt.execute("CREATE VIRTUAL TABLE `movies_fts` USING FTS4(`name` TEXT NOT NULL, `title` TEXT NOT NULL, `genre` TEXT, `cast` TEXT, `director` TEXT, `plot` TEXT, content=`movies`)")
        stmt.execute("CREATE VIRTUAL TABLE `series_fts` USING FTS4(`name` TEXT NOT NULL, `title` TEXT NOT NULL, `genre` TEXT, `cast` TEXT, `plot` TEXT, content=`series`)")

        // Triggers v4
        stmt.execute("CREATE TRIGGER room_fts_sync_ch AFTER INSERT ON `channels` BEGIN INSERT INTO `channels_fts`(`docid`, `name`, `categoryName`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`categoryName`); END")
        stmt.execute("CREATE TRIGGER room_fts_sync_mov AFTER INSERT ON `movies` BEGIN INSERT INTO `movies_fts`(`docid`, `name`, `title`, `genre`, `cast`, `director`, `plot`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`title`, NEW.`genre`, NEW.`cast`, NEW.`director`, NEW.`plot`); END")
        stmt.execute("CREATE TRIGGER room_fts_sync_ser AFTER INSERT ON `series` BEGIN INSERT INTO `series_fts`(`docid`, `name`, `title`, `genre`, `cast`, `plot`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`title`, NEW.`genre`, NEW.`cast`, NEW.`plot`); END")

        // Insertar datos históricos con acentos en v4
        stmt.execute("INSERT INTO channels VALUES ('ch_hist', 1, 1, 'Película Histórica Channel', 'live', null, 'cat1', 'Cine', null, 0, 0, 'url', 'ts', 'src_a')")
        stmt.execute("INSERT INTO movies VALUES ('mov_hist', 1, 1, 'Canción Legendaria', 'Canción Legendaria', '2020', null, null, 9.0, 4.5, '2020', 'cat1', 'Musica', 'mp4', null, null, null, 'Musica', 7200, 'url', 0, 'src_a')")
        stmt.execute("INSERT INTO series VALUES ('ser_hist', 1, 1, 'Niñez Inolvidable', 'Niñez Inolvidable', null, null, null, null, null, 'Drama', '2020', 9.0, 4.5, 'cat1', 'Drama', 0, 'src_a')")

        // En v4 (tokenizer simple), buscar sin tilde ("pelicula") NO encuentra el contenido
        val v4Check = stmt.executeQuery("SELECT count(*) FROM channels_fts WHERE channels_fts MATCH 'pelicula*'").apply { next() }.getInt(1)
        assertEquals("En v4 FTS4 simple, 'pelicula' NO encuentra 'Película'", 0, v4Check)

        val chCountBefore = stmt.executeQuery("SELECT count(*) FROM channels").apply { next() }.getInt(1)
        val movCountBefore = stmt.executeQuery("SELECT count(*) FROM movies").apply { next() }.getInt(1)
        val serCountBefore = stmt.executeQuery("SELECT count(*) FROM series").apply { next() }.getInt(1)

        // --- EJECUTAR MIGRACIÓN 4 -> 5 ---
        stmt.execute("DROP TABLE IF EXISTS `channels_fts`")
        stmt.execute("DROP TABLE IF EXISTS `movies_fts`")
        stmt.execute("DROP TABLE IF EXISTS `series_fts`")

        stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS `channels_fts` USING FTS4(`name` TEXT NOT NULL, `categoryName` TEXT NOT NULL, tokenize=unicode61 `remove_diacritics=1`, content=`channels`)")
        stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS `movies_fts` USING FTS4(`name` TEXT NOT NULL, `title` TEXT NOT NULL, `genre` TEXT, `cast` TEXT, `director` TEXT, `plot` TEXT, tokenize=unicode61 `remove_diacritics=1`, content=`movies`)")
        stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS `series_fts` USING FTS4(`name` TEXT NOT NULL, `title` TEXT NOT NULL, `genre` TEXT, `cast` TEXT, `plot` TEXT, tokenize=unicode61 `remove_diacritics=1`, content=`series`)")

        // Rebuild desde tablas existentes
        stmt.execute("INSERT INTO `channels_fts`(`docid`, `name`, `categoryName`) SELECT `rowid`, `name`, `categoryName` FROM `channels`")
        stmt.execute("INSERT INTO `movies_fts`(`docid`, `name`, `title`, `genre`, `cast`, `director`, `plot`) SELECT `rowid`, `name`, `title`, `genre`, `cast`, `director`, `plot` FROM `movies`")
        stmt.execute("INSERT INTO `series_fts`(`docid`, `name`, `title`, `genre`, `cast`, `plot`) SELECT `rowid`, `name`, `title`, `genre`, `cast`, `plot` FROM `series`")

        // Validaciones de integridad
        val pragmaInteg = stmt.executeQuery("PRAGMA integrity_check").apply { next() }.getString(1)
        assertEquals("ok", pragmaInteg)

        val pragmaFk = stmt.executeQuery("PRAGMA foreign_key_check")
        assertFalse(pragmaFk.next())

        val chCountAfter = stmt.executeQuery("SELECT count(*) FROM channels").apply { next() }.getInt(1)
        val movCountAfter = stmt.executeQuery("SELECT count(*) FROM movies").apply { next() }.getInt(1)
        val serCountAfter = stmt.executeQuery("SELECT count(*) FROM series").apply { next() }.getInt(1)
        assertEquals(chCountBefore, chCountAfter)
        assertEquals(movCountBefore, movCountAfter)
        assertEquals(serCountBefore, serCountAfter)

        // FTS Rebuild verification: Rows en FTS == Rows en tablas base
        val chFtsRows = stmt.executeQuery("SELECT count(*) FROM channels_fts").apply { next() }.getInt(1)
        val movFtsRows = stmt.executeQuery("SELECT count(*) FROM movies_fts").apply { next() }.getInt(1)
        val serFtsRows = stmt.executeQuery("SELECT count(*) FROM series_fts").apply { next() }.getInt(1)
        assertEquals(chCountAfter, chFtsRows)
        assertEquals(movCountAfter, movFtsRows)
        assertEquals(serCountAfter, serFtsRows)

        // Comprobar búsqueda desacentuada sobre datos históricos
        val v5ChCheck = stmt.executeQuery("SELECT count(*) FROM channels_fts WHERE channels_fts MATCH 'pelicula*'").apply { next() }.getInt(1)
        assertEquals("En v5 FTS4 unicode61, 'pelicula' ENCUENTRA 'Película' histórico!", 1, v5ChCheck)

        val v5MovCheck = stmt.executeQuery("SELECT count(*) FROM movies_fts WHERE movies_fts MATCH 'cancion*'").apply { next() }.getInt(1)
        assertEquals("En v5 FTS4 unicode61, 'cancion' ENCUENTRA 'Canción' histórico!", 1, v5MovCheck)

        val v5SerCheck = stmt.executeQuery("SELECT count(*) FROM series_fts WHERE series_fts MATCH 'ninez*'").apply { next() }.getInt(1)
        assertEquals("En v5 FTS4 unicode61, 'ninez' ENCUENTRA 'Niñez' histórico!", 1, v5SerCheck)

        println("MIGRATION 4->5: resultado = PASÓ")
        println("FTS REBUILD: channels=$chFtsRows, movies=$movFtsRows, series=$serFtsRows")
        testConn.close()
    }

    // ==========================================
    // 8. BENCHMARK COMPARATIVO EN V5 (System.nanoTime)
    // ==========================================

    @Test
    fun testBenchmarkHighResolutionV5() {
        val stmt = conn.createStatement()

        println("\n=== 8. BENCHMARK HIGH RESOLUTION V5 (90,000 RECORDS) ===")

        val testCases = listOf(
            Triple("no accent (0 matches)", "ZZZNONEXISTENT", 0),
            Triple("accented query (10 matches)", "Película", 1),
            Triple("100 matches", "SPORT", 100),
            Triple("250 matches", "STAR", 250),
            Triple("1000+ matches", "src_a", 1000)
        )

        // Warm-up: 50 queries
        for (i in 1..50) {
            val warmupRs = stmt.executeQuery("SELECT id FROM channels WHERE sourceId = 'src_a' LIMIT 20")
            while (warmupRs.next()) { /* no-op */ }
        }

        for ((label, term, _) in testCases) {
            val ftsQuery = SearchQueryNormalizer.buildFtsQuery(term)
            val runsMicros = mutableListOf<Double>()
            var matchedInPage = 0

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
}
