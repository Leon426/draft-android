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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.sameerasw.draft.utils.MarkdownAutoFormat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.sameerasw.draft.ui.modifiers.BlurDirection
import com.sameerasw.draft.ui.modifiers.progressiveBlur
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sameerasw.draft.ui.components.toolbar.EssentialsFloatingToolbar
import com.sameerasw.draft.ui.theme.DraftTheme
import com.sameerasw.draft.viewmodel.NoteEditorViewModel
import kotlinx.coroutines.delay

class EditorActivity : ComponentActivity() {

    private val viewModel: NoteEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: ""
        val isNewNote = intent.getBooleanExtra(EXTRA_IS_NEW_NOTE, false)

        setContent {
            DraftTheme {
                NoteEditorScreen(
                    viewModel = viewModel,
                    noteId = noteId,
                    isNewNote = isNewNote,
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
        const val EXTRA_IS_NEW_NOTE = "extra_is_new_note"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    noteId: String,
    isNewNote: Boolean,
    onBack: () -> Unit
) {
    val title by viewModel.title.collectAsState()
    val body by viewModel.body.collectAsState()

    var bodyTextFieldValue by remember { mutableStateOf(TextFieldValue(body)) }
    val bodyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(body) {
        if (body != bodyTextFieldValue.text) {
            bodyTextFieldValue = TextFieldValue(
                text = body,
                selection = TextRange(body.length)
            )
        }
    }

    val scrollState = rememberScrollState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(bodyTextFieldValue.selection.end) {
        try {
            bringIntoViewRequester.bringIntoView()
        } catch (_: Exception) {}
    }

    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
        if (isNewNote) {
            delay(200)
            bodyFocusRequester.requestFocus()
        }
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val exitAlwaysScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)
    val pageTitle = title.ifBlank { stringResource(R.string.editor_untitled) }

    val isBlurEnabled = remember {
        val prefs = viewModel.getApplication<android.app.Application>().getSharedPreferences("draft_settings", android.content.Context.MODE_PRIVATE)
        prefs.getBoolean("enable_ui_blur", true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {}
        ) { innerPadding ->
            val statusBarHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { statusBarHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .progressiveBlur(
                        blurRadius = if (isBlurEnabled) 40f else 0f,
                        height = statusBarHeightPx * 1.15f,
                        direction = BlurDirection.TOP
                    )
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

                val isKeyboardOpen = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .progressiveBlur(
                            blurRadius = if (isBlurEnabled && !isKeyboardOpen) 40f else 0f,
                            height = with(androidx.compose.ui.platform.LocalDensity.current) { 130.dp.toPx() },
                            direction = com.sameerasw.draft.ui.modifiers.BlurDirection.BOTTOM,
                            showGradientOverlay = !isKeyboardOpen
                        )
                        .verticalScroll(scrollState)
                        .padding(top = statusBarHeight, bottom = 400.dp, start = 16.dp, end = 16.dp)
                ) {
                    val lineColor = MaterialTheme.colorScheme.primary

                    TextField(
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
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
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
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }

                    TextField(
                        value = bodyTextFieldValue,
                        onValueChange = { newValue ->
                            val processed = MarkdownAutoFormat.processBodyChange(bodyTextFieldValue, newValue)
                            bodyTextFieldValue = processed
                            viewModel.onBodyChange(processed.text)
                        },
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
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 800.dp)
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .focusRequester(bodyFocusRequester),
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
