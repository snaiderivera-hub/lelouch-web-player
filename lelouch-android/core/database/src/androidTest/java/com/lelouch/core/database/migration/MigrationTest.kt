package com.lelouch.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lelouch.core.database.LelouchDatabase
import com.lelouch.core.database.migrations.MIGRATION_1_2
import com.lelouch.core.database.migrations.MIGRATION_2_3
import com.lelouch.core.database.migrations.MIGRATION_3_4
import com.lelouch.core.database.migrations.MIGRATION_4_5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LelouchDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesAllDataAndConvertsIndices() {
        // 1. Crear base de datos en versión 1
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
                VALUES ('ch-1', 101, 1, 'Canal Test 1', 'live', 'http://logo1.png', 'cat-1', 'Noticias', 'epg-1', 0, 1, 'http://stream1.m3u8', 'ts', 'source-1')
            """.trimIndent())

            execSQL("""
                INSERT INTO movies (id, streamId, num, name, title, year, streamIcon, backdropPath, rating, rating5based, added, categoryId, categoryName, containerExtension, plot, cast, director, genre, durationSecs, streamUrl, isFavorite, sourceId)
                VALUES ('mov-1', 201, 1, 'Pelicula Test 1', 'Pelicula Test 1', '2024', 'http://poster1.png', 'http://back1.png', 8.5, 4.25, '1700000000', 'cat-2', 'Accion', 'mp4', 'Plot test', 'Actor test', 'Dir test', 'Accion', 7200, 'http://movie1.mp4', 0, 'source-1')
            """.trimIndent())

            execSQL("""
                INSERT INTO series (id, seriesId, num, name, title, cover, backdropPath, plot, cast, director, genre, releaseDate, rating, rating5based, categoryId, categoryName, isFavorite, sourceId)
                VALUES ('ser-1', 301, 1, 'Serie Test 1', 'Serie Test 1', 'http://cover1.png', 'http://back1.png', 'Plot serie', 'Cast serie', 'Dir serie', 'Drama', '2023', 9.0, 4.5, 'cat-3', 'Drama', 1, 'source-1')
            """.trimIndent())

            execSQL("""
                INSERT INTO categories (id, categoryId, categoryName, parentId, type, itemCount, isAdult, sourceId)
                VALUES ('source-1-LIVE-cat-1', 'cat-1', 'Noticias', 0, 'LIVE', 1, 0, 'source-1')
            """.trimIndent())

            execSQL("""
                INSERT INTO favorites (id, contentId, title, posterUrl, contentType, categoryId, addedAt, sourceId)
                VALUES ('source-1-LIVE-ch-1', 'ch-1', 'Canal Test 1', 'http://logo1.png', 'LIVE', 'cat-1', 1700000000000, 'source-1')
            """.trimIndent())

            execSQL("""
                INSERT INTO watch_history (id, contentId, title, posterUrl, backdropUrl, contentType, positionMs, durationMs, seasonNumber, episodeNumber, lastWatchedTimestamp, sourceId)
                VALUES ('source-1-mov-1', 'mov-1', 'Pelicula Test 1', 'http://poster1.png', 'http://back1.png', 'VOD', 1500000, 7200000, null, null, 1700000000000, 'source-1')
            """.trimIndent())

            close()
        }

        // 2. Ejecutar migración v1 -> v2 y validar schema Room automáticamente
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // 3. Validar integridad de SQLite
        val integrityCursor = db.query("PRAGMA integrity_check")
        assertTrue(integrityCursor.moveToFirst())
        assertEquals("ok", integrityCursor.getString(0))
        integrityCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        // 4. Validar preservación exacta de todos los datos
        val chCursor = db.query("SELECT COUNT(*) FROM channels")
        assertTrue(chCursor.moveToFirst())
        assertEquals(1, chCursor.getInt(0))
        chCursor.close()

        val movCursor = db.query("SELECT COUNT(*) FROM movies")
        assertTrue(movCursor.moveToFirst())
        assertEquals(1, movCursor.getInt(0))
        movCursor.close()

        val serCursor = db.query("SELECT COUNT(*) FROM series")
        assertTrue(serCursor.moveToFirst())
        assertEquals(1, serCursor.getInt(0))
        serCursor.close()

        val catCursor = db.query("SELECT COUNT(*) FROM categories")
        assertTrue(catCursor.moveToFirst())
        assertEquals(1, catCursor.getInt(0))
        catCursor.close()

        val favCursor = db.query("SELECT COUNT(*) FROM favorites")
        assertTrue(favCursor.moveToFirst())
        assertEquals(1, favCursor.getInt(0))
        favCursor.close()

        val histCursor = db.query("SELECT COUNT(*) FROM watch_history")
        assertTrue(histCursor.moveToFirst())
        assertEquals(1, histCursor.getInt(0))
        histCursor.close()

        // 5. Validar que la restricción UNIQUE fue eliminada permitiendo duplicados legítimos de streamId
        db.execSQL("""
            INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
            VALUES ('ch-2', 101, 2, 'Canal Test 1 (Backup)', 'live', 'http://logo1.png', 'cat-2', 'General', 'epg-1', 0, 0, 'http://stream2.m3u8', 'ts', 'source-2')
        """.trimIndent())

        val chCount2 = db.query("SELECT COUNT(*) FROM channels")
        assertTrue(chCount2.moveToFirst())
        assertEquals(2, chCount2.getInt(0))
        chCount2.close()

        db.close()
    }

    @Test
    fun migrate2To3_createsStagingTablesAndPreservesAllData() {
        // 1. Crear base de datos en versión 2
        var db = helper.createDatabase(TEST_DB, 2).apply {
            execSQL("""
                INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
                VALUES ('ch-100', 1001, 1, 'Canal v2', 'live', 'http://logo.png', 'cat-1', 'General', 'epg-1', 0, 1, 'http://stream.m3u8', 'ts', 'source-a')
            """.trimIndent())

            execSQL("""
                INSERT INTO movies (id, streamId, num, name, title, year, streamIcon, backdropPath, rating, rating5based, added, categoryId, categoryName, containerExtension, plot, cast, director, genre, durationSecs, streamUrl, isFavorite, sourceId)
                VALUES ('mov-100', 2001, 1, 'Pelicula v2', 'Pelicula v2', '2024', null, null, 7.0, 3.5, '1700000000', 'cat-2', 'Cine', 'mp4', null, null, null, null, 5400, 'http://film.mp4', 1, 'source-a')
            """.trimIndent())

            execSQL("""
                INSERT INTO series (id, seriesId, num, name, title, cover, backdropPath, plot, cast, director, genre, releaseDate, rating, rating5based, categoryId, categoryName, isFavorite, sourceId)
                VALUES ('ser-100', 3001, 1, 'Serie v2', 'Serie v2', null, null, null, null, null, null, '2024', 8.0, 4.0, 'cat-3', 'Series', 0, 'source-a')
            """.trimIndent())

            execSQL("""
                INSERT INTO categories (id, categoryId, categoryName, parentId, type, itemCount, isAdult, sourceId)
                VALUES ('source-a-LIVE-cat-1', 'cat-1', 'General', 0, 'LIVE', 1, 0, 'source-a')
            """.trimIndent())

            execSQL("""
                INSERT INTO favorites (id, contentId, title, posterUrl, contentType, categoryId, addedAt, sourceId)
                VALUES ('source-a-LIVE-ch-100', 'ch-100', 'Canal v2', 'http://logo.png', 'LIVE', 'cat-1', 1700000000000, 'source-a')
            """.trimIndent())

            execSQL("""
                INSERT INTO watch_history (id, contentId, title, posterUrl, backdropUrl, contentType, positionMs, durationMs, seasonNumber, episodeNumber, lastWatchedTimestamp, sourceId)
                VALUES ('source-a-mov-100', 'mov-100', 'Pelicula v2', null, null, 'VOD', 120000, 5400000, null, null, 1700000000000, 'source-a')
            """.trimIndent())

            close()
        }

        // 2. Ejecutar migración v2 -> v3
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // 3. Validar integridad de SQLite
        val integrityCursor = db.query("PRAGMA integrity_check")
        assertTrue(integrityCursor.moveToFirst())
        assertEquals("ok", integrityCursor.getString(0))
        integrityCursor.close()

        // 4. Validar preservación de datos existentes
        val chCursor = db.query("SELECT COUNT(*) FROM channels WHERE sourceId = 'source-a'")
        assertTrue(chCursor.moveToFirst())
        assertEquals(1, chCursor.getInt(0))
        chCursor.close()

        val movCursor = db.query("SELECT COUNT(*) FROM movies WHERE sourceId = 'source-a'")
        assertTrue(movCursor.moveToFirst())
        assertEquals(1, movCursor.getInt(0))
        movCursor.close()

        val serCursor = db.query("SELECT COUNT(*) FROM series WHERE sourceId = 'source-a'")
        assertTrue(serCursor.moveToFirst())
        assertEquals(1, serCursor.getInt(0))
        serCursor.close()

        val favCursor = db.query("SELECT COUNT(*) FROM favorites WHERE sourceId = 'source-a'")
        assertTrue(favCursor.moveToFirst())
        assertEquals(1, favCursor.getInt(0))
        favCursor.close()

        val histCursor = db.query("SELECT COUNT(*) FROM watch_history WHERE sourceId = 'source-a'")
        assertTrue(histCursor.moveToFirst())
        assertEquals(1, histCursor.getInt(0))
        histCursor.close()

        // 5. Validar que las 4 tablas de staging existen y aceptan inserciones
        db.execSQL("""
            INSERT INTO channels_staging (syncId, id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
            VALUES ('sync-test', 'ch-stg-1', 999, 1, 'Canal Staging', 'live', null, 'cat-stg', 'Test', null, 0, 0, 'http://stg.m3u8', 'ts', 'source-a')
        """.trimIndent())

        val stgCursor = db.query("SELECT COUNT(*) FROM channels_staging WHERE syncId = 'sync-test'")
        assertTrue(stgCursor.moveToFirst())
        assertEquals(1, stgCursor.getInt(0))
        stgCursor.close()

        db.close()
    }

    @Test
    fun migrate3To4_replacesSeriesUniqueIndexAndAddsQueryIndexes() {
        // 1. Crear base de datos en versión 3
        var db = helper.createDatabase(TEST_DB, 3).apply {
            execSQL("""
                INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
                VALUES ('live_10', 10, 1, 'Canal A', 'live', null, 'cat_1', 'Cat A', null, 0, 0, 'http://ch10.m3u8', 'ts', 'source_a')
            """.trimIndent())

            execSQL("""
                INSERT INTO movies (id, streamId, num, name, title, year, streamIcon, backdropPath, rating, rating5based, added, categoryId, categoryName, containerExtension, plot, cast, director, genre, durationSecs, streamUrl, isFavorite, sourceId)
                VALUES ('vod_20', 20, 1, 'Movie A', 'Movie A', '2024', null, null, 8.0, 4.0, null, 'cat_2', 'Cat B', 'mp4', null, null, null, null, 7200, 'http://m20.mp4', 0, 'source_a')
            """.trimIndent())

            execSQL("""
                INSERT INTO series (id, seriesId, num, name, title, cover, backdropPath, plot, cast, director, genre, releaseDate, rating, rating5based, categoryId, categoryName, isFavorite, sourceId)
                VALUES ('series_30', 30, 1, 'Serie A', 'Serie A', null, null, null, null, null, null, '2024', 9.0, 4.5, 'cat_3', 'Cat C', 0, 'source_a')
            """.trimIndent())

            execSQL("""
                INSERT INTO categories (id, categoryId, categoryName, parentId, type, itemCount, isAdult, sourceId)
                VALUES ('source_a-LIVE-cat_1', 'cat_1', 'Cat A', 0, 'LIVE', 1, 0, 'source_a')
            """.trimIndent())

            close()
        }

        // 2. Ejecutar migración v3 -> v4
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        // 3. Validar integridad SQLite
        val integrityCursor = db.query("PRAGMA integrity_check")
        assertTrue(integrityCursor.moveToFirst())
        assertEquals("ok", integrityCursor.getString(0))
        integrityCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        // 4. Validar normalización de IDs con sourceId
        val chCursor = db.query("SELECT id FROM channels WHERE streamId = 10")
        assertTrue(chCursor.moveToFirst())
        assertEquals("source_a_live_10", chCursor.getString(0))
        chCursor.close()

        val movCursor = db.query("SELECT id FROM movies WHERE streamId = 20")
        assertTrue(movCursor.moveToFirst())
        assertEquals("source_a_vod_20", movCursor.getString(0))
        movCursor.close()

        val serCursor = db.query("SELECT id FROM series WHERE seriesId = 30")
        assertTrue(serCursor.moveToFirst())
        assertEquals("source_a_series_30", serCursor.getString(0))
        serCursor.close()

        // 5. Validar que Source B puede insertar el MISMO seriesId = 30 sin colisión UNIQUE
        db.execSQL("""
            INSERT INTO series (id, seriesId, num, name, title, cover, backdropPath, plot, cast, director, genre, releaseDate, rating, rating5based, categoryId, categoryName, isFavorite, sourceId)
            VALUES ('source_b_series_30', 30, 1, 'Serie B', 'Serie B', null, null, null, null, null, null, '2024', 7.5, 3.75, 'cat_3', 'Cat C', 0, 'source_b')
        """.trimIndent())

        val totalSeries = db.query("SELECT COUNT(*) FROM series WHERE seriesId = 30")
        assertTrue(totalSeries.moveToFirst())
        assertEquals(2, totalSeries.getInt(0))
        totalSeries.close()

        db.close()
    }

    @Test
    fun migrate1To2To3To4_fullChain() {
        // 1. Crear v1
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
                VALUES ('live_555', 555, 1, 'Canal Chain', 'live', null, 'cat-1', 'News', null, 0, 1, 'http://chain.m3u8', 'ts', 'src-1')
            """.trimIndent())
            close()
        }

        // 2. Migrar de v1 hasta v4 pasando por MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        // 3. Validar integridad
        val integrityCursor = db.query("PRAGMA integrity_check")
        assertTrue(integrityCursor.moveToFirst())
        assertEquals("ok", integrityCursor.getString(0))
        integrityCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        val countCursor = db.query("SELECT COUNT(*) FROM channels WHERE streamId = 555 AND sourceId = 'src-1'")
        assertTrue(countCursor.moveToFirst())
        assertEquals(1, countCursor.getInt(0))
        countCursor.close()

        db.close()
    }

    @Test
    fun migrate2To3To4_chain() {
        var db = helper.createDatabase(TEST_DB, 2).apply {
            execSQL("""
                INSERT INTO series (id, seriesId, num, name, title, cover, backdropPath, plot, cast, director, genre, releaseDate, rating, rating5based, categoryId, categoryName, isFavorite, sourceId)
                VALUES ('ser_v2', 777, 1, 'Serie v2 Chain', 'Serie v2 Chain', null, null, null, null, null, null, '2024', 8.5, 4.25, 'cat-1', 'Drama', 0, 'src-2')
            """.trimIndent())
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_2_3, MIGRATION_3_4)

        val integrityCursor = db.query("PRAGMA integrity_check")
        assertTrue(integrityCursor.moveToFirst())
        assertEquals("ok", integrityCursor.getString(0))
        integrityCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        val serCursor = db.query("SELECT COUNT(*) FROM series WHERE seriesId = 777")
        assertTrue(serCursor.moveToFirst())
        assertEquals(1, serCursor.getInt(0))
        serCursor.close()

        db.close()
    }

    @Test
    fun freshInstall_version4() {
        // Crear directamente v4 (instalación limpia)
        val db = helper.createDatabase(TEST_DB, 4)

        val integrityCursor = db.query("PRAGMA integrity_check")
        assertTrue(integrityCursor.moveToFirst())
        assertEquals("ok", integrityCursor.getString(0))
        integrityCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        // Validar que existen las tablas de staging
        val tables = listOf("channels_staging", "movies_staging", "series_staging", "categories_staging")
        for (table in tables) {
            val cur = db.query("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='$table'")
            assertTrue(cur.moveToFirst())
            assertEquals(1, cur.getInt(0))
            cur.close()
        }

        db.close()
    }

    @Test
    fun migrate4To5_rebuildsFtsWithUnicode61() {
        var db = helper.createDatabase(TEST_DB, 4).apply {
            execSQL("""
                INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
                VALUES ('src-1_live_1', 101, 1, 'Película Nacional HD', 'live', null, 'cat-1', 'Cine', null, 0, 0, 'http://stream', 'ts', 'src-1')
            """.trimIndent())
            execSQL("""
                INSERT INTO movies (id, streamId, num, name, title, year, streamIcon, backdropPath, rating, rating5based, added, categoryId, categoryName, containerExtension, plot, cast, director, genre, durationSecs, streamUrl, isFavorite, sourceId)
                VALUES ('src-1_vod_1', 201, 1, 'Canción Latina', 'Canción Latina', '2023', null, null, 8.0, 4.0, '2023', 'cat-2', 'Musica', 'mp4', null, null, null, 'Musica', 7200, 'http://vod', 0, 'src-1')
            """.trimIndent())
            execSQL("""
                INSERT INTO series (id, seriesId, num, name, title, cover, backdropPath, plot, cast, director, genre, releaseDate, rating, rating5based, categoryId, categoryName, isFavorite, sourceId)
                VALUES ('src-1_ser_1', 301, 1, 'Niñez Feliz', 'Niñez Feliz', null, null, null, null, null, 'Drama', '2023', 8.5, 4.25, 'cat-3', 'Drama', 0, 'src-1')
            """.trimIndent())
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        val integrityCursor = db.query("PRAGMA integrity_check")
        assertTrue(integrityCursor.moveToFirst())
        assertEquals("ok", integrityCursor.getString(0))
        integrityCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        // Validar que los registros históricos quedaron indexados de inmediato en FTS4 unicode61
        val chFtsCursor = db.query("SELECT COUNT(*) FROM channels_fts WHERE channels_fts MATCH 'pelicula*'")
        assertTrue(chFtsCursor.moveToFirst())
        assertEquals("FTS rebuild debe encontrar 'Película' buscando 'pelicula'", 1, chFtsCursor.getInt(0))
        chFtsCursor.close()

        val movFtsCursor = db.query("SELECT COUNT(*) FROM movies_fts WHERE movies_fts MATCH 'cancion*'")
        assertTrue(movFtsCursor.moveToFirst())
        assertEquals("FTS rebuild debe encontrar 'Canción' buscando 'cancion'", 1, movFtsCursor.getInt(0))
        movFtsCursor.close()

        val serFtsCursor = db.query("SELECT COUNT(*) FROM series_fts WHERE series_fts MATCH 'ninez*'")
        assertTrue(serFtsCursor.moveToFirst())
        assertEquals("FTS rebuild debe encontrar 'Niñez' buscando 'ninez'", 1, serFtsCursor.getInt(0))
        serFtsCursor.close()

        db.close()
    }

    @Test
    fun migrate1To2To3To4To5_fullChain() {
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
                VALUES ('ch-chain', 101, 1, 'Corazón Salvaje TV', 'live', null, 'cat-1', 'Novelas', null, 0, 0, 'http://stream', 'ts', 'src-1')
            """.trimIndent())
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        val integrityCursor = db.query("PRAGMA integrity_check")
        assertTrue(integrityCursor.moveToFirst())
        assertEquals("ok", integrityCursor.getString(0))
        integrityCursor.close()

        // Validar búsqueda desacentuada en FTS
        val ftsCur = db.query("SELECT COUNT(*) FROM channels_fts WHERE channels_fts MATCH 'corazon*'")
        assertTrue(ftsCur.moveToFirst())
        assertEquals(1, ftsCur.getInt(0))
        ftsCur.close()

        db.close()
    }

    @Test
    fun freshInstall_version5() {
        val db = helper.createDatabase(TEST_DB, 5)

        val integrityCursor = db.query("PRAGMA integrity_check")
        assertTrue(integrityCursor.moveToFirst())
        assertEquals("ok", integrityCursor.getString(0))
        integrityCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        val ftsTables = listOf("channels_fts", "movies_fts", "series_fts")
        for (table in ftsTables) {
            val cur = db.query("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='$table'")
            assertTrue(cur.moveToFirst())
            assertEquals(1, cur.getInt(0))
            cur.close()
        }

        db.close()
    }
}
