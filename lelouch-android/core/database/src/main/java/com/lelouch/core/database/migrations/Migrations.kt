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

/**
 * Migración de base de datos v2 a v3 (FASE P0 #4: Sync Atómico sin bloquear UI).
 *
 * Agrega las 4 tablas de staging aisladas:
 * - `channels_staging`
 * - `movies_staging`
 * - `series_staging`
 * - `categories_staging`
 *
 * Cada tabla incluye `syncId` y `sourceId` para aislar múltiples ejecuciones y proveedores,
 * evitando que la descarga y parseo de catálogos disparen invalidaciones prematuras de Room
 * sobre las tablas activas visibles en la UI.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. channels_staging
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `channels_staging` (
                `syncId` TEXT NOT NULL,
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
                PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_staging_syncId_sourceId` ON `channels_staging` (`syncId`, `sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_staging_streamId` ON `channels_staging` (`streamId`)")

        // 2. movies_staging
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `movies_staging` (
                `syncId` TEXT NOT NULL,
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
                PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_staging_syncId_sourceId` ON `movies_staging` (`syncId`, `sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_staging_streamId` ON `movies_staging` (`streamId`)")

        // 3. series_staging
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `series_staging` (
                `syncId` TEXT NOT NULL,
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
                PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_staging_syncId_sourceId` ON `series_staging` (`syncId`, `sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_staging_seriesId` ON `series_staging` (`seriesId`)")

        // 4. categories_staging
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories_staging` (
                `syncId` TEXT NOT NULL,
                `id` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `parentId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `itemCount` INTEGER NOT NULL,
                `isAdult` INTEGER NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`syncId`, `id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_staging_syncId_sourceId` ON `categories_staging` (`syncId`, `sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_staging_categoryId_type_sourceId` ON `categories_staging` (`categoryId`, `type`, `sourceId`)")
    }
}

/**
 * Migración de base de datos v3 a v4 (FASE P1 #1: Identidad Multi-Proveedor + Índices Room).
 *
 * En versión 3:
 * - `series` tenía un índice UNIQUE global en `seriesId`: `index_series_seriesId`.
 *   Esto causaba colisiones destructivas si dos proveedores IPTV compartían el mismo `seriesId`.
 * - `channels`, `movies` y `series` carecían de índices compuestos por `sourceId` y `(sourceId, categoryId)`.
 * - `categories` carecía de índice compuesto por `(sourceId, type)`.
 *
 * En versión 4:
 * 1. Se reemplaza `index_series_seriesId` (UNIQUE global) por `index_series_sourceId_seriesId` (UNIQUE compuesto).
 * 2. Se normalizan IDs de Xtream previo ('live_%', 'vod_%', 'series_%') incorporando prefijo `sourceId`.
 * 3. Se agregan los índices optimizados para consultas reales:
 *    - `index_channels_sourceId` y `index_channels_sourceId_categoryId`
 *    - `index_movies_sourceId` y `index_movies_sourceId_categoryId`
 *    - `index_series_sourceId_categoryId`
 *    - `index_categories_sourceId_type`
 *
 * Esta migración preserva el 100% de los datos sin reconstrucción destructiva.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Eliminar índice UNIQUE global de series y crear índice UNIQUE compuesto por sourceId
        db.execSQL("DROP INDEX IF EXISTS `index_series_seriesId`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_sourceId_seriesId` ON `series` (`sourceId`, `seriesId`)")

        // 2. Normalizar IDs huérfanos o sin sourceId de Xtream previo
        db.execSQL("UPDATE `channels` SET `id` = `sourceId` || '_' || `id` WHERE `sourceId` != '' AND `id` LIKE 'live_%'")
        db.execSQL("UPDATE `movies` SET `id` = `sourceId` || '_' || `id` WHERE `sourceId` != '' AND `id` LIKE 'vod_%'")
        db.execSQL("UPDATE `series` SET `id` = `sourceId` || '_' || `id` WHERE `sourceId` != '' AND `id` LIKE 'series_%'")

        // 3. Crear índices de consulta reales
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId` ON `channels` (`sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_categoryId` ON `channels` (`sourceId`, `categoryId`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_sourceId` ON `movies` (`sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_sourceId_categoryId` ON `movies` (`sourceId`, `categoryId`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_sourceId_categoryId` ON `series` (`sourceId`, `categoryId`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_sourceId_type` ON `categories` (`sourceId`, `type`)")
    }
}
