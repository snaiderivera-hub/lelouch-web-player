package com.lelouch.player.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import com.lelouch.core.network.security.ClientSecretVault
import com.lelouch.player.BuildConfig
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * Verificación en tiempo de ejecución para compilaciones Release:
 * - Detecta reempaquetado / cambio de firma mediante huella SHA-256 del certificado.
 * - Detecta si alguien habilitó android:debuggable o conectó un depurador.
 * En builds Debug (`BuildConfig.DEBUG == true`) no bloquea pruebas locales ni unit tests.
 */
object ReleaseIntegrityGuard {

    private val expectedReleaseSha256: String by lazy {
        ClientSecretVault.decode(
            0x73,
            75, 233, 167, 94, 69, 122, 53, 234, 217, 224, 62, 7, 25, 87, 166, 148, 135, 202,
            7, 90, 101, 189, 132, 200, 218, 102, 109, 215, 70, 133, 218, 31, 81, 121, 182, 253,
            151, 236, 86, 13, 120, 70, 239, 166, 137, 68, 15, 71, 38, 174, 234, 60, 196, 106,
            87, 168, 178, 133, 200, 11, 43, 110, 184, 131
        )
    }

    fun verifyOrTerminate(context: Context) {
        if (BuildConfig.DEBUG) return

        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val debuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        if (isDebuggable || debuggerAttached) {
            terminateProcess()
            return
        }

        if (!hasValidReleaseSignature(context)) {
            terminateProcess()
        }
    }

    @Suppress("DEPRECATION")
    private fun hasValidReleaseSignature(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo ?: return false
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            } ?: return false

            val digest = MessageDigest.getInstance("SHA-256")
            signatures.any { sig ->
                val hash = digest.digest(sig.toByteArray())
                    .joinToString("") { "%02X".format(it) }
                hash.equals(expectedReleaseSha256, ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun terminateProcess() {
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
