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
import androidx.compose.ui.Modifier
import com.lelouch.core.designsystem.LelouchBackground
import com.lelouch.core.designsystem.LelouchTheme
import com.lelouch.core.designsystem.LelouchTvTheme
import com.lelouch.feature.mobile.MobileHomeScreen
import com.lelouch.feature.tv.TvHomeScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isTv = isAndroidTvDevice()

        setContent {
            if (isTv) {
                // Experiencia 10-Foot UI para Android TV / Google TV
                LelouchTvTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LelouchBackground
                    ) {
                        TvHomeScreen()
                    }
                }
            } else {
                // Experiencia táctil optimizada para Móvil y Tablet
                LelouchTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LelouchBackground
                    ) {
                        MobileHomeScreen()
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
