package com.lelouch.player

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lelouch.core.data.sync.SyncState
import com.lelouch.core.designsystem.LelouchBackground
import com.lelouch.core.designsystem.LelouchTheme
import com.lelouch.core.designsystem.LelouchTvTheme
import com.lelouch.feature.mobile.MobileHomeScreen
import com.lelouch.feature.mobile.MobileLoginScreen
import com.lelouch.feature.tv.TvHomeScreen
import com.lelouch.feature.tv.TvLoginScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = LelouchApplication.instance
        val isTv = isAndroidTvDevice()

        setContent {
            val activeSource by app.authRepository.getActiveSource().collectAsStateWithLifecycle(initialValue = null)
            val syncState by app.syncManager.syncState.collectAsStateWithLifecycle()

            var errorMessage by remember { mutableStateOf<String?>(null) }
            val isLoading = syncState is SyncState.Authenticating ||
                    syncState is SyncState.SyncingCategories ||
                    syncState is SyncState.SyncingLive ||
                    syncState is SyncState.SyncingMovies ||
                    syncState is SyncState.SyncingSeries

            val syncStatusText = when (val state = syncState) {
                is SyncState.Authenticating -> "Validando credenciales con el servidor..."
                is SyncState.SyncingCategories -> state.step
                is SyncState.SyncingLive -> "Sincronizando canales en vivo (${state.count} importados)..."
                is SyncState.SyncingMovies -> "Sincronizando películas VOD (${state.count} importadas)..."
                is SyncState.SyncingSeries -> "Sincronizando series (${state.count} importadas)..."
                is SyncState.Completed -> "¡Catálogo sincronizado con éxito!"
                is SyncState.Error -> state.message
                else -> ""
            }

            if (syncState is SyncState.Error) {
                errorMessage = (syncState as SyncState.Error).message
            }

            fun onLogin(serverUrl: String, user: String, pass: String) {
                errorMessage = null
                lifecycleScope.launch {
                    val authResult = app.authRepository.validateXtream(serverUrl, user, pass)
                    authResult.onSuccess { source ->
                        // Sincronizar catálogo tras login exitoso
                        app.syncManager.syncAll(source.id, serverUrl, user, pass)
                    }.onFailure { error ->
                        errorMessage = error.localizedMessage ?: "Error al conectar con el servidor IPTV"
                    }
                }
            }

            if (isTv) {
                LelouchTvTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LelouchBackground
                    ) {
                        if (activeSource != null) {
                            TvHomeScreen()
                        } else {
                            TvLoginScreen(
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                syncStatusText = syncStatusText,
                                onLoginClick = ::onLogin
                            )
                        }
                    }
                }
            } else {
                LelouchTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LelouchBackground
                    ) {
                        if (activeSource != null) {
                            MobileHomeScreen()
                        } else {
                            MobileLoginScreen(
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                onLoginClick = ::onLogin
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isAndroidTvDevice(): Boolean {
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        return hasLeanback || isTelevisionMode
    }
}
