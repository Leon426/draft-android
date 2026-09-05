package com.sameerasw.draft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sameerasw.draft.data.downloader.EngineState
import com.sameerasw.draft.data.downloader.YoutubeDLManager
import com.sameerasw.draft.data.model.DownloadTask
import com.sameerasw.draft.data.model.ParsedVideoInfo
import com.sameerasw.draft.data.model.TaskStatus
import com.sameerasw.draft.data.repository.AppThemeMode
import com.sameerasw.draft.data.repository.DownloadRepository
import com.sameerasw.draft.data.repository.PreferencesManager
import com.sameerasw.draft.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.getInstance(application)
    val tasks: StateFlow<List<DownloadTask>> = repository.tasks

    val preferences = PreferencesManager.getInstance(application)
    val isBlurEnabled: StateFlow<Boolean> = preferences.uiBlurEnabled

    fun setBlurEnabled(enabled: Boolean) {
        preferences.setUiBlurEnabled(enabled)
    }

    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    private val _parsedInfo = MutableStateFlow<ParsedVideoInfo?>(null)
    val parsedInfo: StateFlow<ParsedVideoInfo?> = _parsedInfo.asStateFlow()

    private val _parseError = MutableStateFlow<String?>(null)
    val parseError: StateFlow<String?> = _parseError.asStateFlow()

    val engineState: StateFlow<EngineState> = YoutubeDLManager.engineState
    val engineError: StateFlow<String?> = YoutubeDLManager.engineError

    private val _isUpdatingYtDlp = MutableStateFlow(false)
    val isUpdatingYtDlp: StateFlow<Boolean> = _isUpdatingYtDlp.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    val ytdlpVersion: String
        get() = YoutubeDLManager.getVersion(getApplication())

    fun retryInit() {
        viewModelScope.launch {
            YoutubeDLManager.init(getApplication())
        }
    }

    fun updateYtDlp() {
        if (_isUpdatingYtDlp.value) return
        viewModelScope.launch {
            _isUpdatingYtDlp.value = true
            val channel = preferences.ytdlpChannel.value
            val channelName = if (channel == com.sameerasw.draft.data.repository.YtDlpChannel.NIGHTLY) "Nightly" else "Stable"
            _updateMessage.value = "Checking for yt-dlp updates ($channelName)…"
            YoutubeDLManager.updateYoutubeDL(getApplication(), channel).fold(
                onSuccess = { msg ->
                    _updateMessage.value = msg
                    _isUpdatingYtDlp.value = false
                },
                onFailure = { err ->
                    _updateMessage.value = "Update failed: ${err.localizedMessage ?: "Unknown"}"
                    _isUpdatingYtDlp.value = false
                }
            )
        }
    }

    fun clearUpdateMessage() {
        _updateMessage.value = null
    }

    fun clearTempCache(): Long {
        return preferences.clearTempFiles()
    }

    fun parseUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _parseError.value = "Please enter a valid URL"
            return
        }

        viewModelScope.launch {
            _isParsing.value = true
            _parseError.value = null
            _parsedInfo.value = null

            try {
                YoutubeDLManager.parseVideoInfo(getApplication(), trimmed).fold(
                    onSuccess = { info ->
                        _parsedInfo.value = info
                    },
                    onFailure = { error ->
                        _parseError.value = error.localizedMessage ?: "Failed to parse link"
                    }
                )
            } catch (e: Exception) {
                _parseError.value = e.localizedMessage ?: "An unexpected error occurred during parsing"
            } finally {
                _isParsing.value = false
            }
        }
    }

    fun clearParsedInfo() {
        _parsedInfo.value = null
        _parseError.value = null
        _isParsing.value = false
    }

    fun enqueueDownload(
        url: String,
        title: String,
        uploader: String,
        duration: String,
        thumbnailUrl: String,
        isAudioOnly: Boolean,
        formatNote: String?
    ) {
        val taskId = UUID.randomUUID().toString()
        val task = DownloadTask(
            id = taskId,
            url = url,
            title = title.ifBlank { "Video $taskId" },
            uploader = uploader,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            progress = 0f,
            status = TaskStatus.QUEUED,
            isAudioOnly = isAudioOnly,
            formatNote = formatNote
        )

        DownloadService.startDownload(getApplication(), task)
        clearParsedInfo()
    }

    fun cancelTask(taskId: String) {
        DownloadService.cancelDownload(getApplication(), taskId)
    }

    fun deleteTask(taskId: String) {
        repository.deleteTask(taskId)
    }

    fun getDownloadDirectory(): File {
        return File(preferences.downloadDirectory.value)
    }

    fun setDownloadDirectory(path: String) {
        preferences.setDownloadDirectory(path)
        repository.setDownloadDirectory(path)
    }
}
