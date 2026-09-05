package com.sameerasw.draft.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sameerasw.draft.MainActivity
import com.sameerasw.draft.R
import com.sameerasw.draft.VeloApp
import com.sameerasw.draft.data.downloader.YoutubeDLManager
import com.sameerasw.draft.data.model.DownloadTask
import com.sameerasw.draft.data.model.TaskStatus
import com.sameerasw.draft.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: DownloadRepository

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    YoutubeDLManager.cancel(taskId)
                    val task = repository.tasks.value.find { it.id == taskId }
                    if (task != null) {
                        repository.upsertTask(task.copy(status = TaskStatus.CANCELED))
                    }
                }
            }
            ACTION_DOWNLOAD -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    val task = repository.tasks.value.find { it.id == taskId }
                    if (task != null) {
                        startForeground(NOTIFICATION_ID, createNotification(task.title, 0, "Starting download…"))
                        executeDownload(task)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun executeDownload(task: DownloadTask) {
        serviceScope.launch {
            val prefs = com.sameerasw.draft.data.repository.PreferencesManager.getInstance(this@DownloadService)
            if (!prefs.cellularDownload.value) {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val network = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(network)
                val isCellular = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
                if (isCellular) {
                    repository.upsertTask(
                        task.copy(
                            status = TaskStatus.FAILED,
                            errorMsg = "Cellular download is disabled in Settings",
                            speed = "",
                            eta = ""
                        )
                    )
                    return@launch
                }
            }

            repository.upsertTask(task.copy(status = TaskStatus.DOWNLOADING, progress = 0f))
            val outputDir = repository.getDownloadDirectory()

            val result = YoutubeDLManager.download(
                context = this@DownloadService,
                task = task,
                outputDir = outputDir,
                onProgress = { progress, etaInSeconds, line ->
                    val speed = extractSpeed(line)
                    val eta = if (etaInSeconds > 0) "${etaInSeconds}s remaining" else ""
                    val updatedTask = task.copy(
                        status = TaskStatus.DOWNLOADING,
                        progress = progress,
                        speed = speed,
                        eta = eta
                    )
                    repository.upsertTask(updatedTask)
                    updateNotification(task.title, progress.toInt(), "$speed  $eta")
                }
            )

            result.fold(
                onSuccess = { file ->
                    val sizeFormatted = formatFileSize(file.length())
                    val completedTask = task.copy(
                        status = TaskStatus.COMPLETED,
                        progress = 100f,
                        filePath = file.absolutePath,
                        fileSize = sizeFormatted,
                        speed = "",
                        eta = ""
                    )
                    repository.upsertTask(completedTask)

                    // Refresh Android system media gallery
                    val mime = if (task.isAudioOnly) "audio/mpeg" else "video/mp4"
                    android.media.MediaScannerConnection.scanFile(
                        this@DownloadService,
                        arrayOf(file.absolutePath),
                        arrayOf(mime)
                    ) { path, uri ->
                        Log.d(TAG, "MediaScanner scanned $path -> $uri")
                    }

                    showCompletedNotification(completedTask)
                },
                onFailure = { error ->
                    Log.e(TAG, "Download failed for ${task.title}", error)
                    repository.upsertTask(
                        task.copy(
                            status = TaskStatus.FAILED,
                            errorMsg = error.localizedMessage ?: "Download failed",
                            speed = "",
                            eta = ""
                        )
                    )
                }
            )

            // Check if more downloads are active; if none, stop foreground
            val hasActiveDownloads = repository.tasks.value.any { it.status == TaskStatus.DOWNLOADING }
            if (!hasActiveDownloads) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun showCompletedNotification(task: DownloadTask) {
        task.filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val uri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                val mime = if (task.isAudioOnly) "audio/*" else "video/*"
                val playIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    task.id.hashCode(),
                    playIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(this, VeloApp.CHANNEL_DOWNLOAD_COMPLETED)
                    .setSmallIcon(R.drawable.rounded_download_24)
                    .setContentTitle(task.title.ifBlank { "Download Completed" })
                    .setContentText("Tap to play • ${task.fileSize ?: ""}")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(task.id.hashCode(), notification)
            }
        }
    }

    private fun createNotification(title: String, progress: Int, contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VeloApp.CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.rounded_download_24)
            .setContentTitle(title.ifBlank { "Downloading video" })
            .setContentText(contentText)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, progress: Int, contentText: String) {
        val notification = createNotification(title, progress, contentText)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun extractSpeed(line: String): String {
        val match = Regex("""(\d+(\.\d+)?\s*(KiB|MiB|GiB|B)/s)""").find(line)
        return match?.value ?: ""
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 2026

        const val ACTION_DOWNLOAD = "com.sameerasw.draft.service.ACTION_DOWNLOAD"
        const val ACTION_CANCEL = "com.sameerasw.draft.service.ACTION_CANCEL"
        const val EXTRA_TASK_ID = "extra_task_id"

        fun startDownload(context: Context, task: DownloadTask) {
            val repository = DownloadRepository.getInstance(context)
            repository.upsertTask(task)

            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_TASK_ID, task.id)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context, taskId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }
}
