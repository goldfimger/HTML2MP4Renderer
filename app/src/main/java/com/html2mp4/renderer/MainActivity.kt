package com.html2mp4.renderer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.html2mp4.renderer.model.RenderedVideoItem
import com.html2mp4.renderer.model.ThemeMode
import com.html2mp4.renderer.ui.components.VideoPlayerDialog
import com.html2mp4.renderer.ui.screens.ProjectScreen
import com.html2mp4.renderer.ui.screens.ResultsScreen
import com.html2mp4.renderer.ui.theme.Html2Mp4Theme
import com.html2mp4.renderer.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: MainViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val metadata by vm.projectMetadata.collectAsStateWithLifecycle()
            val isProbing by vm.isProbingDuration.collectAsStateWithLifecycle()
            val config by vm.config.collectAsStateWithLifecycle()
            val renderStatus by vm.renderStatus.collectAsStateWithLifecycle()
            val videos by vm.renderedVideos.collectAsStateWithLifecycle()
            val thumbnails by vm.thumbnails.collectAsStateWithLifecycle()

            var selectedTab by remember { mutableIntStateOf(0) }
            var playingVideo by remember { mutableStateOf<RenderedVideoItem?>(null) }

            val context = LocalContext.current
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {}

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val htmlPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> uri?.let { vm.onHtmlFileSelected(it) } }

            val assetsFolderLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri -> uri?.let { vm.onAssetsFolderSelected(it) } }

            val outputFolderLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri -> uri?.let { vm.onOutputFolderSelected(it) } }

            Html2Mp4Theme(themeMode = themeMode) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("HTML2MP4 Renderer") },
                            actions = {
                                IconButton(onClick = { vm.cycleThemeMode() }) {
                                    val icon = when (themeMode) {
                                        ThemeMode.SYSTEM -> Icons.Rounded.Brightness4
                                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                                    }
                                    Icon(icon, contentDescription = "Ganti Tema")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                                label = { Text("Proyek") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = {
                                    selectedTab = 1
                                    vm.refreshRenderedVideos()
                                },
                                icon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = null) },
                                label = { Text("Hasil") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (selectedTab == 0) {
                            ProjectScreen(
                                metadata = metadata,
                                isProbingDuration = isProbing,
                                config = config,
                                renderStatus = renderStatus,
                                onPickHtmlFile = {
                                    htmlPickerLauncher.launch(arrayOf("text/html", "application/xhtml+xml"))
                                },
                                onPickAssetsFolder = { assetsFolderLauncher.launch(null) },
                                onPickOutputFolder = { outputFolderLauncher.launch(null) },
                                onOrientationChange = vm::updateOrientation,
                                onResolutionChange = vm::updateResolution,
                                onCustomSizeChange = vm::updateCustomDimensions,
                                onFrameRateChange = vm::updateFrameRate,
                                onVideoCodecChange = vm::updateVideoCodec,
                                onVideoBitratePresetChange = vm::updateVideoBitratePreset,
                                onCustomBitrateChange = vm::updateCustomBitrate,
                                onAudioMutedChange = vm::updateAudioMuted,
                                onAudioCodecChange = vm::updateAudioCodec,
                                onAudioSampleRateChange = vm::updateAudioSampleRate,
                                onAudioBitrateChange = vm::updateAudioBitrate,
                                onAudioChannelsChange = vm::updateAudioChannels,
                                onStartRender = vm::startRender,
                                onCancelRender = vm::cancelRender,
                                onDismissStatus = vm::dismissCompletedBanner,
                                onNavigateToResults = {
                                    selectedTab = 1
                                    vm.refreshRenderedVideos()
                                }
                            )
                        } else {
                            ResultsScreen(
                                videos = videos,
                                thumbnails = thumbnails,
                                onRefresh = vm::refreshRenderedVideos,
                                onPlayVideo = { playingVideo = it }
                            )
                        }
                    }
                }

                playingVideo?.let { video ->
                    VideoPlayerDialog(
                        video = video,
                        onDismiss = { playingVideo = null }
                    )
                }
            }
        }
    }
}
