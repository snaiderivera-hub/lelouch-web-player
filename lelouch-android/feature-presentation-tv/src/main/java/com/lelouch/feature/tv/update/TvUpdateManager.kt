package com.lelouch.feature.tv.update

import android.content.Context
import com.lelouch.core.network.update.AppUpdateManager
import java.io.File

typealias UpdateInfo = com.lelouch.core.network.update.UpdateInfo

object TvUpdateManager {
    suspend fun checkForUpdates(context: Context, customUrl: String? = null) =
        AppUpdateManager.checkForUpdates(context, customUrl)

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (progress: Float, downloadedMb: Float, totalMb: Float) -> Unit
    ): Result<File> = AppUpdateManager.downloadApk(context, downloadUrl, onProgress)

    fun installApk(context: Context, apkFile: File) =
        AppUpdateManager.installApk(context, apkFile)
}
