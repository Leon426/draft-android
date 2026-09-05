package com.sameerasw.draft.ui.components.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sameerasw.draft.R
import com.sameerasw.draft.data.downloader.EngineState
import com.sameerasw.draft.data.repository.AppThemeMode
import com.sameerasw.draft.data.repository.DownloaderType
import com.sameerasw.draft.data.repository.YtDlpChannel
import com.sameerasw.draft.ui.components.containers.RoundedCardContainer
import com.sameerasw.draft.ui.components.dialogs.ChoiceOption
import com.sameerasw.draft.ui.components.dialogs.SingleChoiceDialog
import com.sameerasw.draft.ui.components.dialogs.TextInputDialog
import com.sameerasw.draft.utils.DeviceUtils
import com.sameerasw.draft.utils.HapticUtil
import com.sameerasw.draft.utils.LanguageUtil
import com.sameerasw.draft.viewmodel.DownloadViewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    viewModel: DownloadViewModel,
    contentPadding: PaddingValues,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val preferences = viewModel.preferences

    // Downloader & Acceleration States
    val downloaderType by preferences.downloaderType.collectAsState()
    val concurrentConnections by preferences.concurrentConnections.collectAsState()
    val rateLimit by preferences.rateLimit.collectAsState()
    val forceIpv4 by preferences.forceIpv4.collectAsState()
    val cellularDownload by preferences.cellularDownload.collectAsState()
    val proxyEnabled by preferences.proxyEnabled.collectAsState()
    val proxyUrl by preferences.proxyUrl.collectAsState()

    // Engine & Updates States
    val engineState by viewModel.engineState.collectAsState()
    val engineError by viewModel.engineError.collectAsState()
    val isUpdatingYtDlp by viewModel.isUpdatingYtDlp.collectAsState()
    val updateMessage by viewModel.updateMessage.collectAsState()
    val ytdlpChannel by preferences.ytdlpChannel.collectAsState()
    val ytdlpAutoUpdate by preferences.ytdlpAutoUpdate.collectAsState()
    val ytdlpUpdateInterval by preferences.ytdlpUpdateInterval.collectAsState()

    // Format & Quality States
    val maxResolution by preferences.maxResolution.collectAsState()
    val videoContainer by preferences.videoContainer.collectAsState()
    val audioFormat by preferences.audioFormat.collectAsState()
    val audioQuality by preferences.audioQuality.collectAsState()
    val mergeToMkv by preferences.mergeToMkv.collectAsState()
    val downloadSubtitles by preferences.downloadSubtitles.collectAsState()
    val embedSubtitles by preferences.embedSubtitles.collectAsState()
    val autoSubtitles by preferences.autoSubtitles.collectAsState()
    val subtitleLanguages by preferences.subtitleLanguages.collectAsState()
    val embedThumbnail by preferences.embedThumbnail.collectAsState()
    val cropArtwork by preferences.cropArtwork.collectAsState()
    val embedMetadata by preferences.embedMetadata.collectAsState()
    val restrictFilenames by preferences.restrictFilenames.collectAsState()

    // General Behaviors States
    val downloadPlaylist by preferences.downloadPlaylist.collectAsState()
    val downloadArchive by preferences.downloadArchive.collectAsState()
    val sponsorBlock by preferences.sponsorBlock.collectAsState()
    val privateMode by preferences.privateMode.collectAsState()
    val debugLog by preferences.debugLog.collectAsState()

    // Storage States
    val separateAudioVideo by preferences.separateAudioVideo.collectAsState()
    val subDirectoryExtractor by preferences.subDirectoryExtractor.collectAsState()
    val downloadDirectory by preferences.downloadDirectory.collectAsState()

    // Appearance States
    val themeMode by preferences.themeMode.collectAsState()
    val dynamicColor by preferences.dynamicColor.collectAsState()
    val uiBlurEnabled by preferences.uiBlurEnabled.collectAsState()
    val isBlurProblematic = remember { DeviceUtils.isBlurProblematicDevice() }

    // Dialog Visibilities
    var showDownloaderDialog by remember { mutableStateOf(false) }
    var showConnectionsDialog by remember { mutableStateOf(false) }
    var showRateLimitDialog by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }
    var showAutoUpdateDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showVideoContainerDialog by remember { mutableStateOf(false) }
    var showAudioFormatDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showSubtitleLangsDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}

            val resolved = getDirectoryPathFromUri(uri) ?: uri.toString()
            preferences.setDownloadDirectory(resolved)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // SECTION 1: ENGINE & VERSION CHANNEL
        // ==========================================
        item {
            Text(
                text = stringResource(R.string.sec_engine),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
            )
        }

        item {
            RoundedCardContainer(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                // Engine Status
                val statusText = when (engineState) {
                    EngineState.READY -> "Ready (${viewModel.ytdlpVersion})"
                    EngineState.INITIALIZING -> "Initializing (unpacking engine)…"
                    EngineState.ERROR -> "Init Error: ${engineError ?: "Failed"}"
                    EngineState.NOT_INITIALIZED -> "Starting…"
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.ytdlp_core_engine)) },
                    supportingContent = {
                        Text(
                            text = statusText,
                            color = if (engineState == EngineState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_info_24),
                            contentDescription = null,
                            tint = if (engineState == EngineState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        if (engineState == EngineState.INITIALIZING) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (engineState == EngineState.ERROR) {
                            IconButton(onClick = { viewModel.retryInit() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_refresh_24),
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Update Channel (Stable vs Nightly)
                val channelLabel = when (ytdlpChannel) {
                    YtDlpChannel.STABLE -> stringResource(R.string.channel_stable)
                    YtDlpChannel.NIGHTLY -> stringResource(R.string.channel_nightly)
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.update_channel)) },
                    supportingContent = { Text(channelLabel) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_settings_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_open_in_new_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showChannelDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Check for Updates
                ListItem(
                    headlineContent = { Text(stringResource(R.string.check_ytdlp_updates)) },
                    supportingContent = {
                        Text(updateMessage ?: stringResource(R.string.check_ytdlp_updates_desc))
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_refresh_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        if (isUpdatingYtDlp) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isUpdatingYtDlp) {
                            HapticUtil.performUIHaptic(view)
                            viewModel.updateYtDlp()
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Auto-update Frequency
                val intervalLabel = if (!ytdlpAutoUpdate) {
                    stringResource(R.string.interval_disabled)
                } else when (ytdlpUpdateInterval) {
                    86400000L -> stringResource(R.string.interval_daily)
                    604800000L -> stringResource(R.string.interval_weekly)
                    else -> stringResource(R.string.interval_monthly)
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.auto_update_interval)) },
                    supportingContent = { Text(intervalLabel) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_auto_awesome_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showAutoUpdateDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )
            }
        }

        // ==========================================
        // SECTION 2: DOWNLOADER & ACCELERATION
        // ==========================================
        item {
            Text(
                text = stringResource(R.string.sec_network),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
            )
        }

        item {
            RoundedCardContainer(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                // Downloader Engine (Native vs Aria2c)
                val engineLabel = when (downloaderType) {
                    DownloaderType.NATIVE -> stringResource(R.string.downloader_native)
                    DownloaderType.ARIA2C -> stringResource(R.string.downloader_aria2c)
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.downloader_engine)) },
                    supportingContent = { Text(engineLabel) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_download_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showDownloaderDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Concurrent Connections
                ListItem(
                    headlineContent = { Text(stringResource(R.string.concurrent_connections)) },
                    supportingContent = { Text("$concurrentConnections " + stringResource(R.string.concurrent_connections_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_bolt_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showConnectionsDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Rate Limit
                val rateLimitLabel = if (rateLimit == "0" || rateLimit.isBlank()) {
                    stringResource(R.string.rate_unlimited)
                } else "$rateLimit/s"

                ListItem(
                    headlineContent = { Text(stringResource(R.string.rate_limit)) },
                    supportingContent = { Text(rateLimitLabel) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_bolt_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showRateLimitDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Force IPv4
                ListItem(
                    headlineContent = { Text(stringResource(R.string.force_ipv4)) },
                    supportingContent = { Text(stringResource(R.string.force_ipv4_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_wifi_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = forceIpv4,
                            onCheckedChange = { preferences.setForceIpv4(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Download over Cellular
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cellular_download)) },
                    supportingContent = { Text(stringResource(R.string.cellular_download_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_wifi_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = cellularDownload,
                            onCheckedChange = { preferences.setCellularDownload(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Network Proxy
                ListItem(
                    headlineContent = { Text(stringResource(R.string.network_proxy)) },
                    supportingContent = { Text(if (proxyEnabled) proxyUrl else stringResource(R.string.network_proxy_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_link_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = proxyEnabled,
                            onCheckedChange = { preferences.setProxyEnabled(it) }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showProxyDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )
            }
        }

        // ==========================================
        // SECTION 3: FORMAT & QUALITY
        // ==========================================
        item {
            Text(
                text = stringResource(R.string.sec_download_preferences),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
            )
        }

        item {
            RoundedCardContainer(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                // Preferred Video Resolution
                val resLabel = if (maxResolution == "best") "Highest / Best" else "${maxResolution}p"
                ListItem(
                    headlineContent = { Text(stringResource(R.string.max_resolution)) },
                    supportingContent = { Text(resLabel) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_video_library_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showResolutionDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Preferred Video Container
                ListItem(
                    headlineContent = { Text(stringResource(R.string.video_container)) },
                    supportingContent = { Text(videoContainer.uppercase()) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_video_library_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showVideoContainerDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Remux to MKV
                ListItem(
                    headlineContent = { Text(stringResource(R.string.merge_to_mkv)) },
                    supportingContent = { Text(stringResource(R.string.merge_to_mkv_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_folder_copy_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = mergeToMkv,
                            onCheckedChange = { preferences.setMergeToMkv(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Audio Format
                ListItem(
                    headlineContent = { Text(stringResource(R.string.audio_format)) },
                    supportingContent = { Text(audioFormat.uppercase()) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_music_note_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showAudioFormatDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Audio Quality
                val audioQualityLabel = if (audioQuality == "best") "Best / VBR" else "${audioQuality} kbps"
                ListItem(
                    headlineContent = { Text(stringResource(R.string.audio_quality)) },
                    supportingContent = { Text(audioQualityLabel) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_music_note_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showAudioQualityDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Download Subtitles Switch
                ListItem(
                    headlineContent = { Text(stringResource(R.string.download_subtitles)) },
                    supportingContent = { Text(stringResource(R.string.download_subtitles_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_translate_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = downloadSubtitles,
                            onCheckedChange = { preferences.setDownloadSubtitles(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Embed Subtitles into Video
                if (downloadSubtitles) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.embed_subtitles)) },
                        supportingContent = { Text(stringResource(R.string.embed_subtitles_desc)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_translate_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = embedSubtitles,
                                onCheckedChange = { preferences.setEmbedSubtitles(it) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.auto_subtitles)) },
                        supportingContent = { Text(stringResource(R.string.auto_subtitles_desc)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_auto_awesome_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = autoSubtitles,
                                onCheckedChange = { preferences.setAutoSubtitles(it) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.subtitle_langs)) },
                        supportingContent = { Text(subtitleLanguages) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_translate_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                HapticUtil.performUIHaptic(view)
                                showSubtitleLangsDialog = true
                            },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                    )
                }
            }
        }

        // ==========================================
        // SECTION 4: GENERAL DOWNLOAD BEHAVIORS
        // ==========================================
        item {
            Text(
                text = stringResource(R.string.sec_general_behaviors),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
            )
        }

        item {
            RoundedCardContainer(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                // Embed Thumbnail
                ListItem(
                    headlineContent = { Text(stringResource(R.string.embed_thumbnail)) },
                    supportingContent = { Text(stringResource(R.string.embed_thumbnail_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_image_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = embedThumbnail,
                            onCheckedChange = { preferences.setEmbedThumbnail(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Crop Artwork
                if (embedThumbnail) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.crop_artwork)) },
                        supportingContent = { Text(stringResource(R.string.crop_artwork_desc)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_image_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = cropArtwork,
                                onCheckedChange = { preferences.setCropArtwork(it) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                    )
                }

                // Embed Metadata
                ListItem(
                    headlineContent = { Text(stringResource(R.string.embed_metadata)) },
                    supportingContent = { Text(stringResource(R.string.embed_metadata_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_title_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = embedMetadata,
                            onCheckedChange = { preferences.setEmbedMetadata(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Restrict Filenames
                ListItem(
                    headlineContent = { Text(stringResource(R.string.restrict_filenames)) },
                    supportingContent = { Text(stringResource(R.string.restrict_filenames_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_sticky_note_2_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = restrictFilenames,
                            onCheckedChange = { preferences.setRestrictFilenames(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Download Playlist
                ListItem(
                    headlineContent = { Text(stringResource(R.string.download_playlist)) },
                    supportingContent = { Text(stringResource(R.string.download_playlist_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_video_library_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = downloadPlaylist,
                            onCheckedChange = { preferences.setDownloadPlaylist(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Download Archive
                ListItem(
                    headlineContent = { Text(stringResource(R.string.download_archive)) },
                    supportingContent = { Text(stringResource(R.string.download_archive_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_sticky_note_2_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = downloadArchive,
                            onCheckedChange = { preferences.setDownloadArchive(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // SponsorBlock
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sponsorblock)) },
                    supportingContent = { Text(stringResource(R.string.sponsorblock_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_close_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = sponsorBlock,
                            onCheckedChange = { preferences.setSponsorBlock(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Private Mode
                ListItem(
                    headlineContent = { Text(stringResource(R.string.private_mode)) },
                    supportingContent = { Text(stringResource(R.string.private_mode_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = if (privateMode) R.drawable.rounded_visibility_off_24 else R.drawable.rounded_visibility_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = privateMode,
                            onCheckedChange = { preferences.setPrivateMode(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Debug Log
                ListItem(
                    headlineContent = { Text(stringResource(R.string.debug_log)) },
                    supportingContent = { Text(stringResource(R.string.debug_log_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_info_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = debugLog,
                            onCheckedChange = { preferences.setDebugLog(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )
            }
        }

        // ==========================================
        // SECTION 5: STORAGE & DIRECTORIES
        // ==========================================
        item {
            Text(
                text = stringResource(R.string.sec_storage),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
            )
        }

        item {
            RoundedCardContainer(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                // Storage Location
                ListItem(
                    headlineContent = { Text(stringResource(R.string.storage_location)) },
                    supportingContent = { Text(downloadDirectory) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_folder_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_open_in_new_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            folderPickerLauncher.launch(null)
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Separate Audio & Video Subfolders
                ListItem(
                    headlineContent = { Text(stringResource(R.string.separate_folders)) },
                    supportingContent = { Text(stringResource(R.string.separate_folders_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_folder_copy_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = separateAudioVideo,
                            onCheckedChange = { preferences.setSeparateAudioVideo(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Subdirectory by Extractor / Author
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sub_dir_extractor)) },
                    supportingContent = { Text(stringResource(R.string.sub_dir_extractor_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_folder_copy_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = subDirectoryExtractor,
                            onCheckedChange = { preferences.setSubDirectoryExtractor(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Clear Temp Cache
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_cache)) },
                    supportingContent = { Text(stringResource(R.string.clear_cache_desc)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_cleaning_services_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            val freed = viewModel.clearTempCache()
                            val formatted = formatBytes(freed)
                            Toast.makeText(context, "Cleaned $formatted", Toast.LENGTH_SHORT).show()
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )
            }
        }

        // ==========================================
        // SECTION 6: APPEARANCE & INTERACTION
        // ==========================================
        item {
            Text(
                text = stringResource(R.string.sec_appearance),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
            )
        }

        item {
            RoundedCardContainer(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                // Theme Mode
                val themeLabel = when (themeMode) {
                    AppThemeMode.SYSTEM -> stringResource(R.string.follow_system)
                    AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_theme)) },
                    supportingContent = { Text(themeLabel) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_palette_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showThemeDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Display Language
                val currentLanguageName = remember(showLanguageDialog) { LanguageUtil.getCurrentLanguageDisplayName() }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_language)) },
                    supportingContent = { Text(currentLanguageName) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_translate_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_open_in_new_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            showLanguageDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )

                // Dynamic Color
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                        supportingContent = { Text(stringResource(R.string.dynamic_color_desc)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_auto_awesome_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { preferences.setDynamicColor(it) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                    )
                }

                // Progressive Blur
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ui_blur)) },
                    supportingContent = {
                        Text(if (isBlurProblematic) "Disabled on unsupported device" else stringResource(R.string.ui_blur_desc))
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = if (uiBlurEnabled) R.drawable.rounded_blur_on_24 else R.drawable.rounded_blur_off_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = uiBlurEnabled && !isBlurProblematic,
                            onCheckedChange = { preferences.setUiBlurEnabled(it) },
                            enabled = !isBlurProblematic
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )
            }
        }

        // ==========================================
        // SECTION 7: ABOUT
        // ==========================================
        item {
            Text(
                text = stringResource(R.string.sec_about),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
            )
        }

        item {
            RoundedCardContainer(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_velo)) },
                    supportingContent = { Text("v1.0 • " + stringResource(R.string.about_subtitle)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_video_library_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_open_in_new_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            onOpenAbout()
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                )
            }
        }
    }

    // ==========================================
    // DIALOGS
    // ==========================================

    // 1. Downloader Engine Dialog
    if (showDownloaderDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.downloader_engine),
            options = listOf(
                ChoiceOption(
                    value = DownloaderType.NATIVE,
                    title = stringResource(R.string.downloader_native),
                    subtitle = "Standard lightweight downloader built into yt-dlp"
                ),
                ChoiceOption(
                    value = DownloaderType.ARIA2C,
                    title = stringResource(R.string.downloader_aria2c),
                    subtitle = "High-speed multi-threaded acceleration engine"
                )
            ),
            selectedValue = downloaderType,
            onSelect = { preferences.setDownloaderType(it) },
            onDismissRequest = { showDownloaderDialog = false }
        )
    }

    // 2. Concurrent Connections Dialog
    if (showConnectionsDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.concurrent_connections),
            options = listOf(
                ChoiceOption(1, stringResource(R.string.threads_1)),
                ChoiceOption(4, stringResource(R.string.threads_4)),
                ChoiceOption(8, stringResource(R.string.threads_8)),
                ChoiceOption(16, stringResource(R.string.threads_16))
            ),
            selectedValue = concurrentConnections,
            onSelect = { preferences.setConcurrentConnections(it) },
            onDismissRequest = { showConnectionsDialog = false }
        )
    }

    // 3. Rate Limit Dialog
    if (showRateLimitDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.rate_limit),
            options = listOf(
                ChoiceOption("0", stringResource(R.string.rate_unlimited)),
                ChoiceOption("500K", "500 KB/s"),
                ChoiceOption("1M", "1 MB/s"),
                ChoiceOption("2M", "2 MB/s"),
                ChoiceOption("5M", "5 MB/s"),
                ChoiceOption("10M", "10 MB/s")
            ),
            selectedValue = rateLimit,
            onSelect = { preferences.setRateLimit(it) },
            onDismissRequest = { showRateLimitDialog = false }
        )
    }

    // 4. Update Channel Dialog (Stable vs Nightly)
    if (showChannelDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.update_channel),
            options = listOf(
                ChoiceOption(
                    value = YtDlpChannel.STABLE,
                    title = stringResource(R.string.channel_stable),
                    subtitle = "Official release branch, thoroughly tested"
                ),
                ChoiceOption(
                    value = YtDlpChannel.NIGHTLY,
                    title = stringResource(R.string.channel_nightly),
                    subtitle = "Daily builds with immediate fixes for YouTube and other extractors"
                )
            ),
            selectedValue = ytdlpChannel,
            onSelect = { preferences.setYtdlpChannel(it) },
            onDismissRequest = { showChannelDialog = false }
        )
    }

    // 5. Auto Update Frequency Dialog
    if (showAutoUpdateDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.auto_update_interval),
            options = listOf(
                ChoiceOption(0L, stringResource(R.string.interval_disabled)),
                ChoiceOption(86400000L, stringResource(R.string.interval_daily)),
                ChoiceOption(604800000L, stringResource(R.string.interval_weekly)),
                ChoiceOption(2592000000L, stringResource(R.string.interval_monthly))
            ),
            selectedValue = if (!ytdlpAutoUpdate) 0L else ytdlpUpdateInterval,
            onSelect = { interval ->
                if (interval == 0L) {
                    preferences.setYtdlpAutoUpdate(false)
                } else {
                    preferences.setYtdlpAutoUpdate(true)
                    preferences.setYtdlpUpdateInterval(interval)
                }
            },
            onDismissRequest = { showAutoUpdateDialog = false }
        )
    }

    // 6. Resolution Dialog
    if (showResolutionDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.max_resolution),
            options = listOf(
                ChoiceOption("best", "Highest Quality / Best"),
                ChoiceOption("2160", "4K (2160p)"),
                ChoiceOption("1440", "2K (1440p)"),
                ChoiceOption("1080", "Full HD (1080p)"),
                ChoiceOption("720", "HD (720p)"),
                ChoiceOption("480", "SD (480p)"),
                ChoiceOption("360", "360p")
            ),
            selectedValue = maxResolution,
            onSelect = { preferences.setMaxResolution(it) },
            onDismissRequest = { showResolutionDialog = false }
        )
    }

    // 7. Video Container Dialog
    if (showVideoContainerDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.video_container),
            options = listOf(
                ChoiceOption("auto", "Auto (Default)"),
                ChoiceOption("mp4", "MP4 (Best Compatibility)"),
                ChoiceOption("mkv", "MKV (Matroska)"),
                ChoiceOption("webm", "WebM")
            ),
            selectedValue = videoContainer,
            onSelect = { preferences.setVideoContainer(it) },
            onDismissRequest = { showVideoContainerDialog = false }
        )
    }

    // 8. Audio Format Dialog
    if (showAudioFormatDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.audio_format),
            options = listOf(
                ChoiceOption("mp3", "MP3 (Most Compatible)"),
                ChoiceOption("m4a", "M4A / AAC (Apple / High Quality)"),
                ChoiceOption("opus", "Opus (Best Efficiency)"),
                ChoiceOption("flac", "FLAC (Lossless)"),
                ChoiceOption("wav", "WAV (Uncompressed)")
            ),
            selectedValue = audioFormat,
            onSelect = { preferences.setAudioFormat(it) },
            onDismissRequest = { showAudioFormatDialog = false }
        )
    }

    // 9. Audio Quality Dialog
    if (showAudioQualityDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.audio_quality),
            options = listOf(
                ChoiceOption("best", "Best (VBR 0 / Maximum Bitrate)"),
                ChoiceOption("320", "320 kbps (High Quality)"),
                ChoiceOption("256", "256 kbps"),
                ChoiceOption("192", "192 kbps (Standard)"),
                ChoiceOption("128", "128 kbps (Compact)")
            ),
            selectedValue = audioQuality,
            onSelect = { preferences.setAudioQuality(it) },
            onDismissRequest = { showAudioQualityDialog = false }
        )
    }

    // 10. Subtitle Languages Dialog
    if (showSubtitleLangsDialog) {
        TextInputDialog(
            title = stringResource(R.string.subtitle_langs),
            initialValue = subtitleLanguages,
            placeholder = "all, en, zh, ja",
            onConfirm = { preferences.setSubtitleLanguages(it.ifBlank { "all" }) },
            onDismissRequest = { showSubtitleLangsDialog = false }
        )
    }

    // 11. Theme Dialog
    if (showThemeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.app_theme),
            options = listOf(
                ChoiceOption(AppThemeMode.SYSTEM, stringResource(R.string.follow_system)),
                ChoiceOption(AppThemeMode.LIGHT, stringResource(R.string.theme_light)),
                ChoiceOption(AppThemeMode.DARK, stringResource(R.string.theme_dark))
            ),
            selectedValue = themeMode,
            onSelect = { preferences.setThemeMode(it) },
            onDismissRequest = { showThemeDialog = false }
        )
    }

    // 12. Proxy Dialog
    if (showProxyDialog) {
        TextInputDialog(
            title = stringResource(R.string.network_proxy),
            initialValue = proxyUrl,
            placeholder = stringResource(R.string.proxy_hint),
            onConfirm = { url ->
                preferences.setProxyUrl(url.trim())
                if (url.isNotBlank()) preferences.setProxyEnabled(true)
            },
            onDismissRequest = { showProxyDialog = false }
        )
    }

    // 13. Language Dialog
    if (showLanguageDialog) {
        val languageOptions = LanguageUtil.supportedLanguages.map { item ->
            ChoiceOption(
                value = item.code,
                title = item.title,
                subtitle = item.subtitle
            )
        }
        val currentCode = LanguageUtil.getCurrentLanguageCode()

        SingleChoiceDialog(
            title = stringResource(R.string.app_language),
            options = languageOptions,
            selectedValue = currentCode,
            onSelect = { code ->
                LanguageUtil.setLanguage(code)
            },
            onDismissRequest = { showLanguageDialog = false }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val df = DecimalFormat("#,##0.#")
    return df.format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

private fun getDirectoryPathFromUri(uri: Uri): String? {
    val path = uri.path ?: return null
    if (path.contains(":")) {
        val parts = path.split(":")
        val relative = if (parts.size > 1) parts[1] else ""
        return "${Environment.getExternalStorageDirectory().absolutePath}/$relative"
    }
    return uri.path
}
