package com.sameerasw.draft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sameerasw.draft.data.git.GitSyncManager
import com.sameerasw.draft.data.model.Note
import com.sameerasw.draft.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SyncUiState {
    object Idle : SyncUiState
    object Syncing : SyncUiState
    data class Error(val message: String) : SyncUiState
}

class NoteListViewModel(application: Application) : AndroidViewModel(application) {

    val gitSyncManager = GitSyncManager(application)
    val noteRepository = NoteRepository(gitSyncManager)

    val notes = noteRepository.notes

    private val _isConfigured = MutableStateFlow(gitSyncManager.isConfigured())
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val _isCloneLoading = MutableStateFlow(false)
    val isCloneLoading: StateFlow<Boolean> = _isCloneLoading.asStateFlow()

    private val _cloneError = MutableStateFlow<String?>(null)
    val cloneError: StateFlow<String?> = _cloneError.asStateFlow()

    private val prefs = application.getSharedPreferences("draft_settings", android.content.Context.MODE_PRIVATE)

    private val _isBlurEnabled = MutableStateFlow(prefs.getBoolean("enable_ui_blur", true))
    val isBlurEnabled: StateFlow<Boolean> = _isBlurEnabled.asStateFlow()

    fun setBlurEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enable_ui_blur", enabled).apply()
        _isBlurEnabled.value = enabled
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        if (gitSyncManager.isConfigured()) {
            loadNotes()
        } else {
            _isLoading.value = false
        }
    }

    fun loadNotes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                noteRepository.loadNotes()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cloneRepo(repoUrl: String, pat: String, authorName: String, authorEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCloneLoading.value = true
            _cloneError.value = null
            try {
                gitSyncManager.cloneRepo(repoUrl, pat, authorName, authorEmail)
                _isConfigured.value = true
                noteRepository.loadNotes()
            } catch (e: Exception) {
                _cloneError.value = e.localizedMessage ?: "Failed to clone repository"
            } finally {
                _isCloneLoading.value = false
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _syncState.value = SyncUiState.Syncing
            val result = gitSyncManager.sync()
            if (result.isSuccess) {
                noteRepository.loadNotes()
                _syncState.value = SyncUiState.Idle
            } else {
                _syncState.value = SyncUiState.Error(result.exceptionOrNull()?.localizedMessage ?: "Sync error")
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            val targetNote = noteRepository.getNoteById(noteId)
            if (targetNote != null) {
                noteRepository.deleteNote(targetNote)
                loadNotes()
            }
        }
    }

    fun createNote(onCreated: (Note) -> Unit) {
        viewModelScope.launch {
            val note = noteRepository.createNote()
            onCreated(note)
        }
    }
}
