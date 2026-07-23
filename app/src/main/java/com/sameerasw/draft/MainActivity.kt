package com.sameerasw.draft

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )

    val isRefreshing = syncState is SyncUiState.Syncing
    val pullToRefreshState = rememberPullToRefreshState()
    val exitAlwaysScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val contentPadding = PaddingValues(
        top = statusBarHeight,
        bottom = 150.dp,
        start = 16.dp,
        end = 16.dp
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {}
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                com.sameerasw.draft.ui.components.toolbar.EssentialsFloatingToolbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f),
                    selectedIndex = pagerState.currentPage,
                    items = listOf(
                        com.sameerasw.draft.ui.components.toolbar.ToolbarItem(
                            iconRes = R.drawable.rounded_home_24,
                            labelRes = R.string.tab_drafts,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        ),
                        com.sameerasw.draft.ui.components.toolbar.ToolbarItem(
                            iconRes = R.drawable.rounded_settings_24,
                            labelRes = R.string.tab_settings,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        )
                    ),
                    scrollBehavior = exitAlwaysScrollBehavior,
                    floatingActionButton = {
                        if (isConfigured && pagerState.currentPage == 0) {
                            FloatingActionButton(
                                onClick = {
                                    viewModel.createNote { newNote ->
                                        onOpenNote(newNote.id)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = MaterialTheme.shapes.large,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_add_24),
                                    contentDescription = stringResource(R.string.note_new_button)
                                )
                            }
                        }
                    }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.syncNow() },
                                state = pullToRefreshState,
                                modifier = Modifier.fillMaxSize(),
                                indicator = {
                                    if (isRefreshing) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = statusBarHeight + 16.dp),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            LoadingIndicator()
                                        }
                                    }
                                }
                            ) {
                                if (notes.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(contentPadding),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isConfigured) "No notes found. Tap + to create one." else "Swipe to Settings to configure repository.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = contentPadding
                                    ) {
                                        item {
                                            Text(
                                                text = "My Notes",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp)
                                            )
                                        }

                                        item {
                                            com.sameerasw.draft.ui.components.containers.RoundedCardContainer{
                                                notes.forEach { note ->
                                                    NoteCard(
                                                        note = note,
                                                        onClick = { onOpenNote(note.id) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                isCloneLoading = isCloneLoading,
                                cloneError = cloneError,
                                contentPadding = contentPadding
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: NoteListViewModel,
    isCloneLoading: Boolean,
    cloneError: String?,
    contentPadding: PaddingValues
) {
    var repoUrl by remember { mutableStateOf(viewModel.gitSyncManager.getRepoUrl() ?: "") }
    var pat by remember { mutableStateOf(viewModel.gitSyncManager.getPat() ?: "") }
    var authorName by remember { mutableStateOf(viewModel.gitSyncManager.getAuthorName() ?: "") }
    var authorEmail by remember { mutableStateOf(viewModel.gitSyncManager.getAuthorEmail() ?: "") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Repository Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }

        item {
            com.sameerasw.draft.ui.components.containers.RoundedCardContainer(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        label = { Text("Repository URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    androidx.compose.material3.OutlinedTextField(
                        value = pat,
                        onValueChange = { pat = it },
                        label = { Text("Personal Access Token (PAT)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    androidx.compose.material3.OutlinedTextField(
                        value = authorName,
                        onValueChange = { authorName = it },
                        label = { Text("Author Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    androidx.compose.material3.OutlinedTextField(
                        value = authorEmail,
                        onValueChange = { authorEmail = it },
                        label = { Text("Author Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (cloneError != null) {
                        Text(
                            text = cloneError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    androidx.compose.material3.Button(
                        onClick = {
                            viewModel.cloneRepo(repoUrl, pat, authorName, authorEmail)
                        },
                        enabled = !isCloneLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(if (isCloneLoading) "Cloning..." else "Save & Sync Repository")
                    }
                }
            }
        }
    }
}