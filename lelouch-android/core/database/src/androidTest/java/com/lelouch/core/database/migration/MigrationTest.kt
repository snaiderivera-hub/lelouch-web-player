package com.lelouch.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lelouch.core.database.LelouchDatabase
import com.lelouch.core.database.migrations.MIGRATION_1_2
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
            // Insertar fixture representativo en v1
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
}
