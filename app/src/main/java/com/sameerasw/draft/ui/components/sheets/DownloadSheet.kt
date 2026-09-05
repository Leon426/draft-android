package com.sameerasw.draft.ui.components.sheets

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sameerasw.draft.R
import com.sameerasw.draft.ui.components.containers.RoundedCardContainer
import com.sameerasw.draft.utils.HapticUtil
import com.sameerasw.draft.viewmodel.DownloadViewModel
import kotlinx.coroutines.delay

private const val MS_PER_TICK = 250L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadSheet(
    viewModel: DownloadViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var inputUrl by remember { mutableStateOf("") }
    val isParsing by viewModel.isParsing.collectAsState()
    val parsedInfo by viewModel.parsedInfo.collectAsState()
    val parseError by viewModel.parseError.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val engineError by viewModel.engineError.collectAsState()

    var showAdvancedFormats by remember { mutableStateOf(false) }

    // Elapsed-seconds readout shown next to the stop button while parsing.
    // -1 means "not started yet"; 0 is the instant parsing begins.
    var parseSecs by remember { mutableStateOf(-1) }
    val parseTick = isParsing
    LaunchedEffect(parseTick) {
        parseSecs = if (isParsing) 0 else -1
        if (isParsing) {
            val start = System.currentTimeMillis()
            while (viewModel.isParsing.value) {
                parseSecs = ((System.currentTimeMillis() - start) / 1000L).toInt()
                delay(MS_PER_TICK)
            }
            parseSecs = -1
        }
    }

    fun stopParsing() {
        viewModel.cancelParse()
    }

    // NOTE: There is intentionally no auto-paste of the clipboard URL here.
    // Pasting is user-driven and not automatic to give the user control over what
    // gets pasted and parsed.

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearParsedInfo()
            onDismissRequest()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.new_download),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (engineState == com.sameerasw.draft.data.downloader.EngineState.INITIALIZING) {
                item {
                    RoundedCardContainer(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LoadingIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Preparing yt-dlp core engine… Parsing will begin automatically once ready.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } else if (engineState == com.sameerasw.draft.data.downloader.EngineState.ERROR) {
                item {
                    RoundedCardContainer(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Engine Error: ${engineError ?: "Failed to initialize"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.retryInit() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Retry Engine Initialization")
                            }
                        }
                    }
                }
            }

            item {
                RoundedCardContainer(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.url_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                HapticUtil.performUIHaptic(view)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val text = clipboard?.primaryClip?.let { clip ->
                                    if (clip.itemCount > 0) clip.getItemAt(0)?.text?.toString()?.trim() ?: "" else ""
                                } ?: ""
                                if (text.isNotBlank()) {
                                    inputUrl = text
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_content_paste_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.paste_clipboard))
                        }
                    }

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        enabled = !isParsing,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.paste_or_enter_url),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        singleLine = true,
                        trailingIcon = {
                            if (inputUrl.isNotBlank() && !isParsing) {
                                IconButton(onClick = { inputUrl = "" }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.rounded_close_24),
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isParsing) {
                        // Parsing is in progress — show a stop control instead of a
                        // duplicate Parse button, and keep the sheet interactive.
                        Button(
                            onClick = {
                                HapticUtil.performVirtualKeyHaptic(view)
                                stopParsing()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_close_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (parseSecs >= 0) "Stop parsing (${parseSecs}s)" else "Stop parsing…"
                            )
                        }
                    } else {
                        // Idle — show the normal Parse button.
                        Button(
                            onClick = {
                                HapticUtil.performUIHaptic(view)
                                viewModel.parseUrl(inputUrl)
                            },
                            enabled = inputUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_link_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.parse_link))
                        }
                    }
                    }
                }
            }

            if (parseError != null) {
                item {
                    Text(
                        text = parseError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            val info = parsedInfo
            if (info != null) {
                item {
                    RoundedCardContainer(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceBright
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (info.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = info.thumbnailUrl,
                                    contentDescription = info.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }

                            Text(
                                text = info.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (info.uploader.isNotBlank() || info.durationText.isNotBlank()) {
                                Text(
                                    text = listOf(info.uploader, info.durationText).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        HapticUtil.performUIHaptic(view)
                                        viewModel.enqueueDownload(
                                            url = info.url,
                                            title = info.title,
                                            uploader = info.uploader,
                                            duration = info.durationText,
                                            thumbnailUrl = info.thumbnailUrl,
                                            isAudioOnly = false,
                                            formatNote = "best"
                                        )
                                        onDismissRequest()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.rounded_download_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.download_best_video),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        HapticUtil.performUIHaptic(view)
                                        viewModel.enqueueDownload(
                                            url = info.url,
                                            title = info.title,
                                            uploader = info.uploader,
                                            duration = info.durationText,
                                            thumbnailUrl = info.thumbnailUrl,
                                            isAudioOnly = true,
                                            formatNote = "bestaudio"
                                        )
                                        onDismissRequest()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.rounded_music_note_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.download_best_audio),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (info.formats.isNotEmpty()) {
                                TextButton(
                                    onClick = { showAdvancedFormats = !showAdvancedFormats },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text(
                                        text = if (showAdvancedFormats) "Hide Formats" else stringResource(R.string.more_formats)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = showAdvancedFormats,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        info.formats.forEach { format ->
                                            ListItem(
                                                headlineContent = {
                                                    Text(
                                                        text = format.resolution,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                },
                                                supportingContent = {
                                                    Text(
                                                        text = format.ext.uppercase(),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                },
                                                trailingContent = {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.rounded_download_24),
                                                        contentDescription = "Download ${format.resolution}",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(MaterialTheme.shapes.extraSmall)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                    .clickable {
                                                        HapticUtil.performUIHaptic(view)
                                                        viewModel.enqueueDownload(
                                                            url = info.url,
                                                            title = "${info.title} (${format.resolution})",
                                                            uploader = info.uploader,
                                                            duration = info.durationText,
                                                            thumbnailUrl = info.thumbnailUrl,
                                                            isAudioOnly = false,
                                                            formatNote = format.resolution
                                                        )
                                                        onDismissRequest()
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
