package com.lelouch.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lelouch.player.image.LelouchImageLoader
import com.lelouch.core.data.preferences.UserPreferencesDataSource
import com.lelouch.core.data.repository.AuthRepositoryImpl
import com.lelouch.core.data.repository.ChannelRepositoryImpl
import com.lelouch.core.data.repository.SeriesRepositoryImpl
import com.lelouch.core.data.repository.VodRepositoryImpl
import com.lelouch.core.data.sync.XtreamCatalogSyncManager
import com.lelouch.core.database.LelouchDatabase
import com.lelouch.core.domain.repository.AuthRepository
import com.lelouch.core.domain.repository.ChannelRepository
import com.lelouch.core.domain.repository.SeriesRepository
import com.lelouch.core.domain.repository.VodRepository

class LelouchApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return LelouchImageLoader.create(this)
    }

    lateinit var database: LelouchDatabase
        private set

    lateinit var preferencesDataSource: UserPreferencesDataSource
        private set

    lateinit var syncManager: XtreamCatalogSyncManager
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var channelRepository: ChannelRepository
        private set

    lateinit var vodRepository: VodRepository
        private set

    lateinit var seriesRepository: SeriesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = LelouchDatabase.getInstance(this)
        preferencesDataSource = UserPreferencesDataSource(this)
        syncManager = XtreamCatalogSyncManager(database, preferencesDataSource)

        authRepository = AuthRepositoryImpl(preferencesDataSource)
        channelRepository = ChannelRepositoryImpl(database.channelDao(), database.categoryDao(), syncManager, database.searchDao())
        vodRepository = VodRepositoryImpl(database.movieDao(), database.categoryDao(), syncManager, database.searchDao())
        seriesRepository = SeriesRepositoryImpl(database.seriesDao(), database.categoryDao(), syncManager, database.searchDao())
    }

    companion object {
        lateinit var instance: LelouchApplication
            private set
    }
}
