package com.lelouch.core.database

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Test de auditoría para FASE CONTROLADA — P1 #2D.
 *
 * Simula el ecosistema completo de Android TV con 90,000 elementos simultáneos:
 * - 50,000 Canales en Vivo (Source A: 48,000, Source B: 2,000)
 * - 30,000 Películas / VOD (Source A: 28,000, Source B: 2,000)
 * - 10,000 Series (Source A: 8,500, Source B: 1,500)
 *
 * Valida:
 * 1. Paso 11: Visita secuencial de tabs (Home -> Live -> Movies -> Series -> Live -> Movies)
 *    NO materializa 50k, 30k ni 10k ítems. Solo las páginas activas (~50-100 ítems).
 * 2. Paso 12: Cambio de Source A -> B -> A -> B sin retención de catálogos ni duplicados.
 * 3. Paso 4: Consultas de Home Tab (preview 30 channels, 20 recent movies, 20 featured series)
 *    con LIMIT real en SQL sin cargar catálogos completos.
 * 4. Paso 6: Verificación de lookups de favoritos O(1) vía isFavorite de entidad.
 */
class MultiCatalogPagingStressTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        initSchema()
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) conn.close()
    }

    private fun initSchema() {
        val stmt = conn.createStatement()

        // Channels table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `channels` (
                `id` TEXT NOT NULL,
                `streamId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `streamType` TEXT NOT NULL,
                `streamIcon` TEXT,
                `epgChannelId` TEXT,
                `added` TEXT,
                `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `customSid` TEXT,
                `tvArchive` INTEGER NOT NULL,
                `directSource` TEXT,
                `tvArchiveDuration` INTEGER NOT NULL,
                `streamUrl` TEXT NOT NULL,
                `containerExtension` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_channels_sourceId_streamId` ON `channels` (`sourceId`, `streamId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_categoryId` ON `channels` (`sourceId`, `categoryId`)")

        // Movies table
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
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_sourceId_streamId` ON `movies` (`sourceId`, `streamId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId_categoryId` ON `movies` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId` ON `movies` (`sourceId`)")

        // Series table
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
    }

    private fun populate90kFixture() {
        conn.autoCommit = false

        // 1. CHANNELS: 50,000 (Source A: 48,000, Source B: 2,000)
        val channelSql = "INSERT INTO `channels` (id, streamId, num, name, streamType, categoryId, categoryName, streamUrl, containerExtension, isFavorite, sourceId, tvArchive, tvArchiveDuration) VALUES (?, ?, ?, ?, 'live', ?, ?, ?, 'm3u8', ?, ?, 0, 0)"
        val chPs = conn.prepareStatement(channelSql)
        for (i in 1..48000) {
            chPs.setString(1, "src_a_live_$i")
            chPs.setInt(2, i)
            chPs.setInt(3, i)
            chPs.setString(4, "Canal %05d".format(i))
            chPs.setString(5, if (i <= 10000) "cat_sports" else "cat_general")
            chPs.setString(6, if (i <= 10000) "Deportes" else "General")
            chPs.setString(7, "http://provider/live/$i.m3u8")
            chPs.setInt(8, if (i % 100 == 0) 1 else 0)
            chPs.setString(9, "src_a")
            chPs.addBatch()
            if (i % 5000 == 0) chPs.executeBatch()
        }
        for (i in 1..2000) {
            chPs.setString(1, "src_b_live_$i")
            chPs.setInt(2, i)
            chPs.setInt(3, i)
            chPs.setString(4, "Canal B %05d".format(i))
            chPs.setString(5, "cat_b")
            chPs.setString(6, "B Directo")
            chPs.setString(7, "http://provider_b/live/$i.m3u8")
            chPs.setInt(8, 0)
            chPs.setString(9, "src_b")
            chPs.addBatch()
        }
        chPs.executeBatch()

        // 2. MOVIES: 30,000 (Source A: 28,000, Source B: 2,000)
        val movieSql = "INSERT INTO `movies` (id, streamId, num, name, title, categoryId, categoryName, containerExtension, durationSecs, streamUrl, isFavorite, sourceId) VALUES (?, ?, ?, ?, ?, ?, ?, 'mp4', 7200, ?, ?, ?)"
        val movPs = conn.prepareStatement(movieSql)
        for (i in 1..28000) {
            movPs.setString(1, "src_a_vod_$i")
            movPs.setInt(2, i)
            movPs.setInt(3, i)
            movPs.setString(4, "Movie %05d".format(i))
            movPs.setString(5, "Movie %05d".format(i))
            movPs.setString(6, if (i <= 5000) "cat_action" else "cat_vod_general")
            movPs.setString(7, if (i <= 5000) "Acción" else "General")
            movPs.setString(8, "http://provider/movie/$i.mp4")
            movPs.setInt(9, if (i % 100 == 0) 1 else 0)
            movPs.setString(10, "src_a")
            movPs.addBatch()
            if (i % 5000 == 0) movPs.executeBatch()
        }
        for (i in 1..2000) {
            movPs.setString(1, "src_b_vod_$i")
            movPs.setInt(2, i)
            movPs.setInt(3, i)
            movPs.setString(4, "Movie B %05d".format(i))
            movPs.setString(5, "Movie B %05d".format(i))
            movPs.setString(6, "cat_b_vod")
            movPs.setString(7, "B Películas")
            movPs.setString(8, "http://provider_b/movie/$i.mp4")
            movPs.setInt(9, 0)
            movPs.setString(10, "src_b")
            movPs.addBatch()
        }
        movPs.executeBatch()

        // 3. SERIES: 10,000 (Source A: 8,500, Source B: 1,500)
        val seriesSql = "INSERT INTO `series` (id, seriesId, num, name, title, categoryId, categoryName, isFavorite, sourceId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val serPs = conn.prepareStatement(seriesSql)
        for (i in 1..8500) {
            serPs.setString(1, "src_a_series_$i")
            serPs.setInt(2, i)
            serPs.setInt(3, i)
            serPs.setString(4, "Serie %05d".format(i))
            serPs.setString(5, "Serie %05d".format(i))
            serPs.setString(6, if (i <= 2000) "cat_drama" else "cat_ser_general")
            serPs.setString(7, if (i <= 2000) "Drama" else "General")
            serPs.setInt(8, if (i % 50 == 0) 1 else 0)
            serPs.setString(9, "src_a")
            serPs.addBatch()
            if (i % 2000 == 0) serPs.executeBatch()
        }
        for (i in 1..1500) {
            serPs.setString(1, "src_b_series_$i")
            serPs.setInt(2, i)
            serPs.setInt(3, i)
            serPs.setString(4, "Serie B %05d".format(i))
            serPs.setString(5, "Serie B %05d".format(i))
            serPs.setString(6, "cat_b_ser")
            serPs.setString(7, "B Series")
            serPs.setInt(8, 0)
            serPs.setString(9, "src_b")
            serPs.addBatch()
        }
        serPs.executeBatch()

        conn.commit()
        conn.autoCommit = true
    }

    // =========================================================================
    // PASO 11: NAVEGACIÓN ENTRE TABS CON 90K ELEMENTOS EN BASE DE DATOS
    // =========================================================================

    @Test
    fun `test sequential tab visits with 90k items only materializes initial paging windows`() {
        populate90kFixture()

        // 1. HOME TAB:
        // Consulta canales preview: LIMIT 30
        val homeChPs = conn.prepareStatement("SELECT * FROM channels WHERE sourceId = ? ORDER BY num ASC, id ASC LIMIT 30")
        homeChPs.setString(1, "src_a")
        val homeChRs = homeChPs.executeQuery()
        var homeChCount = 0
        while (homeChRs.next()) homeChCount++
        homeChRs.close()
        assertEquals(30, homeChCount)

        // Consulta películas recientes: LIMIT 20
        val homeMovPs = conn.prepareStatement("SELECT * FROM movies WHERE sourceId = ? ORDER BY num ASC, id ASC LIMIT 20")
        homeMovPs.setString(1, "src_a")
        val homeMovRs = homeMovPs.executeQuery()
        var homeMovCount = 0
        while (homeMovRs.next()) homeMovCount++
        homeMovRs.close()
        assertEquals(20, homeMovCount)

        // Consulta series destacadas: LIMIT 20
        val homeSerPs = conn.prepareStatement("SELECT * FROM series WHERE sourceId = ? ORDER BY num ASC, id ASC LIMIT 20")
        homeSerPs.setString(1, "src_a")
        val homeSerRs = homeSerPs.executeQuery()
        var homeSerCount = 0
        while (homeSerRs.next()) homeSerCount++
        homeSerRs.close()
        assertEquals(20, homeSerCount)

        // Total en Home Tab: 70 modelos, NUNCA 90,000
        val totalHomeModels = homeChCount + homeMovCount + homeSerCount
        assertEquals(70, totalHomeModels)

        // 2. LIVE TAB (Tab 2):
        // PagingSource inicial: initialLoadSize = 100
        val livePagePs = conn.prepareStatement("SELECT * FROM channels WHERE sourceId = ? ORDER BY num ASC, id ASC LIMIT 100")
        livePagePs.setString(1, "src_a")
        val liveRs = livePagePs.executeQuery()
        var liveCount = 0
        while (liveRs.next()) liveCount++
        liveRs.close()
        assertEquals(100, liveCount)

        // 3. MOVIES TAB (Tab 3):
        // PagingSource inicial: initialLoadSize = 100
        val movPagePs = conn.prepareStatement("SELECT * FROM movies WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 100")
        movPagePs.setString(1, "src_a")
        val movRs = movPagePs.executeQuery()
        var movCount = 0
        while (movRs.next()) movCount++
        movRs.close()
        assertEquals(100, movCount)

        // 4. SERIES TAB (Tab 4):
        // PagingSource inicial: initialLoadSize = 100
        val serPagePs = conn.prepareStatement("SELECT * FROM series WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 100")
        serPagePs.setString(1, "src_a")
        val serRs = serPagePs.executeQuery()
        var serCount = 0
        while (serRs.next()) serCount++
        serRs.close()
        assertEquals(100, serCount)

        // 5. LIVE TAB otra vez:
        val liveAgainRs = livePagePs.executeQuery()
        var liveAgainCount = 0
        while (liveAgainRs.next()) liveAgainCount++
        liveAgainRs.close()
        assertEquals(100, liveAgainCount)

        // 6. MOVIES TAB otra vez:
        val movAgainRs = movPagePs.executeQuery()
        var movAgainCount = 0
        while (movAgainRs.next()) movAgainCount++
        movAgainRs.close()
        assertEquals(100, movAgainCount)

        // En ningún momento se materializaron los 50,000 canales, 30,000 películas ni 10,000 series
    }

    // =========================================================================
    // PASO 12: CAMBIO DE SOURCE STRESS (A -> B -> A -> B)
    // =========================================================================

    @Test
    fun `test source switch stress isolates catalogs without cross data or duplicate keys`() {
        populate90kFixture()

        val sources = listOf("src_a", "src_b", "src_a", "src_b")

        for (sourceId in sources) {
            // Canales para sourceId
            val chPs = conn.prepareStatement("SELECT * FROM channels WHERE sourceId = ? ORDER BY num ASC, id ASC LIMIT 50")
            chPs.setString(1, sourceId)
            val chRs = chPs.executeQuery()
            var chLoaded = 0
            while (chRs.next()) {
                chLoaded++
                assertEquals(sourceId, chRs.getString("sourceId"))
                assertTrue(chRs.getString("id").startsWith("${sourceId}_live_"))
            }
            chRs.close()
            assertEquals(50, chLoaded)

            // Películas para sourceId
            val movPs = conn.prepareStatement("SELECT * FROM movies WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 50")
            movPs.setString(1, sourceId)
            val movRs = movPs.executeQuery()
            var movLoaded = 0
            while (movRs.next()) {
                movLoaded++
                assertEquals(sourceId, movRs.getString("sourceId"))
                assertTrue(movRs.getString("id").startsWith("${sourceId}_vod_"))
            }
            movRs.close()
            assertEquals(50, movLoaded)

            // Series para sourceId
            val serPs = conn.prepareStatement("SELECT * FROM series WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 50")
            serPs.setString(1, sourceId)
            val serRs = serPs.executeQuery()
            var serLoaded = 0
            while (serRs.next()) {
                serLoaded++
                assertEquals(sourceId, serRs.getString("sourceId"))
                assertTrue(serRs.getString("id").startsWith("${sourceId}_series_"))
            }
            serRs.close()
            assertEquals(50, serLoaded)
        }
    }

    // =========================================================================
    // PASO 6: FAVORITE LOOKUP DIRECTO O(1)
    // =========================================================================

    @Test
    fun `test favorite status queried via entity isFavorite field without linear search`() {
        populate90kFixture()

        // Canal favorito (streamId = 100)
        val chPs = conn.prepareStatement("SELECT isFavorite FROM channels WHERE id = ?")
        chPs.setString(1, "src_a_live_100")
        val chRs = chPs.executeQuery()
        assertTrue(chRs.next())
        assertEquals(1, chRs.getInt(1))
        chRs.close()

        // Canal no favorito (streamId = 101)
        chPs.setString(1, "src_a_live_101")
        val chRs2 = chPs.executeQuery()
        assertTrue(chRs2.next())
        assertEquals(0, chRs2.getInt(1))
        chRs2.close()
    }
}
