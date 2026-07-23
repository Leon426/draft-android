package com.sameerasw.draft

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sameerasw.draft.data.git.SyncWorker
import com.sameerasw.draft.ui.components.GitSetupSheet
import com.sameerasw.draft.ui.components.NoteCard
import com.sameerasw.draft.ui.theme.DraftTheme
import com.sameerasw.draft.viewmodel.NoteListViewModel
import com.sameerasw.draft.viewmodel.SyncUiState
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: NoteListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule periodic background sync
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "draft_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        )

        setContent {
            DraftTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    onOpenNote = { noteId ->
                        val intent = Intent(this, EditorActivity::class.java).apply {
                            putExtra(EditorActivity.EXTRA_NOTE_ID, noteId)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.isConfigured.value) {
            viewModel.loadNotes()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onOpenNote: (String) -> Unit
) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val isConfigured by viewModel.isConfigured.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isCloneLoading by viewModel.isCloneLoading.collectAsState()
    val cloneError by viewModel.cloneError.collectAsState()

    var showSetupSheet by remember { mutableStateOf(false) }
    val isRefreshing = syncState is SyncUiState.Syncing
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.note_list_title)) },
                actions = {
                    IconButton(onClick = { showSetupSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        },
        floatingActionButton = {
            if (isConfigured) {
                FloatingActionButton(
                    onClick = {
                        viewModel.createNote { newNote ->
                            onOpenNote(newNote.id)
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.note_new_button))
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.syncNow() },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            if (notes.isEmpty()) {
                Text(
                    text = if (isConfigured) "No notes found. Tap + to create one." else "Tap settings icon to configure repository.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(notes, key = { it.filePath }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) }
                        )
                    }
                }
            }
        }

        if (!isConfigured || showSetupSheet) {
            GitSetupSheet(
                initialRepoUrl = viewModel.gitSyncManager.getRepoUrl(),
                initialPat = viewModel.gitSyncManager.getPat(),
                initialAuthorName = viewModel.gitSyncManager.getAuthorName(),
                initialAuthorEmail = viewModel.gitSyncManager.getAuthorEmail(),
                isLoading = isCloneLoading,
                errorMessage = cloneError,
                onSaveAndClone = { url, pat, authorName, authorEmail ->
                    viewModel.cloneRepo(url, pat, authorName, authorEmail)
                },
                onDismissRequest = {
                    if (isConfigured) {
                        showSetupSheet = false
                    }
                }
            )
        }
    }
}