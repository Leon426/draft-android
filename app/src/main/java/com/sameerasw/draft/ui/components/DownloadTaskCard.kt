package com.sameerasw.draft.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.sameerasw.draft.R
import com.sameerasw.draft.data.model.DownloadTask
import com.sameerasw.draft.data.model.TaskStatus
import com.sameerasw.draft.ui.components.menus.SegmentedDropdownMenu
import com.sameerasw.draft.ui.components.menus.SegmentedDropdownMenuItem
import com.sameerasw.draft.utils.HapticUtil
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showMenu by remember { mutableStateOf(false) }
    var expanded by remember(task.id) { mutableStateOf(false) }

    val isFinished = task.status == TaskStatus.COMPLETED && !task.filePath.isNullOrBlank()
    val isActive = task.status == TaskStatus.DOWNLOADING || task.status == TaskStatus.QUEUED

    fun openFile() {
        task.filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                try {
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val mime = if (task.isAudioOnly) "audio/*" else "video/*"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open file"))
                } catch (e: Exception) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(file), if (task.isAudioOnly) "audio/*" else "video/*")
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            }
        }
    }

    fun shareFile() {
        task.filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                try {
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val mime = if (task.isAudioOnly) "audio/*" else "video/*"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share file"))
                } catch (_: Exception) {}
            }
        }
    }

    Box(modifier = modifier) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceBright)
                .combinedClickable(
                    onClick = {
                        when {
                            isFinished -> {
                                HapticUtil.performUIHaptic(view)
                                openFile()
                            }
                            isActive -> {
                                // Tap a running/queued card to expand or collapse
                                // the detailed progress view.
                                HapticUtil.performUIHaptic(view)
                                expanded = !expanded
                            }
                        }
                    },
                    onLongClick = {
                        HapticUtil.performVirtualKeyHaptic(view)
                        showMenu = true
                    }
                ),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = task.thumbnailUrl,
                            contentDescription = task.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                id = if (task.isAudioOnly) R.drawable.rounded_music_note_24 else R.drawable.rounded_video_library_24
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    text = task.title.ifBlank { "Downloading Video" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    when (task.status) {
                        TaskStatus.DOWNLOADING -> {
                            LinearProgressIndicator(
                                progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${task.progress.toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (task.speed.isNotBlank()) {
                                    Text(
                                        text = " • ${task.speed}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (task.eta.isNotBlank()) {
                                    Text(
                                        text = " • ${task.eta}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        TaskStatus.QUEUED -> {
                            Text(
                                text = "Queued…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TaskStatus.COMPLETED -> {
                            val details = listOfNotNull(
                                task.uploader.ifBlank { null },
                                task.fileSize?.ifBlank { null },
                                task.duration.ifBlank { null }
                            ).joinToString(" • ")
                            Text(
                                text = details.ifBlank { "Completed" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TaskStatus.FAILED -> {
                            Text(
                                text = task.errorMsg ?: "Download failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TaskStatus.CANCELED -> {
                            Text(
                                text = "Canceled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ----------------------------------------------------------
                    // Expanded detailed progress view (tap the active card to
                    // toggle). Shows live download metrics + source metadata.
                    // ----------------------------------------------------------
                    AnimatedVisibility(
                        visible = expanded && isActive,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (task.status == TaskStatus.DOWNLOADING) {
                                // Progress metrics
                                DetailStatRow(label = "Downloaded", value = if (task.totalSize.isNotBlank()) {
                                    "${task.downloadedSize.ifBlank { "0" }} / ${task.totalSize}"
                                } else "—")
                                DetailStatRow(label = "Speed", value = task.speed.ifBlank { "—" })
                                DetailStatRow(label = "Time remaining", value = task.eta.ifBlank { "—" })
                                DetailStatRow(label = "Elapsed", value = formatElapsed(task.elapsedSec))
                                DetailStatRow(
                                    label = "Stage",
                                    value = task.stage.ifBlank { "Downloading…" },
                                    emphasized = task.stage.isNotBlank()
                                )
                            } else {
                                Text(
                                    text = "Waiting for an available download slot…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Static metadata rows (all states)
                            DetailStatRow(
                                label = if (task.isAudioOnly) "Type" else "Video format",
                                value = formatNoteLabel(task)
                            )
                            hostOf(task.url).takeIf { it.isNotBlank() }?.let { host ->
                                DetailStatRow(label = "Source", value = host)
                            }
                            if (task.uploader.isNotBlank()) {
                                DetailStatRow(label = "Channel", value = task.uploader)
                            }
                            if (task.duration.isNotBlank()) {
                                DetailStatRow(label = "Duration", value = task.duration)
                            }

                            // Expand hint
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap again to collapse",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            },
            trailingContent = {
                if (task.status == TaskStatus.DOWNLOADING) {
                    IconButton(onClick = {
                        HapticUtil.performUIHaptic(view)
                        onCancel()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_close_24),
                            contentDescription = "Cancel Download",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (isFinished) {
                    IconButton(onClick = {
                        HapticUtil.performUIHaptic(view)
                        openFile()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_play_arrow_24),
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        )

        SegmentedDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (isFinished) {
                SegmentedDropdownMenuItem(
                    text = { Text(stringResource(R.string.open_file)) },
                    onClick = {
                        showMenu = false
                        openFile()
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_play_arrow_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                SegmentedDropdownMenuItem(
                    text = { Text(stringResource(R.string.share_file)) },
                    onClick = {
                        showMenu = false
                        shareFile()
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_share_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
            SegmentedDropdownMenuItem(
                text = { Text(stringResource(R.string.delete_task)) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_delete_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@Composable
private fun DetailStatRow(
    label: String,
    value: String,
    emphasized: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

private fun formatNoteLabel(task: DownloadTask): String {
    val note = task.formatNote
    return when {
        task.isAudioOnly -> "Audio only"
        note.isNullOrBlank() || note == "best" -> "Best quality"
        note == "bestaudio" -> "Audio only"
        else -> if (note.all { it.isDigit() || it == 'p' }) "Video $note" else note
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val sec = totalSeconds.coerceAtLeast(0L)
    val hours = sec / 3600
    val minutes = (sec % 3600) / 60
    val seconds = sec % 60
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun hostOf(url: String): String {
    return runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")
}
