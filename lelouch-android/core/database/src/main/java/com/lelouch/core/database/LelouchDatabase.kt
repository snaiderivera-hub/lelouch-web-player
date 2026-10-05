package com.lelouch.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lelouch.core.database.dao.CatalogStagingDao
import com.lelouch.core.database.dao.CategoryDao
import com.lelouch.core.database.dao.ChannelDao
import com.lelouch.core.database.dao.FavoriteDao
import com.lelouch.core.database.dao.MovieDao
import com.lelouch.core.database.dao.SearchDao
import com.lelouch.core.database.dao.SeriesDao
import com.lelouch.core.database.dao.WatchHistoryDao
import com.lelouch.core.database.entity.CategoryEntity
import com.lelouch.core.database.entity.CategoryStagingEntity
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.ChannelFtsEntity
import com.lelouch.core.database.entity.ChannelStagingEntity
import com.lelouch.core.database.entity.FavoriteEntity
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.database.entity.MovieFtsEntity
import com.lelouch.core.database.entity.MovieStagingEntity
import com.lelouch.core.database.entity.SeriesEntity
import com.lelouch.core.database.entity.SeriesFtsEntity
import com.lelouch.core.database.entity.SeriesStagingEntity
import com.lelouch.core.database.entity.WatchHistoryEntity

@Database(
    entities = [
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        CategoryEntity::class,
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        MovieFtsEntity::class,
        ChannelFtsEntity::class,
        SeriesFtsEntity::class,
        ChannelStagingEntity::class,
        MovieStagingEntity::class,
        SeriesStagingEntity::class,
        CategoryStagingEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class LelouchDatabase : RoomDatabase() {

    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun categoryDao(): CategoryDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchDao(): SearchDao
    abstract fun catalogStagingDao(): CatalogStagingDao

    companion object {
        private const val DATABASE_NAME = "lelouch_player.db"

        @Volatile
        private var INSTANCE: LelouchDatabase? = null

        fun getInstance(context: Context): LelouchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LelouchDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        com.lelouch.core.database.migrations.MIGRATION_1_2,
                        com.lelouch.core.database.migrations.MIGRATION_2_3
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
