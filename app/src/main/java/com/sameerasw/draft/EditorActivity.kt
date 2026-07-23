package com.sameerasw.draft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sameerasw.draft.ui.components.toolbar.EssentialsFloatingToolbar
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val exitAlwaysScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)
    val pageTitle = title.ifBlank { stringResource(R.string.editor_untitled) }

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
                EssentialsFloatingToolbar(
                    title = pageTitle,
                    onBackClick = onBack,
                    scrollBehavior = exitAlwaysScrollBehavior,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f),
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                viewModel.deleteCurrentNote { onBack() }
                            },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.large,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_delete_24),
                                contentDescription = stringResource(R.string.delete_note)
                            )
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarHeight + 16.dp, bottom = 120.dp, start = 16.dp, end = 16.dp)
                ) {
                    val lineColor = MaterialTheme.colorScheme.primary

                    androidx.compose.material3.TextField(
                        value = title,
                        onValueChange = { viewModel.onTitleChange(it) },
                        placeholder = {
                            Text(
                                text = "Title",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        ) {
                            val path = Path()
                            val wavelength = 12.dp.toPx()
                            val amplitude = 1.5.dp.toPx()
                            val centerY = size.height / 2
                            val points = (size.width / 2f).toInt().coerceAtLeast(10)

                            path.moveTo(0f, centerY)
                            for (i in 0..points) {
                                val x = (i.toFloat() / points) * size.width
                                val y = centerY + (kotlin.math.sin((x / wavelength) * 2 * Math.PI) * amplitude).toFloat()
                                path.lineTo(x, y)
                            }

                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }
                    }

                    androidx.compose.material3.TextField(
                        value = body,
                        onValueChange = { viewModel.onBodyChange(it) },
                        placeholder = {
                            Text(
                                text = "Draft here ...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}
