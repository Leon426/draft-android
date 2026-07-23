package com.sameerasw.draft.data.repository

import com.sameerasw.draft.data.git.GitSyncManager
import com.sameerasw.draft.data.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class NoteRepository(private val gitSyncManager: GitSyncManager) {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: Flow<List<Note>> = _notes.asStateFlow()

    suspend fun loadNotes() = withContext(Dispatchers.IO) {
        val repoDir = gitSyncManager.localRepoDir
        if (!repoDir.exists()) {
            _notes.value = emptyList()
            return@withContext
        }

        val files = repoDir.listFiles { file -> file.isFile && file.name.endsWith(".md") } ?: emptyArray()
        val loadedNotes = files.mapNotNull { parseNote(it) }.sortedByDescending { it.updatedAt }
        _notes.value = loadedNotes
    }

    suspend fun getNoteById(id: String): Note? = withContext(Dispatchers.IO) {
        val repoDir = gitSyncManager.localRepoDir
        if (!repoDir.exists()) return@withContext null

        val files = repoDir.listFiles { file -> file.isFile && file.name.endsWith(".md") } ?: emptyArray()
        files.mapNotNull { parseNote(it) }.find { it.id == id }
    }

    suspend fun createNote(): Note = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000
        val title = "Untitled Note"
        val fileName = "Note-$timestamp.md"
        val file = File(gitSyncManager.localRepoDir, fileName)

        val content = "---\n" +
                "id: \"$id\"\n" +
                "title: \"$title\"\n" +
                "updated_at: $timestamp\n" +
                "---\n\n"

        file.writeText(content)
        val note = Note(
            id = id,
            title = title,
            body = "",
            updatedAt = timestamp,
            filePath = file.absolutePath
        )
        loadNotes()
        note
    }

    suspend fun saveNote(note: Note, newTitle: String, newBody: String) = withContext(Dispatchers.IO) {
        val file = File(note.filePath)
        val timestamp = System.currentTimeMillis() / 1000

        val content = "---\n" +
                "id: \"${note.id}\"\n" +
                "title: \"$newTitle\"\n" +
                "updated_at: $timestamp\n" +
                "---\n" +
                newBody

        file.writeText(content)
        loadNotes()
    }

    suspend fun deleteNote(note: Note) = withContext(Dispatchers.IO) {
        val file = File(note.filePath)
        if (file.exists()) {
            file.delete()
        }
        loadNotes()
    }

    private fun parseNote(file: File): Note? {
        return try {
            val content = file.readText()
            val frontmatterRegex = Regex("^\\s*---\\r?\\n(.*?)\\r?\\n---\\r?\\n?(.*)$", RegexOption.DOT_MATCHES_ALL)
            val match = frontmatterRegex.find(content)

            if (match != null) {
                val header = match.groupValues[1]
                val body = match.groupValues[2]

                var id = UUID.randomUUID().toString()
                var title = file.nameWithoutExtension
                var updatedAt = file.lastModified() / 1000

                header.lines().forEach { line ->
                    val parts = line.split(":", limit = 2).map { it.trim() }
                    if (parts.size == 2) {
                        val key = parts[0]
                        val value = parts[1].removeSurrounding("\"")
                        when (key) {
                            "id" -> id = value
                            "title" -> title = value
                            "updated_at" -> value.toLongOrNull()?.let { updatedAt = it }
                        }
                    }
                }

                val isUnsynced = gitSyncManager.isFileUnsynced(file.absolutePath)
                Note(id = id, title = title, body = body, updatedAt = updatedAt, filePath = file.absolutePath, isUnsynced = isUnsynced)
            } else {
                val title = file.nameWithoutExtension
                val isUnsynced = gitSyncManager.isFileUnsynced(file.absolutePath)
                Note(id = UUID.randomUUID().toString(), title = title, body = content, updatedAt = file.lastModified() / 1000, filePath = file.absolutePath, isUnsynced = isUnsynced)
            }
        } catch (e: Exception) {
            null
        }
    }
}
