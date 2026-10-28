package com.lelouch.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lelouch.player.image.LelouchImageLoader
import com.lelouch.core.data.preferences.RETIRED_CUSTOM_SOURCE_ID
import com.lelouch.core.data.preferences.UserPreferencesDataSource
import com.lelouch.core.data.repository.AuthRepositoryImpl
import com.lelouch.core.data.repository.ChannelRepositoryImpl
import com.lelouch.core.data.repository.PlaybackHistoryRepositoryImpl
import com.lelouch.core.data.repository.SeriesRepositoryImpl
import com.lelouch.core.data.repository.VodRepositoryImpl
import com.lelouch.core.data.sync.XtreamCatalogSyncManager
import com.lelouch.core.database.LelouchDatabase
import com.lelouch.core.domain.repository.AuthRepository
import com.lelouch.core.domain.repository.ChannelRepository
import com.lelouch.core.domain.repository.PlaybackHistoryRepository
import com.lelouch.core.domain.repository.SeriesRepository
import com.lelouch.core.domain.repository.VodRepository
import com.lelouch.core.network.diagnostics.IptvDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    lateinit var playbackHistoryRepository: PlaybackHistoryRepository
        private set

    /** Diagnóstico de cuenta/lista/red que se ejecuta desde Ajustes (test manual). */
    lateinit var diagnostics: IptvDiagnostics
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.lelouch.core.network.dns.LelouchDnsManager.init(this)

        database = LelouchDatabase.getInstance(this)
        preferencesDataSource = UserPreferencesDataSource(this)
        syncManager = XtreamCatalogSyncManager(database, preferencesDataSource)

        authRepository = AuthRepositoryImpl(preferencesDataSource)
        channelRepository = ChannelRepositoryImpl(database.channelDao(), database.categoryDao(), syncManager, database.searchDao())
        vodRepository = VodRepositoryImpl(database.movieDao(), database.categoryDao(), syncManager, database.searchDao())
        seriesRepository = SeriesRepositoryImpl(database.seriesDao(), database.categoryDao(), syncManager, database.searchDao())
        playbackHistoryRepository = PlaybackHistoryRepositoryImpl(database.watchHistoryDao())
        diagnostics = IptvDiagnostics()

        // FASE 35 (1.0.97): la lista personalizada quedó retirada. Se purga su catálogo local
        // (incluidos los 3.594 ítems de la última sincronización) en cada arranque. Las
        // eliminaciones son idempotentes y baratas cuando ya no queda nada.
        applicationScope.launch { purgeRetiredSources() }
    }

    private suspend fun purgeRetiredSources() {
        try {
            // Preferencias primero: la lista retirada desaparece de la UI aunque la purga de
            // Room tarde. También repara el puntero activo si apuntaba a ella.
            preferencesDataSource.purgeRetiredSources()
            purgeSourceData(RETIRED_CUSTOM_SOURCE_ID)
            android.util.Log.i("LelouchApplication", "RETIRED_SOURCE_PURGED: $RETIRED_CUSTOM_SOURCE_ID")
        } catch (e: Exception) {
            android.util.Log.w("LelouchApplication", "RETIRED_SOURCE_PURGE_FAILED: ${e.message}")
        }
    }

    /**
     * Elimina del dispositivo todo el catálogo y las referencias de una fuente.
     * Pensado para la fuente retirada y para el borrado manual de listas.
     */
    suspend fun purgeSourceData(sourceId: String) {
        database.channelDao().deleteChannelsBySource(sourceId)
        database.movieDao().deleteMoviesBySource(sourceId)
        database.seriesDao().deleteSeriesBySource(sourceId)
        database.categoryDao().deleteCategoriesBySource(sourceId)
        database.favoriteDao().deleteFavoritesBySource(sourceId)
        database.watchHistoryDao().clearHistory(sourceId)
        database.catalogStagingDao().deleteStagingChannelsBySource(sourceId)
        database.catalogStagingDao().deleteStagingMoviesBySource(sourceId)
        database.catalogStagingDao().deleteStagingSeriesBySource(sourceId)
        database.catalogStagingDao().deleteStagingCategoriesBySource(sourceId)
        preferencesDataSource.removeSyncMetadata(sourceId)
    }

    companion object {
        lateinit var instance: LelouchApplication
            private set
    }
}
