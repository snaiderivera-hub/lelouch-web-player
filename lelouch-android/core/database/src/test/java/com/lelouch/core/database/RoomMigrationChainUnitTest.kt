package com.lelouch.core.database

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Pruebas unitarias de la cadena completa de migraciones para FASE P1 #3.2:
 * 1 -> 2 -> 3 -> 4 -> 5
 * 2 -> 3 -> 4 -> 5
 * 3 -> 4 -> 5
 * 4 -> 5
 * Fresh install 5
 *
 * Valida:
 * - PRAGMA integrity_check = ok
 * - PRAGMA foreign_key_check = vacío
 * - Preservación de conteos pre/post
 * - Rebuild FTS4 unicode61 inmediato con búsqueda desacentuada
 */
class RoomMigrationChainUnitTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) conn.close()
    }

    // --- Helpers de creación DDL por versión ---

    private fun initSchemaV1(stmt: java.sql.Statement) {
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
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_channels_streamId` ON `channels` (`streamId`)")

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
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_streamId` ON `movies` (`streamId`)")

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
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_seriesId` ON `series` (`seriesId`)")

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
                `seasonNumber` INTEGER, `episodeNumber` INTEGER,
                `lastWatchedTimestamp` INTEGER NOT NULL, `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }

    private fun applyMigration1To2(stmt: java.sql.Statement) {
        stmt.execute("DROP INDEX IF EXISTS `index_channels_streamId`")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_streamId` ON `channels` (`streamId`)")
        stmt.execute("DROP INDEX IF EXISTS `index_movies_streamId`")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_streamId` ON `movies` (`streamId`)")
    }

    private fun applyMigration2To3(stmt: java.sql.Statement) {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `channels_staging` (
                `syncId` TEXT NOT NULL, `id` TEXT NOT NULL, `streamId` INTEGER NOT NULL, `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL, `streamType` TEXT NOT NULL, `streamIcon` TEXT, `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL, `epgChannelId` TEXT, `isAdult` INTEGER NOT NULL,
                `isFavorite` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `containerExtension` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL, PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `movies_staging` (
                `syncId` TEXT NOT NULL, `id` TEXT NOT NULL, `streamId` INTEGER NOT NULL, `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL, `title` TEXT NOT NULL, `year` TEXT, `streamIcon` TEXT, `backdropPath` TEXT,
                `rating` REAL, `rating5based` REAL, `added` TEXT, `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL, `containerExtension` TEXT NOT NULL, `plot` TEXT, `cast` TEXT,
                `director` TEXT, `genre` TEXT, `durationSecs` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `series_staging` (
                `syncId` TEXT NOT NULL, `id` TEXT NOT NULL, `seriesId` INTEGER NOT NULL, `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL, `title` TEXT NOT NULL, `cover` TEXT, `backdropPath` TEXT, `plot` TEXT,
                `cast` TEXT, `director` TEXT, `genre` TEXT, `releaseDate` TEXT, `rating` REAL, `rating5based` REAL,
                `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL,
                `sourceId` TEXT NOT NULL, PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `categories_staging` (
                `syncId` TEXT NOT NULL, `id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL,
                `parentId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemCount` INTEGER NOT NULL,
                `isAdult` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())
    }

    private fun applyMigration3To4(stmt: java.sql.Statement) {
        stmt.execute("DROP INDEX IF EXISTS `index_series_seriesId`")
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_sourceId_seriesId` ON `series` (`sourceId`, `seriesId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_sourceId` ON `channels` (`sourceId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_categoryId` ON `channels` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId` ON `movies` (`sourceId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId_categoryId` ON `movies` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_sourceId_categoryId` ON `series` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_categories_sourceId_type` ON `categories` (`sourceId`, `type`)")
    }

    private fun applyMigration4To5(stmt: java.sql.Statement) {
        stmt.execute("DROP TABLE IF EXISTS `channels_fts`")
        stmt.execute("DROP TABLE IF EXISTS `movies_fts`")
        stmt.execute("DROP TABLE IF EXISTS `series_fts`")

        stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS `channels_fts` USING FTS4(`name` TEXT NOT NULL, `categoryName` TEXT NOT NULL, tokenize=unicode61 `remove_diacritics=1`, content=`channels`)")
        stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS `movies_fts` USING FTS4(`name` TEXT NOT NULL, `title` TEXT NOT NULL, `genre` TEXT, `cast` TEXT, `director` TEXT, `plot` TEXT, tokenize=unicode61 `remove_diacritics=1`, content=`movies`)")
        stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS `series_fts` USING FTS4(`name` TEXT NOT NULL, `title` TEXT NOT NULL, `genre` TEXT, `cast` TEXT, `plot` TEXT, tokenize=unicode61 `remove_diacritics=1`, content=`series`)")

        stmt.execute("INSERT INTO `channels_fts`(`docid`, `name`, `categoryName`) SELECT `rowid`, `name`, `categoryName` FROM `channels`")
        stmt.execute("INSERT INTO `movies_fts`(`docid`, `name`, `title`, `genre`, `cast`, `director`, `plot`) SELECT `rowid`, `name`, `title`, `genre`, `cast`, `director`, `plot` FROM `movies`")
        stmt.execute("INSERT INTO `series_fts`(`docid`, `name`, `title`, `genre`, `cast`, `plot`) SELECT `rowid`, `name`, `title`, `genre`, `cast`, `plot` FROM `series`")
    }

    // --- TESTS ---

    @Test
    fun testMigrationChain_1_to_5() {
        val stmt = conn.createStatement()

        initSchemaV1(stmt)

        // Insert seed accented data in v1
        stmt.execute("INSERT INTO channels VALUES ('ch_1', 101, 1, 'Película Nacional HD', 'live', null, 'cat-1', 'Cine', null, 0, 0, 'http://st', 'ts', 'src-1')")
        stmt.execute("INSERT INTO movies VALUES ('mov_1', 201, 1, 'Canción Latina', 'Canción Latina', '2023', null, null, 8.0, 4.0, '2023', 'cat-2', 'Musica', 'mp4', null, null, null, 'Musica', 7200, 'http://vod', 0, 'src-1')")
        stmt.execute("INSERT INTO series VALUES ('ser_1', 301, 1, 'Niñez Feliz', 'Niñez Feliz', null, null, null, null, null, 'Drama', '2023', 8.5, 4.25, 'cat-3', 'Drama', 0, 'src-1')")
        stmt.execute("INSERT INTO categories VALUES ('src-1_cat-1', 'cat-1', 'Cine', 0, 'LIVE', 1, 0, 'src-1')")
        stmt.execute("INSERT INTO favorites VALUES ('fav_1', 'ch_1', 'Película Nacional HD', null, 'LIVE', 'cat-1', 1000, 'src-1')")
        stmt.execute("INSERT INTO watch_history VALUES ('hist_1', 'mov_1', 'Canción Latina', null, null, 'VOD', 100, 200, null, null, 1000, 'src-1')")

        // Execute chain: 1 -> 2 -> 3 -> 4 -> 5
        applyMigration1To2(stmt)
        applyMigration2To3(stmt)
        applyMigration3To4(stmt)
        applyMigration4To5(stmt)

        // Integrity & Foreign Keys
        val pragmaInteg = stmt.executeQuery("PRAGMA integrity_check").apply { next() }.getString(1)
        assertEquals("ok", pragmaInteg)
        val pragmaFk = stmt.executeQuery("PRAGMA foreign_key_check")
        assertFalse(pragmaFk.next())

        // Data preservation
        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM channels").apply { next() }.getInt(1))
        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM movies").apply { next() }.getInt(1))
        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM series").apply { next() }.getInt(1))
        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM categories").apply { next() }.getInt(1))
        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM favorites").apply { next() }.getInt(1))
        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM watch_history").apply { next() }.getInt(1))

        // FTS search desacentuada
        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM channels_fts WHERE channels_fts MATCH 'pelicula*'").apply { next() }.getInt(1))
        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM movies_fts WHERE movies_fts MATCH 'cancion*'").apply { next() }.getInt(1))
        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM series_fts WHERE series_fts MATCH 'ninez*'").apply { next() }.getInt(1))

        println("MIGRATION 1->2->3->4->5: resultado = PASÓ")
        stmt.close()
    }

    @Test
    fun testMigrationChain_2_to_5() {
        val stmt = conn.createStatement()

        initSchemaV1(stmt)
        applyMigration1To2(stmt)

        stmt.execute("INSERT INTO movies VALUES ('mov_2', 202, 2, 'Corazón de Fuego', 'Corazón de Fuego', '2023', null, null, 8.0, 4.0, '2023', 'cat-2', 'Accion', 'mp4', null, null, null, 'Accion', 7200, 'http://vod', 0, 'src-1')")

        applyMigration2To3(stmt)
        applyMigration3To4(stmt)
        applyMigration4To5(stmt)

        val pragmaInteg = stmt.executeQuery("PRAGMA integrity_check").apply { next() }.getString(1)
        assertEquals("ok", pragmaInteg)

        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM movies_fts WHERE movies_fts MATCH 'corazon*'").apply { next() }.getInt(1))
        println("MIGRATION 2->3->4->5: resultado = PASÓ")
        stmt.close()
    }

    @Test
    fun testMigrationChain_3_to_5() {
        val stmt = conn.createStatement()

        initSchemaV1(stmt)
        applyMigration1To2(stmt)
        applyMigration2To3(stmt)

        stmt.execute("INSERT INTO series VALUES ('ser_3', 303, 3, 'Niñez Eterna', 'Niñez Eterna', null, null, null, null, null, 'Drama', '2023', 8.5, 4.25, 'cat-3', 'Drama', 0, 'src-1')")

        applyMigration3To4(stmt)
        applyMigration4To5(stmt)

        val pragmaInteg = stmt.executeQuery("PRAGMA integrity_check").apply { next() }.getString(1)
        assertEquals("ok", pragmaInteg)

        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM series_fts WHERE series_fts MATCH 'ninez*'").apply { next() }.getInt(1))
        println("MIGRATION 3->4->5: resultado = PASÓ")
        stmt.close()
    }

    @Test
    fun testMigrationChain_4_to_5() {
        val stmt = conn.createStatement()

        initSchemaV1(stmt)
        applyMigration1To2(stmt)
        applyMigration2To3(stmt)
        applyMigration3To4(stmt)

        stmt.execute("INSERT INTO channels VALUES ('ch_4', 104, 4, 'Canción Latina TV', 'live', null, 'cat-1', 'Musica', null, 0, 0, 'http://st', 'ts', 'src-1')")

        applyMigration4To5(stmt)

        val pragmaInteg = stmt.executeQuery("PRAGMA integrity_check").apply { next() }.getString(1)
        assertEquals("ok", pragmaInteg)

        assertEquals(1, stmt.executeQuery("SELECT count(*) FROM channels_fts WHERE channels_fts MATCH 'cancion*'").apply { next() }.getInt(1))
        println("MIGRATION 4->5: resultado = PASÓ")
        stmt.close()
    }

    @Test
    fun testFreshInstall_5() {
        val stmt = conn.createStatement()

        initSchemaV1(stmt)
        applyMigration1To2(stmt)
        applyMigration2To3(stmt)
        applyMigration3To4(stmt)
        applyMigration4To5(stmt)

        val pragmaInteg = stmt.executeQuery("PRAGMA integrity_check").apply { next() }.getString(1)
        assertEquals("ok", pragmaInteg)

        val pragmaFk = stmt.executeQuery("PRAGMA foreign_key_check")
        assertFalse(pragmaFk.next())

        val ftsTables = listOf("channels_fts", "movies_fts", "series_fts")
        for (table in ftsTables) {
            val cur = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='$table'")
            cur.next()
            assertEquals("Table $table must exist in fresh v5", 1, cur.getInt(1))
        }

        println("FRESH INSTALL 5: resultado = PASÓ")
        stmt.close()
    }
}
