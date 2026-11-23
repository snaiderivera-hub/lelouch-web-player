package com.lelouch.player

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Log

/**
 * Receiver exclusivo para Android TV / TV Box: abre LELOUCH automáticamente
 * cuando el equipo termina de arrancar. En teléfonos/tablets se ignora para no
 * interrumpir al usuario.
 */
class TvBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != ACTION_QUICKBOOT_POWERON &&
            action != ACTION_HTC_QUICKBOOT_POWERON
        ) {
            return
        }

        if (!isTvDevice(context)) {
            Log.d(TAG, "Ignorando arranque automático en dispositivo no-TV.")
            return
        }

        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(launchIntent)
            Log.i(TAG, "Auto-arranque ejecutado en TV Box tras $action.")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo lanzar MainActivity en arranque TV: ${e.message}", e)
        }
    }

    private fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    companion object {
        private const val TAG = "TvBootReceiver"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
