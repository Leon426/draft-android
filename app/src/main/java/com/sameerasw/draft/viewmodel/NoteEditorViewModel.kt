package com.sameerasw.draft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sameerasw.draft.data.git.GitSyncManager
import com.sameerasw.draft.data.model.Note
import com.sameerasw.draft.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoteEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val gitSyncManager = GitSyncManager(application)
    private val noteRepository = NoteRepository(gitSyncManager)

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private var autoSaveJob: Job? = null

    fun loadNote(noteId: String) {
        viewModelScope.launch {
            val note = noteRepository.getNoteById(noteId)
            _currentNote.value = note
            if (note != null) {
                _title.value = note.title
                _body.value = com.sameerasw.draft.utils.MarkdownAutoFormat.formatExistingMarkdown(note.body)
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle
        triggerAutoSave()
    }

    fun onBodyChange(newBody: String) {
        _body.value = newBody
        triggerAutoSave()
    }

    private fun triggerAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1000)
            saveCurrentNote()
        }
    }

    private var isDeleted = false

    fun deleteCurrentNote(onDeleted: () -> Unit) {
        autoSaveJob?.cancel()
        isDeleted = true
        val note = _currentNote.value
        onDeleted()
        if (note != null) {
            viewModelScope.launch(Dispatchers.IO) {
                noteRepository.deleteNote(note)
                gitSyncManager.sync()
            }
        }
    }

    fun saveCurrentNote() {
        if (isDeleted) return
        val note = _currentNote.value ?: return
        if (note.title == _title.value && note.body == _body.value) return
        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.saveNote(note, _title.value, _body.value)
        }
    }

    fun syncAndExit(onComplete: () -> Unit) {
        autoSaveJob?.cancel()
        val note = _currentNote.value
        val titleVal = _title.value
        val bodyVal = _body.value
        onComplete()
        if (note != null) {
            viewModelScope.launch(Dispatchers.IO) {
                if (note.title != titleVal || note.body != bodyVal) {
                    noteRepository.saveNote(note, titleVal, bodyVal)
                }
                gitSyncManager.sync()
            }
        }
    }
}
