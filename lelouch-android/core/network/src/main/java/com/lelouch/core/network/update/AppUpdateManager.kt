package com.lelouch.core.network.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lelouch.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val changelog: String
)

object AppUpdateManager {

    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/snaiderivera-hub/lelouch-web-player/releases/latest"
    private const val RAW_VERSION_URL =
        "https://raw.githubusercontent.com/snaiderivera-hub/lelouch-web-player/main/version.json"

    suspend fun checkForUpdates(
        context: Context,
        customUrl: String? = null
    ): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val client = NetworkClient.createOkHttpClient()
            val targetUrl = customUrl?.trim()?.takeIf { it.isNotBlank() } ?: GITHUB_LATEST_RELEASE_URL
            val request = Request.Builder()
                .url(targetUrl)
                .header("Accept", "application/json")
                .build()

            // Obtener el versionCode actual instalado en el dispositivo
            val installedVersionCode = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                }
            } catch (e: Exception) { 1 }

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (targetUrl == GITHUB_LATEST_RELEASE_URL) {
                        return@withContext checkFromRawVersion(client, RAW_VERSION_URL, installedVersionCode)
                    }
                    return@withContext Result.failure(Exception("Error HTTP ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.success(null)
                val json = JSONObject(bodyStr)

                // Si es formato GitHub Release
                if (json.has("tag_name")) {
                    val tagName = json.optString("tag_name", "v1.0.0")
                    val body = json.optString("body", "Nueva actualización disponible.")
                    var apkUrl: String? = null

                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }

                    if (apkUrl.isNullOrBlank()) {
                        return@withContext Result.success(null)
                    }

                    val remoteVersionCode = parseVersionNumber(tagName)
                    // Solo hay actualización si el remoto es MAYOR que el instalado
                    if (remoteVersionCode <= installedVersionCode) {
                        return@withContext Result.success(null) // Ya está al día
                    }

                    return@withContext Result.success(
                        UpdateInfo(
                            versionName = tagName,
                            versionCode = remoteVersionCode,
                            downloadUrl = apkUrl,
                            changelog = body
                        )
                    )
                }

                // Si es formato version.json directo
                if (json.has("apkUrl")) {
                    val rawVc = json.optInt("versionCode", 0)
                    val vName = json.optString("versionName", "1.0.0")
                    val remoteVersionCode = if (rawVc >= 100_000) rawVc else parseVersionNumber(vName)
                    if (remoteVersionCode <= installedVersionCode) {
                        return@withContext Result.success(null)
                    }
                    return@withContext Result.success(
                        UpdateInfo(
                            versionName = vName,
                            versionCode = remoteVersionCode,
                            downloadUrl = json.getString("apkUrl"),
                            changelog = json.optString("changelog", "Mejoras de rendimiento y estabilidad.")
                        )
                    )
                }

                Result.success(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun checkFromRawVersion(
        client: okhttp3.OkHttpClient,
        url: String,
        installedVersionCode: Int = 1
    ): Result<UpdateInfo?> {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return Result.success(null)
                val str = res.body?.string() ?: return Result.success(null)
                val json = JSONObject(str)
                val rawVc = json.optInt("versionCode", 0)
                val vName = json.optString("versionName", "1.0.0")
                val remoteVersionCode = if (rawVc >= 100_000) rawVc else parseVersionNumber(vName)
                if (remoteVersionCode <= installedVersionCode) {
                    return Result.success(null) // Ya está al día
                }
                Result.success(
                    UpdateInfo(
                        versionName = json.optString("versionName", "1.0.0"),
                        versionCode = remoteVersionCode,
                        downloadUrl = json.getString("apkUrl"),
                        changelog = json.optString("changelog", "Mejoras de rendimiento y estabilidad.")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (progress: Float, downloadedMb: Float, totalMb: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val client = NetworkClient.createOkHttpClient()
            val request = Request.Builder().url(downloadUrl).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error al descargar: HTTP ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Cuerpo de descarga vacío"))
                val totalLength = body.contentLength()
                val totalMb = if (totalLength > 0) totalLength / (1024f * 1024f) else 0f

                val updatesDir = File(context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val apkFile = File(updatesDir, "lelouch_update.apk")
                if (apkFile.exists()) apkFile.delete()

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val downloadedMb = totalRead / (1024f * 1024f)
                            val progress = if (totalLength > 0) (totalRead.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f) else 0f
                            onProgress(progress, downloadedMb, totalMb)
                        }
                        output.flush()
                    }
                }

                Result.success(apkFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(permissionIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AppUpdateManager", "Error al instalar APK: ${e.message}")
        }
    }

    /**
     * Convierte un tag de GitHub a un entero comparable.
     * Ejemplos:
     *   "ver.1.0.1" → 1_000_001
     *   "v1.0.2"    → 1_000_002
     *   "2.0.0"     → 2_000_000
     *   "1.1.0"     → 1_001_000
     * Fórmula: major * 1_000_000 + minor * 1_000 + patch
     * Debe coincidir con versionCode en app/build.gradle.kts.
     */
    private fun parseVersionNumber(versionStr: String): Int {
        // Extraer solo la parte semver: quitar prefijos tipo "ver.", "v", "version-"
        val clean = versionStr.replace(Regex("^[^0-9]*"), "") // remueve todo antes del primer dígito
        val parts = clean.split(".").take(3).map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 1_000_000 + minor * 1_000 + patch
    }
}
