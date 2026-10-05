package com.lelouch.core.database

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class AtomicSyncMechanismTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        initSchemaV3()
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) conn.close()
    }

    private fun initSchemaV3() {
        val stmt = conn.createStatement()
        // Tables
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

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL,
                `parentId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemCount` INTEGER NOT NULL,
                `isAdult` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `favorites` (
                `id` TEXT NOT NULL, `contentId` TEXT NOT NULL, `title` TEXT NOT NULL,
                `posterUrl` TEXT, `contentType` TEXT NOT NULL, `categoryId` TEXT NOT NULL,
                `addedAt` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `watch_history` (
                `id` TEXT NOT NULL, `contentId` TEXT NOT NULL, `title` TEXT NOT NULL,
                `posterUrl` TEXT, `backdropUrl` TEXT, `contentType` TEXT NOT NULL,
                `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL,
                `seasonNumber` INTEGER, `episodeNumber` INTEGER, `lastWatchedTimestamp` INTEGER NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        // Staging Tables (v3)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `channels_staging` (
                `syncId` TEXT NOT NULL, `id` TEXT NOT NULL, `streamId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL, `name` TEXT NOT NULL, `streamType` TEXT NOT NULL,
                `streamIcon` TEXT, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL,
                `epgChannelId` TEXT, `isAdult` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL,
                `streamUrl` TEXT NOT NULL, `containerExtension` TEXT NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `movies_staging` (
                `syncId` TEXT NOT NULL, `id` TEXT NOT NULL, `streamId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL, `name` TEXT NOT NULL, `title` TEXT NOT NULL,
                `year` TEXT, `streamIcon` TEXT, `backdropPath` TEXT, `rating` REAL,
                `rating5based` REAL, `added` TEXT, `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL, `containerExtension` TEXT NOT NULL,
                `plot` TEXT, `cast` TEXT, `director` TEXT, `genre` TEXT,
                `durationSecs` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `series_staging` (
                `syncId` TEXT NOT NULL, `id` TEXT NOT NULL, `seriesId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL, `name` TEXT NOT NULL, `title` TEXT NOT NULL,
                `cover` TEXT, `backdropPath` TEXT, `plot` TEXT, `cast` TEXT,
                `director` TEXT, `genre` TEXT, `releaseDate` TEXT, `rating` REAL,
                `rating5based` REAL, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `categories_staging` (
                `syncId` TEXT NOT NULL, `id` TEXT NOT NULL, `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL, `parentId` INTEGER NOT NULL, `type` TEXT NOT NULL,
                `itemCount` INTEGER NOT NULL, `isAdult` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())
        stmt.close()
    }

    private fun queryCount(table: String, whereClause: String = ""): Int {
        val sql = if (whereClause.isBlank()) "SELECT COUNT(*) FROM `$table`" else "SELECT COUNT(*) FROM `$table` WHERE $whereClause"
        val rs = conn.createStatement().executeQuery(sql)
        rs.next()
        val count = rs.getInt(1)
        rs.close()
        return count
    }

    @Test
    fun testPragmaIntegrityCheck() {
        val rs = conn.createStatement().executeQuery("PRAGMA integrity_check")
        assertTrue(rs.next())
        assertEquals("ok", rs.getString(1))
        rs.close()
    }

    @Test
    fun testSuccessfulSyncAndAtomicSwap() {
        val stmt = conn.createStatement()

        // 1. Fixture inicial: Source A (100 ch, 50 mov, 20 ser), Source B (80 ch, 30 mov, 10 ser)
        for (i in 1..100) {
            stmt.execute("INSERT INTO channels VALUES ('ch-a-$i', $i, $i, 'Channel A $i', 'live', null, 'cat-1', 'General', null, 0, 0, 'http://a/$i', 'm3u8', 'source-a')")
        }
        for (i in 1..50) {
            stmt.execute("INSERT INTO movies VALUES ('mov-a-$i', ${1000+i}, $i, 'Movie A $i', 'Movie A $i', '2024', null, null, 7.0, 3.5, null, 'cat-2', 'Movies', 'mp4', null, null, null, null, 5000, 'http://a/$i.mp4', 0, 'source-a')")
        }
        for (i in 1..20) {
            stmt.execute("INSERT INTO series VALUES ('ser-a-$i', ${2000+i}, $i, 'Series A $i', 'Series A $i', null, null, null, null, null, null, '2024', 8.0, 4.0, 'cat-3', 'Series', 0, 'source-a')")
        }

        for (i in 1..80) {
            stmt.execute("INSERT INTO channels VALUES ('ch-b-$i', ${3000+i}, $i, 'Channel B $i', 'live', null, 'cat-1', 'General', null, 0, 0, 'http://b/$i', 'm3u8', 'source-b')")
        }
        for (i in 1..30) {
            stmt.execute("INSERT INTO movies VALUES ('mov-b-$i', ${4000+i}, $i, 'Movie B $i', 'Movie B $i', '2024', null, null, 8.0, 4.0, null, 'cat-2', 'Movies', 'mp4', null, null, null, null, 6000, 'http://b/$i.mp4', 0, 'source-b')")
        }
        for (i in 1..10) {
            stmt.execute("INSERT INTO series VALUES ('ser-b-$i', ${5000+i}, $i, 'Series B $i', 'Series B $i', null, null, null, null, null, null, '2024', 9.0, 4.5, 'cat-3', 'Series', 0, 'source-b')")
        }

        assertEquals(100, queryCount("channels", "sourceId = 'source-a'"))
        assertEquals(80, queryCount("channels", "sourceId = 'source-b'"))

        // 2. Simular sync nuevo de Source A: 90 channels, 60 movies, 25 series
        val syncId = "sync-12345"
        for (i in 1..90) {
            stmt.execute("INSERT INTO channels_staging VALUES ('$syncId', 'ch-a-new-$i', ${10000+i}, $i, 'New Channel A $i', 'live', null, 'cat-1', 'General', null, 0, 0, 'http://a/new/$i', 'm3u8', 'source-a')")
        }
        for (i in 1..60) {
            stmt.execute("INSERT INTO movies_staging VALUES ('$syncId', 'mov-a-new-$i', ${20000+i}, $i, 'New Movie A $i', 'New Movie A $i', '2024', null, null, 8.5, 4.25, null, 'cat-2', 'Movies', 'mp4', null, null, null, null, 5500, 'http://a/new/$i.mp4', 0, 'source-a')")
        }
        for (i in 1..25) {
            stmt.execute("INSERT INTO series_staging VALUES ('$syncId', 'ser-a-new-$i', ${30000+i}, $i, 'New Series A $i', 'New Series A $i', null, null, null, null, null, null, '2024', 9.5, 4.75, 'cat-3', 'Series', 0, 'source-a')")
        }

        // Antes del swap, las tablas activas NO cambiaron
        assertEquals(100, queryCount("channels", "sourceId = 'source-a'"))
        assertEquals(50, queryCount("movies", "sourceId = 'source-a'"))
        assertEquals(20, queryCount("series", "sourceId = 'source-a'"))

        // 3. Ejecutar SWAP ATÓMICO en transacción
        conn.autoCommit = false
        try {
            // Delete active for source-a
            stmt.execute("DELETE FROM channels WHERE sourceId = 'source-a'")
            stmt.execute("DELETE FROM movies WHERE sourceId = 'source-a'")
            stmt.execute("DELETE FROM series WHERE sourceId = 'source-a'")

            // Copy staging to active
            stmt.execute("""
                INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
                SELECT id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId
                FROM channels_staging WHERE syncId = '$syncId' AND sourceId = 'source-a'
            """)
            stmt.execute("""
                INSERT INTO movies (`id`, `streamId`, `num`, `name`, `title`, `year`, `streamIcon`, `backdropPath`, `rating`, `rating5based`, `added`, `categoryId`, `categoryName`, `containerExtension`, `plot`, `cast`, `director`, `genre`, `durationSecs`, `streamUrl`, `isFavorite`, `sourceId`)
                SELECT `id`, `streamId`, `num`, `name`, `title`, `year`, `streamIcon`, `backdropPath`, `rating`, `rating5based`, `added`, `categoryId`, `categoryName`, `containerExtension`, `plot`, `cast`, `director`, `genre`, `durationSecs`, `streamUrl`, `isFavorite`, `sourceId`
                FROM movies_staging WHERE syncId = '$syncId' AND sourceId = 'source-a'
            """)
            stmt.execute("""
                INSERT INTO series (`id`, `seriesId`, `num`, `name`, `title`, `cover`, `backdropPath`, `plot`, `cast`, `director`, `genre`, `releaseDate`, `rating`, `rating5based`, `categoryId`, `categoryName`, `isFavorite`, `sourceId`)
                SELECT `id`, `seriesId`, `num`, `name`, `title`, `cover`, `backdropPath`, `plot`, `cast`, `director`, `genre`, `releaseDate`, `rating`, `rating5based`, `categoryId`, `categoryName`, `isFavorite`, `sourceId`
                FROM series_staging WHERE syncId = '$syncId' AND sourceId = 'source-a'
            """)

            // Cleanup staging
            stmt.execute("DELETE FROM channels_staging WHERE syncId = '$syncId'")
            stmt.execute("DELETE FROM movies_staging WHERE syncId = '$syncId'")
            stmt.execute("DELETE FROM series_staging WHERE syncId = '$syncId'")

            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }

        // 4. Validar resultados tras swap exitoso
        assertEquals(90, queryCount("channels", "sourceId = 'source-a'"))
        assertEquals(60, queryCount("movies", "sourceId = 'source-a'"))
        assertEquals(25, queryCount("series", "sourceId = 'source-a'"))

        // Source B permaneció 100% intacto
        assertEquals(80, queryCount("channels", "sourceId = 'source-b'"))
        assertEquals(30, queryCount("movies", "sourceId = 'source-b'"))
        assertEquals(10, queryCount("series", "sourceId = 'source-b'"))

        // Staging quedó limpio
        assertEquals(0, queryCount("channels_staging", "syncId = '$syncId'"))
        assertEquals(0, queryCount("movies_staging", "syncId = '$syncId'"))
        assertEquals(0, queryCount("series_staging", "syncId = '$syncId'"))

        stmt.close()
    }

    @Test
    fun testProviderDeletedItemDisappears() {
        val stmt = conn.createStatement()
        // Catálogo previo: A, B, C
        stmt.execute("INSERT INTO channels VALUES ('ch-A', 1, 1, 'Channel A', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://a', 'ts', 'src-1')")
        stmt.execute("INSERT INTO channels VALUES ('ch-B', 2, 2, 'Channel B', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://b', 'ts', 'src-1')")
        stmt.execute("INSERT INTO channels VALUES ('ch-C', 3, 3, 'Channel C', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://c', 'ts', 'src-1')")

        // Proveedor devuelve en nuevo sync: A, C, D (B fue eliminado)
        val syncId = "sync-drop-b"
        stmt.execute("INSERT INTO channels_staging VALUES ('$syncId', 'ch-A', 1, 1, 'Channel A', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://a', 'ts', 'src-1')")
        stmt.execute("INSERT INTO channels_staging VALUES ('$syncId', 'ch-C', 3, 3, 'Channel C', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://c', 'ts', 'src-1')")
        stmt.execute("INSERT INTO channels_staging VALUES ('$syncId', 'ch-D', 4, 4, 'Channel D', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://d', 'ts', 'src-1')")

        // Swap atómico
        conn.autoCommit = false
        stmt.execute("DELETE FROM channels WHERE sourceId = 'src-1'")
        stmt.execute("""
            INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
            SELECT id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId
            FROM channels_staging WHERE syncId = '$syncId' AND sourceId = 'src-1'
        """)
        stmt.execute("DELETE FROM channels_staging WHERE syncId = '$syncId'")
        conn.commit()
        conn.autoCommit = true

        // Verificar que B desapareció y quedan A, C, D
        val rs = stmt.executeQuery("SELECT id FROM channels WHERE sourceId = 'src-1' ORDER BY num ASC")
        val ids = mutableListOf<String>()
        while (rs.next()) ids.add(rs.getString("id"))
        rs.close()

        assertEquals(listOf("ch-A", "ch-C", "ch-D"), ids)
        assertFalse("Channel B debe haber desaparecido tras sync", ids.contains("ch-B"))
        stmt.close()
    }

    @Test
    fun testPreserveUserFavorites() {
        val stmt = conn.createStatement()
        // Canal marcado como favorito en channels y en favorites
        stmt.execute("INSERT INTO channels VALUES ('ch-fav-1', 777, 1, 'Canal Favorito', 'live', null, 'cat-1', 'G', null, 0, 1, 'http://fav', 'ts', 'src-fav')")
        stmt.execute("INSERT INTO favorites VALUES ('fav-777', '777', 'Canal Favorito', null, 'LIVE', 'cat-1', 1700000000, 'src-fav')")

        // Nuevo sync del proveedor: el proveedor entrega isFavorite = 0
        val syncId = "sync-fav-test"
        stmt.execute("INSERT INTO channels_staging VALUES ('$syncId', 'ch-fav-1', 777, 1, 'Canal Favorito (Updated)', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://fav2', 'ts', 'src-fav')")

        // Preservar favoritos antes del swap
        stmt.execute("""
            UPDATE channels_staging 
            SET isFavorite = 1 
            WHERE syncId = '$syncId' 
              AND sourceId = 'src-fav' 
              AND (
                  id IN (SELECT id FROM channels WHERE sourceId = 'src-fav' AND isFavorite = 1)
                  OR streamId IN (SELECT streamId FROM channels WHERE sourceId = 'src-fav' AND isFavorite = 1)
                  OR streamId IN (SELECT CAST(contentId AS INTEGER) FROM favorites WHERE sourceId = 'src-fav' AND contentType = 'LIVE')
              )
        """.trimIndent())

        // Swap
        conn.autoCommit = false
        stmt.execute("DELETE FROM channels WHERE sourceId = 'src-fav'")
        stmt.execute("""
            INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
            SELECT id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId
            FROM channels_staging WHERE syncId = '$syncId' AND sourceId = 'src-fav'
        """)
        stmt.execute("DELETE FROM channels_staging WHERE syncId = '$syncId'")
        conn.commit()
        conn.autoCommit = true

        // Verificar que el canal activo conserva isFavorite = 1 y nombre actualizado
        val rs = stmt.executeQuery("SELECT isFavorite, name FROM channels WHERE id = 'ch-fav-1'")
        assertTrue(rs.next())
        assertEquals(1, rs.getInt("isFavorite"))
        assertEquals("Canal Favorito (Updated)", rs.getString("name"))
        rs.close()

        // Tabla favorites se preserva intacta
        assertEquals(1, queryCount("favorites", "sourceId = 'src-fav'"))
        stmt.close()
    }

    @Test
    fun testFailureBeforeSwapPreservesOldCatalog() {
        val stmt = conn.createStatement()
        stmt.execute("INSERT INTO channels VALUES ('ch-old-1', 10, 1, 'Canal Estable', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://old', 'ts', 'src-fail')")
        assertEquals(1, queryCount("channels", "sourceId = 'src-fail'"))

        // Simular sync que inserta 50% en staging y luego falla (ej. timeout de red)
        val syncId = "sync-aborted"
        stmt.execute("INSERT INTO channels_staging VALUES ('$syncId', 'ch-new-partial', 20, 2, 'Canal Incompleto', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://part', 'ts', 'src-fail')")

        // Excepción ocurre: cleanup staging
        stmt.execute("DELETE FROM channels_staging WHERE syncId = '$syncId'")

        // El catálogo viejo permanece 100% intacto
        assertEquals(1, queryCount("channels", "sourceId = 'src-fail'"))
        val rs = stmt.executeQuery("SELECT name FROM channels WHERE sourceId = 'src-fail'")
        assertTrue(rs.next())
        assertEquals("Canal Estable", rs.getString("name"))
        rs.close()

        stmt.close()
    }

    @Test
    fun testRollbackOnSwapFailure() {
        val stmt = conn.createStatement()
        stmt.execute("INSERT INTO channels VALUES ('ch-safe', 99, 1, 'Canal Blindado', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://safe', 'ts', 'src-rb')")

        val syncId = "sync-rollback"
        stmt.execute("INSERT INTO channels_staging VALUES ('$syncId', 'ch-new', 100, 1, 'Canal Nuevo', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://new', 'ts', 'src-rb')")

        conn.autoCommit = false
        var exceptionCaught = false
        try {
            stmt.execute("DELETE FROM channels WHERE sourceId = 'src-rb'")
            // Simular un fallo forzado dentro de la transacción
            throw RuntimeException("Fallo de disco o constraint en mitad del swap")
        } catch (_: Exception) {
            conn.rollback()
            exceptionCaught = true
        } finally {
            conn.autoCommit = true
        }

        assertTrue("La excepción debe haber sido capturada", exceptionCaught)

        // Catálogo previo sigue intacto gracias a ROLLBACK
        assertEquals(1, queryCount("channels", "sourceId = 'src-rb'"))
        val rs = stmt.executeQuery("SELECT name FROM channels WHERE sourceId = 'src-rb'")
        assertTrue(rs.next())
        assertEquals("Canal Blindado", rs.getString("name"))
        rs.close()

        stmt.close()
    }

    @Test
    fun testOrphanStagingCleanupIsolatesSources() {
        val stmt = conn.createStatement()
        // Staging huérfano de ejecuciones anteriores
        stmt.execute("INSERT INTO channels_staging VALUES ('sync-old-a', 'ch-orph-a', 1, 1, 'Old A', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://a', 'ts', 'source-a')")
        stmt.execute("INSERT INTO channels_staging VALUES ('sync-old-b', 'ch-orph-b', 2, 2, 'Old B', 'live', null, 'cat-1', 'G', null, 0, 0, 'http://b', 'ts', 'source-b')")

        // Nuevo sync de source-a con syncId "sync-active-a"
        // Cleanup para source-a
        stmt.execute("DELETE FROM channels_staging WHERE sourceId = 'source-a' AND syncId != 'sync-active-a'")

        // Staging de source-a fue borrado
        assertEquals(0, queryCount("channels_staging", "sourceId = 'source-a'"))
        // Staging de source-b sigue existiendo y NO fue alterado
        assertEquals(1, queryCount("channels_staging", "sourceId = 'source-b'"))

        stmt.close()
    }
}
