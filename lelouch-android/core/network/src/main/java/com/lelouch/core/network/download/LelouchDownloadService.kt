package com.lelouch.core.network.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.lelouch.core.network.NetworkClient
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class DownloadTask(
    val id: Long,
    val title: String,
    val url: String,
    val fileName: String,
    val destinationPath: String,
    val partPath: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val errorMessage: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        else if (status == DownloadStatus.COMPLETED) 100 else 0

    val sizeMbFormatted: String
        get() = if (totalBytes > 0) "%.1f MB".format(totalBytes / (1024.0 * 1024.0))
        else if (downloadedBytes > 0) "%.1f MB".format(downloadedBytes / (1024.0 * 1024.0))
        else "—"

    val statusLabel: String
        get() = when (status) {
            DownloadStatus.COMPLETED -> "✅ Completada"
            DownloadStatus.DOWNLOADING -> {
                val speed = if (speedBytesPerSec > 0) " • %.1f MB/s".format(speedBytesPerSec / (1024.0 * 1024.0)) else ""
                "⬇️ Descargando ($progressPercent%)$speed"
            }
            DownloadStatus.PENDING -> "⏳ En cola..."
            DownloadStatus.PAUSED -> "⏸️ Pausada ($progressPercent%)"
            DownloadStatus.ERROR -> "❌ Error: ${errorMessage ?: "Red interrumpida"}"
        }
}

/**
 * Servicio en primer plano (Foreground Service) para descargas de Películas y Series.
 *
 * Características clave:
 * 1. Sujeta WakeLock de CPU para que la pantalla apagada o cambio de app NO congele la descarga.
 * 2. Soporta HTTP Range (resume en archivos .part): si se interrumpe la red o el usuario pausa,
 *    reanuda desde el byte exacto donde se quedó sin volver a descargar desde 0.
 * 3. Notificación persistente con barra de progreso y velocidad en MB/s.
 * 4. Al completar, renombra el .part al archivo final y notifica a MediaScannerConnection.
 */
class LelouchDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val downloadOkHttpClient: OkHttpClient by lazy {
        NetworkClient.createOkHttpClient().newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // Sin timeout de lectura para transferencias continuas gigantes
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LelouchPlus:DownloadServiceWakeLock")
        } catch (_: Exception) {}
        loadPersistedTasks(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
                if (url.isNotBlank() && title.isNotBlank() && fileName.isNotBlank()) {
                    enqueueDownload(url, title, fileName)
                }
            }
            ACTION_PAUSE -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                if (id != -1L) pauseTask(id)
            }
            ACTION_RESUME -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                if (id != -1L) resumeTask(id)
            }
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                val delete = intent.getBooleanExtra(EXTRA_DELETE_FILE, true)
                if (id != -1L) cancelTask(id, delete)
            }
        }
        return START_STICKY
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas Lelouch Plus",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de descarga de películas y series en segundo plano"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun enqueueDownload(url: String, title: String, fileName: String) {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!publicDir.exists()) publicDir.mkdirs()

        val destFile = File(publicDir, fileName)
        val partFile = File(publicDir, "$fileName.part")

        val taskId = Math.abs((url + fileName).hashCode().toLong())

        val existingTask = _tasks.value[taskId]
        if (existingTask != null && existingTask.status == DownloadStatus.DOWNLOADING) {
            return
        }

        val downloadedSoFar = if (partFile.exists()) partFile.length() else 0L

        val task = DownloadTask(
            id = taskId,
            title = title,
            url = url,
            fileName = fileName,
            destinationPath = destFile.absolutePath,
            partPath = partFile.absolutePath,
            downloadedBytes = downloadedSoFar,
            totalBytes = existingTask?.totalBytes ?: -1L,
            status = DownloadStatus.DOWNLOADING
        )

        updateTask(task)
        savePersistedTasks(this)
        startDownloadJob(task)
    }

    private fun pauseTask(id: Long) {
        val job = activeJobs.remove(id)
        job?.cancel()

        val task = _tasks.value[id] ?: return
        val currentPart = File(task.partPath)
        val currentBytes = if (currentPart.exists()) currentPart.length() else task.downloadedBytes

        updateTask(task.copy(
            downloadedBytes = currentBytes,
            speedBytesPerSec = 0L,
            status = DownloadStatus.PAUSED
        ))
        savePersistedTasks(this)
        checkActiveTasksAndForeground()
    }

    private fun resumeTask(id: Long) {
        val task = _tasks.value[id] ?: return
        if (task.status == DownloadStatus.DOWNLOADING) return

        val partFile = File(task.partPath)
        val currentBytes = if (partFile.exists()) partFile.length() else 0L

        val updated = task.copy(
            downloadedBytes = currentBytes,
            status = DownloadStatus.DOWNLOADING,
            errorMessage = null
        )
        updateTask(updated)
        savePersistedTasks(this)
        startDownloadJob(updated)
    }

    private fun cancelTask(id: Long, deleteFiles: Boolean) {
        val job = activeJobs.remove(id)
        job?.cancel()

        val task = _tasks.value[id]
        if (task != null && deleteFiles) {
            try {
                File(task.partPath).delete()
                File(task.destinationPath).delete()
            } catch (_: Exception) {}
        }

        _tasks.update { map ->
            map.filterKeys { it != id }
        }
        savePersistedTasks(this)
        checkActiveTasksAndForeground()
    }

    private fun startDownloadJob(initialTask: DownloadTask) {
        val taskId = initialTask.id
        activeJobs[taskId]?.cancel()

        acquireWakeLockIfNeeded()
        updateForegroundNotification(initialTask)

        val job = serviceScope.launch {
            runDownload(taskId)
        }
        activeJobs[taskId] = job
    }

    private suspend fun runDownload(taskId: Long) {
        val currentTask = _tasks.value[taskId] ?: return
        val partFile = File(currentTask.partPath)
        val destFile = File(currentTask.destinationPath)

        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        try {
            val reqBuilder = Request.Builder()
                .url(currentTask.url)
                .addHeader("User-Agent", "IPTVSmartersPlayer")
                .addHeader("Accept", "*/*")

            if (existingBytes > 0) {
                reqBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            val response = downloadOkHttpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                updateTask(currentTask.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = "HTTP ${response.code}: ${response.message}"
                ))
                savePersistedTasks(this@LelouchDownloadService)
                checkActiveTasksAndForeground()
                return
            }

            val isPartial = response.code == 206
            val body = response.body
            val contentLength = body?.contentLength() ?: -1L
            val totalBytes = if (isPartial && contentLength > 0) {
                existingBytes + contentLength
            } else if (contentLength > 0) {
                contentLength
            } else {
                currentTask.totalBytes
            }

            // Si es respuesta 200 completa pero ya teníamos bytes, sobreescribir desde 0
            val append = isPartial && existingBytes > 0

            val outputStream = FileOutputStream(partFile, append)
            val inputStream = body?.byteStream()

            if (inputStream == null) {
                updateTask(currentTask.copy(status = DownloadStatus.ERROR, errorMessage = "Cuerpo de respuesta vacío"))
                savePersistedTasks(this@LelouchDownloadService)
                checkActiveTasksAndForeground()
                return
            }

            outputStream.use { out ->
                inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = if (append) existingBytes else 0L
                    var lastUpdateTime = System.currentTimeMillis()
                    var bytesSinceLastUpdate = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!coroutineContext.isActive) {
                            return // Job fue cancelado o pausado
                        }
                        out.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        bytesSinceLastUpdate += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 750L) {
                            val elapsedSec = (now - lastUpdateTime) / 1000.0
                            val speed = if (elapsedSec > 0) (bytesSinceLastUpdate / elapsedSec).toLong() else 0L

                            val updated = _tasks.value[taskId]?.copy(
                                downloadedBytes = totalDownloaded,
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed,
                                status = DownloadStatus.DOWNLOADING
                            )
                            if (updated != null) {
                                updateTask(updated)
                                updateForegroundNotification(updated)
                            }
                            lastUpdateTime = now
                            bytesSinceLastUpdate = 0L
                        }
                    }
                }
            }

            // Descarga finalizada: Renombrar .part a archivo final
            if (partFile.exists()) {
                if (destFile.exists()) destFile.delete()
                val renamed = partFile.renameTo(destFile)
                val finalFile = if (renamed) destFile else partFile

                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(finalFile.absolutePath),
                    null,
                    null
                )
            }

            val completed = _tasks.value[taskId]?.copy(
                downloadedBytes = totalBytes.takeIf { it > 0 } ?: destFile.length(),
                totalBytes = totalBytes.takeIf { it > 0 } ?: destFile.length(),
                speedBytesPerSec = 0L,
                status = DownloadStatus.COMPLETED
            )
            if (completed != null) {
                updateTask(completed)
                savePersistedTasks(this@LelouchDownloadService)
                showCompletedNotification(completed)
            }

        } catch (e: Exception) {
            if (coroutineContext.isActive) {
                val failed = _tasks.value[taskId]?.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = e.message ?: "Fallo de conexión"
                )
                if (failed != null) {
                    updateTask(failed)
                    savePersistedTasks(this@LelouchDownloadService)
                }
            }
        } finally {
            activeJobs.remove(taskId)
            checkActiveTasksAndForeground()
        }
    }

    private fun updateForegroundNotification(task: DownloadTask) {
        val totalMb = if (task.totalBytes > 0) task.totalBytes / (1024.0 * 1024.0) else 0.0
        val downMb = task.downloadedBytes / (1024.0 * 1024.0)
        val speedMb = task.speedBytesPerSec / (1024.0 * 1024.0)

        val subtext = if (totalMb > 0) {
            "%.1f MB / %.1f MB (%.1f MB/s)".format(downMb, totalMb, speedMb)
        } else {
            "%.1f MB descargados (%.1f MB/s)".format(downMb, speedMb)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lelouch Plus: ${task.title}")
            .setContentText(subtext)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, task.progressPercent, task.totalBytes <= 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showCompletedNotification(task: DownloadTask) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("✅ Descarga completada")
            .setContentText(task.title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify((task.id % 100000).toInt(), notification)
    }

    private fun acquireWakeLockIfNeeded() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24h safety timeout
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLockIfNoActiveTasks() {
        try {
            if (activeJobs.isEmpty() && wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun checkActiveTasksAndForeground() {
        if (activeJobs.isEmpty()) {
            releaseWakeLockIfNoActiveTasks()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        releaseWakeLockIfNoActiveTasks()
    }

    companion object {
        const val CHANNEL_ID = "lelouch_downloads_channel"
        const val NOTIFICATION_ID = 8802

        const val ACTION_START = "com.lelouch.download.ACTION_START"
        const val ACTION_PAUSE = "com.lelouch.download.ACTION_PAUSE"
        const val ACTION_RESUME = "com.lelouch.download.ACTION_RESUME"
        const val ACTION_CANCEL = "com.lelouch.download.ACTION_CANCEL"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_DELETE_FILE = "extra_delete_file"

        private val _tasks = MutableStateFlow<Map<Long, DownloadTask>>(emptyMap())
        val tasksState = _tasks.asStateFlow()

        fun updateTask(task: DownloadTask) {
            _tasks.update { current ->
                current + (task.id to task)
            }
        }

        fun startDownload(context: Context, url: String, title: String, fileName: String) {
            val intent = Intent(context, LelouchDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FILE_NAME, fileName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseDownload(context: Context, id: Long) {
            val intent = Intent(context, LelouchDownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_ID, id)
            }
            context.startService(intent)
        }

        fun resumeDownload(context: Context, id: Long) {
            val intent = Intent(context, LelouchDownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_ID, id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context, id: Long, deleteFile: Boolean = true) {
            val intent = Intent(context, LelouchDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_DELETE_FILE, deleteFile)
            }
            context.startService(intent)
        }

        fun savePersistedTasks(context: Context) {
            try {
                val prefs = context.getSharedPreferences("lelouch_download_service_prefs", Context.MODE_PRIVATE)
                val array = JSONArray()
                _tasks.value.values.forEach { task ->
                    val obj = JSONObject().apply {
                        put("id", task.id)
                        put("title", task.title)
                        put("url", task.url)
                        put("fileName", task.fileName)
                        put("dest", task.destinationPath)
                        put("part", task.partPath)
                        put("totalBytes", task.totalBytes)
                        put("downloadedBytes", task.downloadedBytes)
                        put("status", if (task.status == DownloadStatus.DOWNLOADING) DownloadStatus.PAUSED.name else task.status.name)
                    }
                    array.put(obj)
                }
                prefs.edit().putString("tasks_json", array.toString()).apply()
            } catch (_: Exception) {}
        }

        fun loadPersistedTasks(context: Context) {
            try {
                val prefs = context.getSharedPreferences("lelouch_download_service_prefs", Context.MODE_PRIVATE)
                val raw = prefs.getString("tasks_json", null) ?: return
                val array = JSONArray(raw)
                val map = mutableMapOf<Long, DownloadTask>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getLong("id")
                    val partPath = obj.getString("part")
                    val destPath = obj.getString("dest")
                    val destFile = File(destPath)
                    val partFile = File(partPath)

                    val isCompleted = destFile.exists() && destFile.length() > 0L
                    val partBytes = if (partFile.exists()) partFile.length() else obj.optLong("downloadedBytes", 0L)
                    val totalBytes = obj.optLong("totalBytes", -1L)

                    val statusStr = obj.optString("status", DownloadStatus.PAUSED.name)
                    val status = if (isCompleted) {
                        DownloadStatus.COMPLETED
                    } else when (statusStr) {
                        DownloadStatus.COMPLETED.name -> DownloadStatus.COMPLETED
                        else -> DownloadStatus.PAUSED
                    }

                    map[id] = DownloadTask(
                        id = id,
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        fileName = obj.getString("fileName"),
                        destinationPath = destPath,
                        partPath = partPath,
                        downloadedBytes = if (isCompleted) destFile.length() else partBytes,
                        totalBytes = totalBytes,
                        status = status
                    )
                }
                _tasks.value = map
            } catch (_: Exception) {}
        }
    }
}

