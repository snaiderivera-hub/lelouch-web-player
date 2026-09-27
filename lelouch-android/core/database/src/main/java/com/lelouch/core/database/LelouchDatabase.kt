package com.lelouch.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lelouch.core.database.dao.CategoryDao
import com.lelouch.core.database.dao.ChannelDao
import com.lelouch.core.database.dao.FavoriteDao
import com.lelouch.core.database.dao.MovieDao
import com.lelouch.core.database.dao.SearchDao
import com.lelouch.core.database.dao.SeriesDao
import com.lelouch.core.database.dao.WatchHistoryDao
import com.lelouch.core.database.entity.CategoryEntity
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.ChannelFtsEntity
import com.lelouch.core.database.entity.FavoriteEntity
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.database.entity.MovieFtsEntity
import com.lelouch.core.database.entity.SeriesEntity
import com.lelouch.core.database.entity.SeriesFtsEntity
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
        SeriesFtsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LelouchDatabase : RoomDatabase() {

    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun categoryDao(): CategoryDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchDao(): SearchDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
