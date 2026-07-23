package com.sameerasw.draft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sameerasw.draft.ui.theme.DraftTheme
import com.sameerasw.draft.viewmodel.NoteEditorViewModel

class EditorActivity : ComponentActivity() {

    private val viewModel: NoteEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: ""

        setContent {
            DraftTheme {
                NoteEditorScreen(
                    viewModel = viewModel,
                    noteId = noteId,
                    onBack = {
                        viewModel.syncAndExit { finish() }
                    }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveCurrentNote()
    }

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    noteId: String,
    onBack: () -> Unit
) {
    val title by viewModel.title.collectAsState()
    val body by viewModel.body.collectAsState()

    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { stringResource(R.string.editor_untitled) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteCurrentNote { onBack() } }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_note))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.onTitleChange(it) },
                label = { Text("Title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = body,
                onValueChange = { viewModel.onBodyChange(it) },
                label = { Text("Content (Markdown)") },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                )
            )
        }
    }
}
