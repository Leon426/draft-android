package com.sameerasw.draft

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VeloApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()

        appScope.launch {
            val initRes = com.sameerasw.draft.data.downloader.YoutubeDLManager.init(this@VeloApp)
            if (initRes.isSuccess) {
                val prefs = com.sameerasw.draft.data.repository.PreferencesManager.getInstance(this@VeloApp)
                if (prefs.ytdlpAutoUpdate.value) {
                    val sp = getSharedPreferences("velo_preferences", Context.MODE_PRIVATE)
                    val lastCheck = sp.getLong("last_ytdlp_update_check", 0L)
                    val interval = prefs.ytdlpUpdateInterval.value
                    if (System.currentTimeMillis() - lastCheck > interval) {
                        sp.edit().putLong("last_ytdlp_update_check", System.currentTimeMillis()).apply()
                        com.sameerasw.draft.data.downloader.YoutubeDLManager.updateYoutubeDL(this@VeloApp, prefs.ytdlpChannel.value)
                    }
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_DOWNLOAD,
                "Video Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time download progress for videos and audio"
                setShowBadge(false)
            }
            val completedChannel = NotificationChannel(
                CHANNEL_DOWNLOAD_COMPLETED,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a download has completed"
                setShowBadge(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(progressChannel)
            manager.createNotificationChannel(completedChannel)
        }
    }

    companion object {
        lateinit var instance: VeloApp
            private set
        const val CHANNEL_DOWNLOAD = "velo_download_channel"
        const val CHANNEL_DOWNLOAD_COMPLETED = "velo_download_completed_channel"
    }
}
