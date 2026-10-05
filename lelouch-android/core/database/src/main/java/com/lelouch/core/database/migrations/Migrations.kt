package com.lelouch.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migración de base de datos v1 a v2.
 *
 * En versión 1:
 * - `index_channels_streamId` era UNIQUE.
 * - `index_movies_streamId` era UNIQUE.
 *
 * En versión 2:
 * - Los índices `index_channels_streamId` e `index_movies_streamId` pasaron a no-únicos
 *   para permitir streams compartidos entre categorías o múltiples fuentes IPTV.
 *
 * Esta migración preserva 100% de los datos (canales, películas, series, categorías,
 * historial de reproducción, favoritos y tablas FTS), reemplazando únicamente
 * los dos índices sin destruir ni alterar ninguna tabla.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Reemplazar índice de channels por no-único
        db.execSQL("DROP INDEX IF EXISTS `index_channels_streamId`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_streamId` ON `channels` (`streamId`)")

        // Reemplazar índice de movies por no-único
        db.execSQL("DROP INDEX IF EXISTS `index_movies_streamId`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_streamId` ON `movies` (`streamId`)")
    }
}
