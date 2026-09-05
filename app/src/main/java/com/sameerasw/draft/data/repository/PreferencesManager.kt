package com.sameerasw.draft.data.repository

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class DownloaderType {
    NATIVE,
    ARIA2C
}

enum class YtDlpChannel {
    STABLE,
    NIGHTLY
}

class PreferencesManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("velo_preferences", Context.MODE_PRIVATE)

    // --- Downloader & Acceleration ---
    private val _downloaderType = MutableStateFlow(
        runCatching { DownloaderType.valueOf(prefs.getString("downloader_type", if (prefs.getBoolean("aria2c_enabled", false)) DownloaderType.ARIA2C.name else DownloaderType.NATIVE.name) ?: DownloaderType.NATIVE.name) }
            .getOrDefault(DownloaderType.NATIVE)
    )
    val downloaderType: StateFlow<DownloaderType> = _downloaderType.asStateFlow()

    val aria2cEnabled: StateFlow<Boolean> get() = MutableStateFlow(_downloaderType.value == DownloaderType.ARIA2C).asStateFlow()

    private val _concurrentConnections = MutableStateFlow(prefs.getInt("concurrent_connections", 16))
    val concurrentConnections: StateFlow<Int> = _concurrentConnections.asStateFlow()

    private val _rateLimit = MutableStateFlow(prefs.getString("rate_limit", "0") ?: "0")
    val rateLimit: StateFlow<String> = _rateLimit.asStateFlow()

    private val _forceIpv4 = MutableStateFlow(prefs.getBoolean("force_ipv4", false))
    val forceIpv4: StateFlow<Boolean> = _forceIpv4.asStateFlow()

    private val _cellularDownload = MutableStateFlow(prefs.getBoolean("cellular_download", true))
    val cellularDownload: StateFlow<Boolean> = _cellularDownload.asStateFlow()

    private val _proxyEnabled = MutableStateFlow(prefs.getBoolean("proxy_enabled", false))
    val proxyEnabled: StateFlow<Boolean> = _proxyEnabled.asStateFlow()

    private val _proxyUrl = MutableStateFlow(prefs.getString("proxy_url", "http://127.0.0.1:7890") ?: "http://127.0.0.1:7890")
    val proxyUrl: StateFlow<String> = _proxyUrl.asStateFlow()

    // --- Engine & Updates ---
    private val _ytdlpChannel = MutableStateFlow(
        runCatching { YtDlpChannel.valueOf(prefs.getString("ytdlp_channel", YtDlpChannel.STABLE.name) ?: YtDlpChannel.STABLE.name) }
            .getOrDefault(YtDlpChannel.STABLE)
    )
    val ytdlpChannel: StateFlow<YtDlpChannel> = _ytdlpChannel.asStateFlow()

    private val _ytdlpAutoUpdate = MutableStateFlow(prefs.getBoolean("ytdlp_auto_update", false))
    val ytdlpAutoUpdate: StateFlow<Boolean> = _ytdlpAutoUpdate.asStateFlow()

    private val _ytdlpUpdateInterval = MutableStateFlow(prefs.getLong("ytdlp_update_interval", 604800000L)) // 7 days
    val ytdlpUpdateInterval: StateFlow<Long> = _ytdlpUpdateInterval.asStateFlow()

    // --- Format & Quality ---
    private val _maxResolution = MutableStateFlow(prefs.getString("max_resolution", "best") ?: "best")
    val maxResolution: StateFlow<String> = _maxResolution.asStateFlow()

    private val _videoContainer = MutableStateFlow(prefs.getString("video_container", "auto") ?: "auto")
    val videoContainer: StateFlow<String> = _videoContainer.asStateFlow()

    private val _audioFormat = MutableStateFlow(prefs.getString("audio_format", "mp3") ?: "mp3")
    val audioFormat: StateFlow<String> = _audioFormat.asStateFlow()

    private val _audioQuality = MutableStateFlow(prefs.getString("audio_quality", "best") ?: "best")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _mergeToMkv = MutableStateFlow(prefs.getBoolean("merge_to_mkv", false))
    val mergeToMkv: StateFlow<Boolean> = _mergeToMkv.asStateFlow()

    private val _downloadSubtitles = MutableStateFlow(prefs.getBoolean("download_subtitles", false))
    val downloadSubtitles: StateFlow<Boolean> = _downloadSubtitles.asStateFlow()

    private val _embedSubtitles = MutableStateFlow(prefs.getBoolean("embed_subtitles", true))
    val embedSubtitles: StateFlow<Boolean> = _embedSubtitles.asStateFlow()

    private val _autoSubtitles = MutableStateFlow(prefs.getBoolean("auto_subtitles", false))
    val autoSubtitles: StateFlow<Boolean> = _autoSubtitles.asStateFlow()

    private val _subtitleLanguages = MutableStateFlow(prefs.getString("subtitle_languages", "all") ?: "all")
    val subtitleLanguages: StateFlow<String> = _subtitleLanguages.asStateFlow()

    private val _embedThumbnail = MutableStateFlow(prefs.getBoolean("embed_thumbnail", true))
    val embedThumbnail: StateFlow<Boolean> = _embedThumbnail.asStateFlow()

    private val _cropArtwork = MutableStateFlow(prefs.getBoolean("crop_artwork", false))
    val cropArtwork: StateFlow<Boolean> = _cropArtwork.asStateFlow()

    private val _embedMetadata = MutableStateFlow(prefs.getBoolean("embed_metadata", true))
    val embedMetadata: StateFlow<Boolean> = _embedMetadata.asStateFlow()

    private val _restrictFilenames = MutableStateFlow(prefs.getBoolean("restrict_filenames", false))
    val restrictFilenames: StateFlow<Boolean> = _restrictFilenames.asStateFlow()

    // --- General Behaviors ---
    private val _downloadPlaylist = MutableStateFlow(prefs.getBoolean("download_playlist", true))
    val downloadPlaylist: StateFlow<Boolean> = _downloadPlaylist.asStateFlow()

    private val _downloadArchive = MutableStateFlow(prefs.getBoolean("download_archive", false))
    val downloadArchive: StateFlow<Boolean> = _downloadArchive.asStateFlow()

    private val _sponsorBlock = MutableStateFlow(prefs.getBoolean("sponsorblock", false))
    val sponsorBlock: StateFlow<Boolean> = _sponsorBlock.asStateFlow()

    private val _privateMode = MutableStateFlow(prefs.getBoolean("private_mode", false))
    val privateMode: StateFlow<Boolean> = _privateMode.asStateFlow()

    private val _debugLog = MutableStateFlow(prefs.getBoolean("debug_log", false))
    val debugLog: StateFlow<Boolean> = _debugLog.asStateFlow()

    // --- Storage & Files ---
    private val _separateAudioVideo = MutableStateFlow(prefs.getBoolean("separate_audio_video", true))
    val separateAudioVideo: StateFlow<Boolean> = _separateAudioVideo.asStateFlow()

    private val _subDirectoryExtractor = MutableStateFlow(prefs.getBoolean("sub_dir_extractor", false))
    val subDirectoryExtractor: StateFlow<Boolean> = _subDirectoryExtractor.asStateFlow()

    private val _downloadDirectory = MutableStateFlow(getSavedDownloadDirectory())
    val downloadDirectory: StateFlow<String> = _downloadDirectory.asStateFlow()

    // --- Appearance ---
    private val _themeMode = MutableStateFlow(
        runCatching { AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name) }
            .getOrDefault(AppThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _uiBlurEnabled = MutableStateFlow(prefs.getBoolean("ui_blur_enabled", true))
    val uiBlurEnabled: StateFlow<Boolean> = _uiBlurEnabled.asStateFlow()

    private fun getSavedDownloadDirectory(): String {
        val custom = prefs.getString("download_directory", null)
        if (!custom.isNullOrBlank()) {
            return custom
        }
        val defaultDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Velo")
        if (!defaultDir.exists()) defaultDir.mkdirs()
        return defaultDir.absolutePath
    }

    // Setters
    fun setDownloaderType(type: DownloaderType) {
        _downloaderType.value = type
        prefs.edit()
            .putString("downloader_type", type.name)
            .putBoolean("aria2c_enabled", type == DownloaderType.ARIA2C)
            .apply()
    }

    fun setConcurrentConnections(connections: Int) {
        _concurrentConnections.value = connections
        prefs.edit().putInt("concurrent_connections", connections).apply()
    }

    fun setRateLimit(limit: String) {
        _rateLimit.value = limit
        prefs.edit().putString("rate_limit", limit).apply()
    }

    fun setForceIpv4(enabled: Boolean) {
        _forceIpv4.value = enabled
        prefs.edit().putBoolean("force_ipv4", enabled).apply()
    }

    fun setCellularDownload(enabled: Boolean) {
        _cellularDownload.value = enabled
        prefs.edit().putBoolean("cellular_download", enabled).apply()
    }

    fun setYtdlpChannel(channel: YtDlpChannel) {
        _ytdlpChannel.value = channel
        prefs.edit().putString("ytdlp_channel", channel.name).apply()
    }

    fun setYtdlpAutoUpdate(enabled: Boolean) {
        _ytdlpAutoUpdate.value = enabled
        prefs.edit().putBoolean("ytdlp_auto_update", enabled).apply()
    }

    fun setYtdlpUpdateInterval(interval: Long) {
        _ytdlpUpdateInterval.value = interval
        prefs.edit().putLong("ytdlp_update_interval", interval).apply()
    }

    fun setMaxResolution(resolution: String) {
        _maxResolution.value = resolution
        prefs.edit().putString("max_resolution", resolution).apply()
    }

    fun setVideoContainer(container: String) {
        _videoContainer.value = container
        prefs.edit().putString("video_container", container).apply()
    }

    fun setAudioFormat(format: String) {
        _audioFormat.value = format
        prefs.edit().putString("audio_format", format).apply()
    }

    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
        prefs.edit().putString("audio_quality", quality).apply()
    }

    fun setMergeToMkv(enabled: Boolean) {
        _mergeToMkv.value = enabled
        prefs.edit().putBoolean("merge_to_mkv", enabled).apply()
    }

    fun setDownloadSubtitles(enabled: Boolean) {
        _downloadSubtitles.value = enabled
        prefs.edit().putBoolean("download_subtitles", enabled).apply()
    }

    fun setEmbedSubtitles(enabled: Boolean) {
        _embedSubtitles.value = enabled
        prefs.edit().putBoolean("embed_subtitles", enabled).apply()
    }

    fun setAutoSubtitles(enabled: Boolean) {
        _autoSubtitles.value = enabled
        prefs.edit().putBoolean("auto_subtitles", enabled).apply()
    }

    fun setSubtitleLanguages(languages: String) {
        _subtitleLanguages.value = languages
        prefs.edit().putString("subtitle_languages", languages).apply()
    }

    fun setEmbedThumbnail(enabled: Boolean) {
        _embedThumbnail.value = enabled
        prefs.edit().putBoolean("embed_thumbnail", enabled).apply()
    }

    fun setCropArtwork(enabled: Boolean) {
        _cropArtwork.value = enabled
        prefs.edit().putBoolean("crop_artwork", enabled).apply()
    }

    fun setEmbedMetadata(enabled: Boolean) {
        _embedMetadata.value = enabled
        prefs.edit().putBoolean("embed_metadata", enabled).apply()
    }

    fun setRestrictFilenames(enabled: Boolean) {
        _restrictFilenames.value = enabled
        prefs.edit().putBoolean("restrict_filenames", enabled).apply()
    }

    fun setDownloadPlaylist(enabled: Boolean) {
        _downloadPlaylist.value = enabled
        prefs.edit().putBoolean("download_playlist", enabled).apply()
    }

    fun setDownloadArchive(enabled: Boolean) {
        _downloadArchive.value = enabled
        prefs.edit().putBoolean("download_archive", enabled).apply()
    }

    fun setSponsorBlock(enabled: Boolean) {
        _sponsorBlock.value = enabled
        prefs.edit().putBoolean("sponsorblock", enabled).apply()
    }

    fun setPrivateMode(enabled: Boolean) {
        _privateMode.value = enabled
        prefs.edit().putBoolean("private_mode", enabled).apply()
    }

    fun setDebugLog(enabled: Boolean) {
        _debugLog.value = enabled
        prefs.edit().putBoolean("debug_log", enabled).apply()
    }

    fun setProxyEnabled(enabled: Boolean) {
        _proxyEnabled.value = enabled
        prefs.edit().putBoolean("proxy_enabled", enabled).apply()
    }

    fun setProxyUrl(url: String) {
        _proxyUrl.value = url
        prefs.edit().putString("proxy_url", url).apply()
    }

    fun setAria2cEnabled(enabled: Boolean) {
        setDownloaderType(if (enabled) DownloaderType.ARIA2C else DownloaderType.NATIVE)
    }

    fun setSeparateAudioVideo(enabled: Boolean) {
        _separateAudioVideo.value = enabled
        prefs.edit().putBoolean("separate_audio_video", enabled).apply()
    }

    fun setSubDirectoryExtractor(enabled: Boolean) {
        _subDirectoryExtractor.value = enabled
        prefs.edit().putBoolean("sub_dir_extractor", enabled).apply()
    }

    fun setDownloadDirectory(path: String) {
        _downloadDirectory.value = path
        prefs.edit().putString("download_directory", path).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
    }

    fun setUiBlurEnabled(enabled: Boolean) {
        _uiBlurEnabled.value = enabled
        prefs.edit().putBoolean("ui_blur_enabled", enabled).apply()
    }

    fun clearTempFiles(): Long {
        var freedBytes = 0L
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                freedBytes += file.length()
                file.deleteRecursively()
            }
            val downloadDir = File(downloadDirectory.value)
            downloadDir.listFiles { file -> file.name.endsWith(".part") || file.name.endsWith(".ytdl") }?.forEach { file ->
                freedBytes += file.length()
                file.delete()
            }
        } catch (_: Exception) {}
        return freedBytes
    }

    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
