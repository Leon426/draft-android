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

    /** Upper bound (ms) for how long a single link parse may run before auto-stop. */
    private const val parseTimeoutMs = 25_000L

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

    /**
     * A dedicated (non-download) process id used to identify an in-flight
     * "parse/peek" subprocess so it can be cleanly cancelled by the user.
     */
    private const val PARSE_PROCESS_PREFIX = "parse"

    private val parseToken = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * True once [cancelParse] has been requested for the current parse so the
     * parse loop can bail out cleanly even while a blocking subprocess is ending.
     */
    private val _parseCancelled = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Stops the currently running link-parse subprocess (if any). */
    fun cancelParse() {
        _parseCancelled.set(true)
        val token = parseToken.get()
        if (token != 0L) {
            cancel("$PARSE_PROCESS_PREFIX$token")
        }
    }

    class ParseSuspendedException : Exception("Link parsing was stopped")

    suspend fun parseVideoInfo(context: Context, url: String): Result<ParsedVideoInfo> = withContext(Dispatchers.IO) {
        val initResult = ensureInitialized(context)
        if (initResult.isFailure) {
            val err = initResult.exceptionOrNull()?.localizedMessage ?: "Engine not ready"
            return@withContext Result.failure(IllegalStateException("Engine initialization failed: $err"))
        }

        // Mint a fresh token for this parse and clear the cancelled flag.
        val token = parseToken.incrementAndGet()
        val processId = "$PARSE_PROCESS_PREFIX$token"
        _parseCancelled.set(false)

        try {
            val result = withTimeout(parseTimeoutMs) {
                runCatching {
                    fun checkActive() {
                        // The parse subprocess is blocking, so cancellation must be
                        // signalled through cancelParse() (which destroys the process
                        // and flips this flag) rather than via coroutine cancellation.
                        if (_parseCancelled.get()) {
                            throw ParseSuspendedException()
                        }
                    }

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

                    checkActive()
                    val response = YoutubeDL.getInstance().execute(request, processId, null)
                    checkActive()

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

            // Drain the cancelled flag: either we stopped or the parse finished.
            if (_parseCancelled.get()) {
                return@withContext Result.failure(ParseSuspendedException())
            }

            result.onFailure { e ->
                if (e is ParseSuspendedException) {
                    Log.i(TAG, "Link parsing cancelled by user.")
                }
            }
            result
        } catch (e: TimeoutCancellationException) {
            // A ViewModel job cancellation also lands here once the blocking
            // subprocess finishes/dies, so check whether the user stopped first.
            val wasUserCancelled = _parseCancelled.get()
            cancelParse()
            if (wasUserCancelled) {
                Result.failure(ParseSuspendedException())
            } else {
                Result.failure(IllegalStateException("Link parsing is taking too long and was stopped. Please check network / proxy settings or update the yt-dlp core, then try again."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Make sure the subprocess is gone even on early exit.
            cancel(processId)
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

        val prefs = com.sameerasw.draft.data.repository.PreferencesManager.getInstance(context)
        val preferredAria2c = prefs.downloaderType.value == com.sameerasw.draft.data.repository.DownloaderType.ARIA2C

        // Prepare the destination folder and filename template once, so both the
        // original attempt and the automatic retry write to the same location.
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

        fun buildRequest(useAria2c: Boolean, hardenedRetry: Boolean): YoutubeDLRequest {
            return YoutubeDLRequest(task.url).apply {
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

                if (useAria2c) {
                    val conns = prefs.concurrentConnections.value
                    addOption("--downloader", "libaria2c.so")
                    addOption("--downloader-args", "aria2c:-x $conns -s $conns -k 1M -j $conns")
                } else {
                    val conns = prefs.concurrentConnections.value
                    addOption("--concurrent-fragments", conns.toString())
                }

                // Only the automatic retry is hardened. Some sites (e.g. PornHub)
                // hand out short-lived HLS/HTTP links that die with HTTP 410, so
                // give the engine extra network retries on the fresh attempt.
                if (hardenedRetry) {
                    addOption("--retries", "10")
                    addOption("--fragment-retries", "10")
                    addOption("--retry-sleep", "3")
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
        }

        fun runAttempt(useAria2c: Boolean, hardenedRetry: Boolean): Result<File> {
            return runCatching {
                val request = buildRequest(useAria2c, hardenedRetry)
                val response = YoutubeDL.getInstance().execute(request, task.id) { progress, eta, line ->
                    onProgress(progress, eta, line)
                }

                Log.d(TAG, "Download finished. Out: ${response.out}")

                // Find downloaded file in output directory
                val searchDir = if (prefs.separateAudioVideo.value) {
                    File(outputDir, if (task.isAudioOnly) "Audio" else "Video")
                } else outputDir
                val files = searchDir.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                files.maxByOrNull { it.lastModified() }
                    ?: File(searchDir, "${task.title.ifBlank { "video" }}.${if (task.isAudioOnly) prefs.audioFormat.value else "mp4"}")
            }
        }

        val firstAttempt = runAttempt(preferredAria2c, hardenedRetry = false)
        if (firstAttempt.isSuccess) return@withContext firstAttempt

        val firstError = firstAttempt.exceptionOrNull()
        // Never auto-retry when the user cancelled the task.
        if (firstError is YoutubeDL.CanceledException) return@withContext firstAttempt

        val firstMessage = firstError?.message ?: ""
        if (!isTransientDownloadFailure(firstMessage)) {
            return@withContext Result.failure(
                IllegalStateException("Download failed: ${summarizeFailure(firstMessage)}")
            )
        }

        Log.w(TAG, "Download attempt 1 failed with a transient error; retrying with the native downloader. $firstMessage")

        // Automatic recovery: re-extract the page once and retry with yt-dlp's
        // built-in downloader. HLS manifests/fragments are far more reliable
        // there than under the external aria2c engine, and the fresh extraction
        // also re-validates short-lived CDN links (HTTP 410 Gone) that broke the
        // first attempt.
        val retryAttempt = runAttempt(useAria2c = false, hardenedRetry = true)
        if (retryAttempt.isSuccess) return@withContext retryAttempt

        val retryError = retryAttempt.exceptionOrNull()
        if (retryError is YoutubeDL.CanceledException) return@withContext retryAttempt

        val retryMessage = retryError?.message ?: ""
        val friendly = StringBuilder("Download failed: ").append(summarizeFailure(firstMessage))
        if (retryMessage.isNotBlank()) {
            friendly.append("\nAutomatic retry also failed: ").append(summarizeFailure(retryMessage))
        }
        if (preferredAria2c) {
            friendly.append("\nTip: the aria2c accelerator failed — the Downloader Engine can be switched to Native in Settings.")
        }
        friendly.append("\nTip: if this keeps failing, update the yt-dlp core (Settings → Check for yt-dlp Updates) and retry.")
        Result.failure(IllegalStateException(friendly.toString()))
    }

    private fun isTransientDownloadFailure(message: String): Boolean {
        val m = message.lowercase(Locale.ROOT)
        return listOf(
            "http error 4", "http error 5", "404", "403", "410", "429", "503",
            "gone", "m3u8", "manifest", "fragment", "aria2c", "external downloader",
            "unable to download", "timed out", "timeout", "connection reset",
            "connection refused", "network is unreachable", "name or service not known",
            "temporary failure", "server error", "econnreset", "econnrefused",
            "handshake", "certificate"
        ).any { m.contains(it) }
    }

    private fun summarizeFailure(message: String): String {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return "unknown error"
        val lines = trimmed.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val meaningful = lines.lastOrNull { !it.startsWith("WARNING:") } ?: lines.lastOrNull() ?: trimmed
        return meaningful.removePrefix("ERROR:").trim().take(240).ifBlank { "unknown error" }
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
