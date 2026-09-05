package com.sameerasw.draft.data.downloader

import android.content.Context
import android.util.Log
import com.sameerasw.draft.data.model.DownloadTask
import com.sameerasw.draft.data.model.ParsedVideoInfo
import com.sameerasw.draft.data.model.VideoFormatItem
import com.sameerasw.draft.data.repository.PreferencesManager
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.Locale

enum class EngineState {
    NOT_INITIALIZED,
    INITIALIZING,
    READY,
    ERROR
}

object YoutubeDLManager {

    private const val TAG = "YoutubeDLManager"

    private val initMutex = Mutex()
    private val _engineState = MutableStateFlow(EngineState.NOT_INITIALIZED)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()

    suspend fun init(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        if (_engineState.value == EngineState.READY) return@withContext Result.success(Unit)

        initMutex.withLock {
            if (_engineState.value == EngineState.READY) return@withContext Result.success(Unit)
            _engineState.value = EngineState.INITIALIZING
            _engineError.value = null

            try {
                val appContext = context.applicationContext
                YoutubeDL.getInstance().init(appContext)

                try {
                    FFmpeg.getInstance().init(appContext)
                } catch (e: Throwable) {
                    Log.w(TAG, "FFmpeg init warning (may still function)", e)
                }

                try {
                    Aria2c.getInstance().init(appContext)
                } catch (e: Throwable) {
                    Log.w(TAG, "Aria2c init warning", e)
                }

                _engineState.value = EngineState.READY
                Log.i(TAG, "YoutubeDL engine initialized successfully")
                Result.success(Unit)
            } catch (e: Throwable) {
                _engineState.value = EngineState.ERROR
                val message = e.localizedMessage ?: e.toString()
                _engineError.value = message
                Log.e(TAG, "YoutubeDL initialization failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun ensureInitialized(context: Context): Result<Unit> {
        if (_engineState.value == EngineState.READY) return Result.success(Unit)
        if (_engineState.value == EngineState.INITIALIZING) {
            var waited = 0
            while (_engineState.value == EngineState.INITIALIZING && waited < 100) {
                delay(150)
                waited++
            }
            if (_engineState.value == EngineState.READY) return Result.success(Unit)
            if (_engineState.value == EngineState.ERROR) {
                return Result.failure(IllegalStateException(_engineError.value ?: "Engine initialization failed"))
            }
        }
        return init(context)
    }

    suspend fun parseVideoInfo(context: Context, url: String): Result<ParsedVideoInfo> = withContext(Dispatchers.IO) {
        val initResult = ensureInitialized(context)
        if (initResult.isFailure) {
            val err = initResult.exceptionOrNull()?.localizedMessage ?: "Engine not ready"
            return@withContext Result.failure(IllegalStateException("Engine initialization failed: $err"))
        }

        try {
            withTimeout(35_000L) {
                runCatching {
                    val prefs = PreferencesManager.getInstance(context)
                    val request = YoutubeDLRequest(url).apply {
                        addOption("--dump-single-json")
                        addOption("--no-playlist")
                        addOption("-R", "1")
                        addOption("--socket-timeout", "10")
                        addOption("--no-warnings")
                        addOption("--no-check-certificates")
                        addOption("--geo-bypass")

                        if (prefs.forceIpv4.value) {
                            addOption("--force-ipv4")
                        }

                        if (prefs.proxyEnabled.value && prefs.proxyUrl.value.isNotBlank()) {
                            addOption("--proxy", prefs.proxyUrl.value)
                        }
                    }

                    val response = YoutubeDL.getInstance().execute(request)
                    val json = JSONObject(response.out)

                    val title = json.optString("title", "Untitled Video")
                    val uploader = json.optString("uploader", json.optString("channel", ""))
                    val durationSec = json.optLong("duration", 0L)
                    val durationText = formatDuration(durationSec)
                    val thumbnail = json.optString("thumbnail", "")

                    val formatList = mutableListOf<VideoFormatItem>()
                    val formatsArray = json.optJSONArray("formats")
                    if (formatsArray != null) {
                        val seenResolutions = mutableSetOf<String>()
                        for (i in 0 until formatsArray.length()) {
                            val formatObj = formatsArray.optJSONObject(i) ?: continue
                            val formatId = formatObj.optString("format_id", "")
                            val ext = formatObj.optString("ext", "mp4")
                            val vcodec = formatObj.optString("vcodec", "none")
                            val acodec = formatObj.optString("acodec", "none")
                            val height = formatObj.optInt("height", 0)
                            val note = formatObj.optString("format_note", "")

                            val isAudio = vcodec == "none" && acodec != "none"
                            val resolution = when {
                                height > 0 -> "${height}p"
                                note.isNotBlank() -> note
                                isAudio -> "Audio Only"
                                else -> ext.uppercase(Locale.ROOT)
                            }

                            if (resolution != "Audio Only" && height > 0) {
                                if (!seenResolutions.contains(resolution)) {
                                    seenResolutions.add(resolution)
                                    formatList.add(
                                        VideoFormatItem(
                                            formatId = formatId,
                                            formatNote = resolution,
                                            resolution = resolution,
                                            ext = ext,
                                            isAudio = false
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Sort resolutions descending (e.g. 1080p, 720p, 480p)
                    formatList.sortByDescending {
                        it.resolution.filter { char -> char.isDigit() }.toIntOrNull() ?: 0
                    }

                    ParsedVideoInfo(
                        url = url,
                        title = title,
                        uploader = uploader,
                        durationText = durationText,
                        thumbnailUrl = thumbnail,
                        formats = formatList
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Link parsing timed out (35s). Please check network/proxy settings or update yt-dlp core in Settings."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun download(
        context: Context,
        task: DownloadTask,
        outputDir: File,
        onProgress: (progress: Float, etaInSeconds: Long, line: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val initResult = ensureInitialized(context)
        if (initResult.isFailure) {
            val err = initResult.exceptionOrNull()?.localizedMessage ?: "Engine not ready"
            return@withContext Result.failure(IllegalStateException("Engine initialization failed: $err"))
        }

        runCatching {
            val prefs = com.sameerasw.draft.data.repository.PreferencesManager.getInstance(context)
            val targetDir = if (prefs.separateAudioVideo.value) {
                File(outputDir, if (task.isAudioOnly) "Audio" else "Video").also { if (!it.exists()) it.mkdirs() }
            } else {
                if (!outputDir.exists()) outputDir.mkdirs()
                outputDir
            }

            val subFolder = if (prefs.subDirectoryExtractor.value) "%(extractor)s/%(uploader)s/" else ""
            val templatePath = if (prefs.separateAudioVideo.value) {
                val mediaType = if (task.isAudioOnly) "Audio" else "Video"
                File(outputDir, "$mediaType/$subFolder%(title).100B.%(ext)s").absolutePath
            } else {
                File(outputDir, "$subFolder%(title).100B.%(ext)s").absolutePath
            }

            val request = YoutubeDLRequest(task.url).apply {
                addOption("-o", templatePath)
                if (!prefs.downloadPlaylist.value) {
                    addOption("--no-playlist")
                }
                addOption("--no-mtime")

                if (prefs.forceIpv4.value) {
                    addOption("--force-ipv4")
                }

                if (prefs.proxyEnabled.value && prefs.proxyUrl.value.isNotBlank()) {
                    addOption("--proxy", prefs.proxyUrl.value)
                }

                val rate = prefs.rateLimit.value
                if (rate.isNotBlank() && rate != "0") {
                    addOption("--limit-rate", rate)
                }

                if (prefs.downloaderType.value == com.sameerasw.draft.data.repository.DownloaderType.ARIA2C) {
                    val conns = prefs.concurrentConnections.value
                    addOption("--downloader", "libaria2c.so")
                    addOption("--downloader-args", "aria2c:-x $conns -s $conns -k 1M -j $conns")
                } else {
                    val conns = prefs.concurrentConnections.value
                    addOption("--concurrent-fragments", conns.toString())
                }

                if (prefs.embedThumbnail.value) {
                    addOption("--embed-thumbnail")
                    if (prefs.cropArtwork.value) {
                        addOption("--ppa", "ffmpeg: -c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
                    }
                }

                if (prefs.restrictFilenames.value) {
                    addOption("--restrict-filenames")
                }

                if (prefs.embedMetadata.value) {
                    addOption("--add-metadata")
                }

                if (prefs.sponsorBlock.value) {
                    addOption("--sponsorblock-remove", "all")
                }

                if (prefs.downloadArchive.value) {
                    val archiveFile = File(context.filesDir, "archive.txt")
                    addOption("--download-archive", archiveFile.absolutePath)
                }

                if (prefs.downloadSubtitles.value) {
                    if (prefs.autoSubtitles.value) {
                        addOption("--write-auto-subs")
                    }
                    val langs = prefs.subtitleLanguages.value
                    if (langs.isNotBlank() && langs != "all") {
                        addOption("--sub-langs", langs)
                    } else {
                        addOption("--all-subs")
                    }
                    if (prefs.embedSubtitles.value) {
                        addOption("--embed-subs")
                    } else {
                        addOption("--write-subs")
                    }
                }

                if (prefs.debugLog.value) {
                    addOption("--verbose")
                }

                if (task.isAudioOnly) {
                    addOption("-x")
                    val audioFmt = prefs.audioFormat.value.ifBlank { "mp3" }
                    addOption("--audio-format", audioFmt)
                    val audioQuality = prefs.audioQuality.value
                    if (audioQuality != "best") {
                        addOption("--audio-quality", "${audioQuality}K")
                    }
                } else {
                    val videoContainer = prefs.videoContainer.value
                    if (prefs.mergeToMkv.value || videoContainer == "mkv") {
                        addOption("--remux-video", "mkv")
                        addOption("--merge-output-format", "mkv")
                    } else if (videoContainer != "auto") {
                        addOption("--merge-output-format", videoContainer)
                    }

                    if (!task.formatNote.isNullOrBlank() && task.formatNote != "best") {
                        val height = task.formatNote.filter { it.isDigit() }
                        if (height.isNotEmpty()) {
                            addOption("-f", "bestvideo[height<=${height}]+bestaudio/best[height<=${height}]/best")
                        } else {
                            addOption("-f", "${task.formatNote}+bestaudio/best")
                        }
                    } else {
                        val maxRes = prefs.maxResolution.value
                        if (maxRes != "best" && maxRes.filter { it.isDigit() }.isNotEmpty()) {
                            val maxH = maxRes.filter { it.isDigit() }
                            addOption("-f", "bestvideo[height<=${maxH}]+bestaudio/best[height<=${maxH}]/best")
                        } else {
                            addOption("-f", "bestvideo+bestaudio/best")
                        }
                    }
                }
            }

            val response = YoutubeDL.getInstance().execute(request, task.id) { progress, eta, line ->
                onProgress(progress, eta, line)
            }

            Log.d(TAG, "Download finished. Out: ${response.out}")

            // Find downloaded file in output directory
            val searchDir = if (prefs.separateAudioVideo.value) {
                File(outputDir, if (task.isAudioOnly) "Audio" else "Video")
            } else outputDir
            val files = searchDir.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            val downloadedFile = files.maxByOrNull { it.lastModified() }
                ?: File(searchDir, "${task.title.ifBlank { "video" }}.${if (task.isAudioOnly) prefs.audioFormat.value else "mp4"}")

            downloadedFile
        }
    }

    suspend fun updateYoutubeDL(context: Context, channel: com.sameerasw.draft.data.repository.YtDlpChannel = com.sameerasw.draft.data.repository.YtDlpChannel.STABLE): Result<String> = withContext(Dispatchers.IO) {
        val initRes = ensureInitialized(context)
        if (initRes.isFailure) {
            return@withContext Result.failure(initRes.exceptionOrNull() ?: IllegalStateException("Engine not initialized"))
        }
        runCatching {
            val updateChannel = when (channel) {
                com.sameerasw.draft.data.repository.YtDlpChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                com.sameerasw.draft.data.repository.YtDlpChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
            }
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                context.applicationContext,
                updateChannel
            )
            val current = YoutubeDL.getInstance().version(context.applicationContext) ?: "v2024+"
            "Status: $status (Version: $current)"
        }
    }

    fun cancel(taskId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to destroy process $taskId", e)
        }
    }

    fun getVersion(context: Context): String {
        return when (_engineState.value) {
            EngineState.READY -> {
                try {
                    YoutubeDL.getInstance().version(context) ?: "v2024+"
                } catch (_: Exception) {
                    "v2024+"
                }
            }
            EngineState.INITIALIZING -> "Initializing…"
            EngineState.ERROR -> "Init Error"
            EngineState.NOT_INITIALIZED -> "Starting…"
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val mins = seconds / 60
        val remainingSecs = seconds % 60
        val hours = mins / 60
        val remainingMins = mins % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remainingMins, remainingSecs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", remainingMins, remainingSecs)
        }
    }
}
