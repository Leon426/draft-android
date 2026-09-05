package com.sameerasw.draft.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.sameerasw.draft.data.model.DownloadTask
import com.sameerasw.draft.data.model.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DownloadRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("velo_downloads", Context.MODE_PRIVATE)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        try {
            val jsonStr = prefs.getString("tasks_history", "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadTask>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val statusStr = obj.optString("status", TaskStatus.COMPLETED.name)
                val status = runCatching { TaskStatus.valueOf(statusStr) }.getOrDefault(TaskStatus.COMPLETED)
                // If app was killed while downloading, mark as failed
                val finalStatus = if (status == TaskStatus.DOWNLOADING || status == TaskStatus.QUEUED) {
                    TaskStatus.FAILED
                } else status

                list.add(
                    DownloadTask(
                        id = obj.optString("id", ""),
                        url = obj.optString("url", ""),
                        title = obj.optString("title", ""),
                        uploader = obj.optString("uploader", ""),
                        duration = obj.optString("duration", ""),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        progress = obj.optDouble("progress", 100.0).toFloat(),
                        speed = obj.optString("speed", ""),
                        eta = obj.optString("eta", ""),
                        status = finalStatus,
                        errorMsg = obj.optString("errorMsg").ifEmpty { null },
                        filePath = obj.optString("filePath").ifEmpty { null },
                        fileSize = obj.optString("fileSize").ifEmpty { null },
                        isAudioOnly = obj.optBoolean("isAudioOnly", false),
                        formatNote = obj.optString("formatNote").ifEmpty { null },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _tasks.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error loading tasks", e)
        }
    }

    private fun persistTasks(list: List<DownloadTask>) {
        val prefsManager = PreferencesManager.getInstance(context)
        if (prefsManager.privateMode.value) {
            // Private mode: do not save download history to disk
            return
        }
        try {
            val array = JSONArray()
            list.forEach { task ->
                val obj = JSONObject().apply {
                    put("id", task.id)
                    put("url", task.url)
                    put("title", task.title)
                    put("uploader", task.uploader)
                    put("duration", task.duration)
                    put("thumbnailUrl", task.thumbnailUrl)
                    put("progress", task.progress.toDouble())
                    put("speed", task.speed)
                    put("eta", task.eta)
                    put("status", task.status.name)
                    put("errorMsg", task.errorMsg ?: "")
                    put("filePath", task.filePath ?: "")
                    put("fileSize", task.fileSize ?: "")
                    put("isAudioOnly", task.isAudioOnly)
                    put("formatNote", task.formatNote ?: "")
                    put("timestamp", task.timestamp)
                }
                array.put(obj)
            }
            prefs.edit().putString("tasks_history", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error saving tasks", e)
        }
    }

    fun upsertTask(task: DownloadTask) {
        _tasks.update { current ->
            val index = current.indexOfFirst { it.id == task.id }
            val updated = if (index != -1) {
                current.toMutableList().apply { set(index, task) }
            } else {
                listOf(task) + current
            }
            persistTasks(updated)
            updated
        }
    }

    fun deleteTask(taskId: String) {
        _tasks.update { current ->
            val updated = current.filterNot { it.id == taskId }
            persistTasks(updated)
            updated
        }
    }

    fun getDownloadDirectory(): File {
        val customPath = prefs.getString("custom_download_path", null)
        if (!customPath.isNullOrBlank()) {
            val dir = File(customPath)
            if (dir.exists() || dir.mkdirs()) {
                return dir
            }
        }
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val defaultDir = File(publicDownloads, "Velo")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir
    }

    fun setDownloadDirectory(path: String) {
        prefs.edit().putString("custom_download_path", path).apply()
    }

    companion object {
        @Volatile
        private var instance: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository {
            return instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
