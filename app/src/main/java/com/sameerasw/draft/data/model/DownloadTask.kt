package com.sameerasw.draft.data.model

enum class TaskStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELED
}

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String = "",
    val uploader: String = "",
    val duration: String = "",
    val thumbnailUrl: String = "",
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val status: TaskStatus = TaskStatus.QUEUED,
    val errorMsg: String? = null,
    val filePath: String? = null,
    val fileSize: String? = null,
    val isAudioOnly: Boolean = false,
    val formatNote: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class VideoFormatItem(
    val formatId: String,
    val formatNote: String,
    val resolution: String,
    val ext: String,
    val isAudio: Boolean = false
)

data class ParsedVideoInfo(
    val url: String,
    val title: String,
    val uploader: String = "",
    val durationText: String = "",
    val thumbnailUrl: String = "",
    val formats: List<VideoFormatItem> = emptyList()
)
