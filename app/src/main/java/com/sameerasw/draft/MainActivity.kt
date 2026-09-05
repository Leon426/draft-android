package com.sameerasw.draft

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.animation.AnticipateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.sameerasw.draft.data.model.DownloadTask
import com.sameerasw.draft.ui.components.DownloadTaskCard
import com.sameerasw.draft.ui.components.containers.RoundedCardContainer
import com.sameerasw.draft.ui.components.settings.SettingsPage
import com.sameerasw.draft.ui.components.sheets.AboutBottomSheet
import com.sameerasw.draft.ui.components.sheets.DownloadSheet
import com.sameerasw.draft.ui.components.toolbar.EssentialsFloatingToolbar
import com.sameerasw.draft.ui.components.toolbar.ToolbarItem
import com.sameerasw.draft.ui.modifiers.BlurDirection
import com.sameerasw.draft.ui.modifiers.progressiveBlur
import com.sameerasw.draft.ui.theme.DraftTheme
import com.sameerasw.draft.utils.DeviceUtils
import com.sameerasw.draft.utils.HapticUtil
import com.sameerasw.draft.viewmodel.DownloadViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            try {
                val splashScreenView = splashScreenViewProvider.view
                val splashIcon = try {
                    splashScreenViewProvider.iconView
                } catch (_: Exception) {
                    null
                }

                val fadeOut = ObjectAnimator.ofFloat(splashScreenView, "alpha", 1f, 0f).apply {
                    interpolator = AnticipateInterpolator()
                    duration = 750
                }
                fadeOut.doOnEnd {
                    splashScreenViewProvider.remove()
                    enableEdgeToEdge()
                }

                if (splashIcon != null) {
                    val scaleUpX = ObjectAnimator.ofFloat(splashIcon, "scaleX", 1f, 1.5f).apply {
                        interpolator = AnticipateInterpolator()
                        duration = 750
                    }
                    val scaleUpY = ObjectAnimator.ofFloat(splashIcon, "scaleY", 1f, 1.5f).apply {
                        interpolator = AnticipateInterpolator()
                        duration = 750
                    }
                    scaleUpX.start()
                    scaleUpY.start()
                }
                fadeOut.start()
            } catch (e: Exception) {
                Log.e("SplashScreen", "Error during splash screen animation", e)
                try { splashScreenViewProvider.remove() } catch (_: Exception) {}
            }
        }

        setContent {
            val themeMode by viewModel.preferences.themeMode.collectAsState()
            val dynamicColor by viewModel.preferences.dynamicColor.collectAsState()
            DraftTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(viewModel: DownloadViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val isBlurEnabled by viewModel.isBlurEnabled.collectAsState()

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    val exitAlwaysScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusBarHeightPx = with(LocalDensity.current) { statusBarHeight.toPx() }
    val contentPadding = PaddingValues(
        top = statusBarHeight,
        bottom = 150.dp,
        start = 16.dp,
        end = 16.dp
    )

    var showDownloadSheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }

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
                    .progressiveBlur(
                        blurRadius = if (isBlurEnabled) 40f else 0f,
                        height = statusBarHeightPx * 1.15f,
                        direction = BlurDirection.TOP
                    )
            ) {
                EssentialsFloatingToolbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f),
                    selectedIndex = pagerState.currentPage,
                    items = listOf(
                        ToolbarItem(
                            iconRes = R.drawable.rounded_download_24,
                            labelRes = R.string.tab_downloads,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                        ),
                        ToolbarItem(
                            iconRes = R.drawable.rounded_settings_24,
                            labelRes = R.string.tab_settings,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(1) }
                            }
                        )
                    ),
                    scrollBehavior = exitAlwaysScrollBehavior,
                    floatingActionButton = {
                        if (pagerState.currentPage == 0) {
                            FloatingActionButton(
                                onClick = { showDownloadSheet = true },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.background,
                                shape = MaterialTheme.shapes.large,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_add_24),
                                    contentDescription = stringResource(R.string.new_download)
                                )
                            }
                        } else {
                            FloatingActionButton(
                                onClick = { showAboutSheet = true },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.background,
                                shape = MaterialTheme.shapes.large,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_info_24),
                                    contentDescription = stringResource(R.string.about_velo)
                                )
                            }
                        }
                    }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .progressiveBlur(
                            blurRadius = if (isBlurEnabled) 40f else 0f,
                            height = with(LocalDensity.current) { 130.dp.toPx() },
                            direction = BlurDirection.BOTTOM
                        )
                ) { page ->
                    when (page) {
                        0 -> DownloadTasksPage(
                            tasks = tasks,
                            viewModel = viewModel,
                            contentPadding = contentPadding
                        )
                        1 -> SettingsPage(
                            viewModel = viewModel,
                            contentPadding = contentPadding,
                            onOpenAbout = { showAboutSheet = true }
                        )
                    }
                }
            }
        }
    }

    if (showDownloadSheet) {
        DownloadSheet(
            viewModel = viewModel,
            onDismissRequest = { showDownloadSheet = false }
        )
    }

    if (showAboutSheet) {
        AboutBottomSheet(
            onDismissRequest = { showAboutSheet = false }
        )
    }
}

@Composable
fun DownloadTasksPage(
    tasks: List<DownloadTask>,
    viewModel: DownloadViewModel,
    contentPadding: PaddingValues
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_video_library_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = stringResource(R.string.empty_tasks_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.empty_tasks_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            item {
                RoundedCardContainer {
                    tasks.forEachIndexed { index, task ->
                        val visibleState = remember { androidx.compose.animation.core.MutableTransitionState(false) }
                        LaunchedEffect(task.id) {
                            delay(index * 60L)
                            visibleState.targetState = true
                        }

                        AnimatedVisibility(
                            visibleState = visibleState,
                            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                    slideInVertically(
                                        initialOffsetY = { -it / 2 },
                                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                                    )
                        ) {
                            DownloadTaskCard(
                                task = task,
                                onCancel = { viewModel.cancelTask(task.id) },
                                onDelete = { viewModel.deleteTask(task.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}