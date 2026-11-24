package com.html2mp4.renderer

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.html2mp4.renderer.model.AppLanguage
import com.html2mp4.renderer.model.ThemeMode
import com.html2mp4.renderer.ui.i18n.resolveAppStrings
import com.html2mp4.renderer.ui.screens.VideoEditorScreen
import com.html2mp4.renderer.ui.theme.Html2Mp4Theme
import com.html2mp4.renderer.ui.viewmodel.VideoEditorViewModel

class VideoEditorActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_NAME = "extra_video_name"
        const val EXTRA_OUTPUT_TREE_URI = "extra_output_tree_uri"
        const val EXTRA_APP_LANGUAGE = "extra_app_language"
        const val EXTRA_THEME_MODE = "extra_theme_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
        val videoUri = uriStr?.let { Uri.parse(it) }
        if (videoUri == null) {
            finish()
            return
        }
        val videoName = intent.getStringExtra(EXTRA_VIDEO_NAME) ?: "video.mp4"
        val outTreeStr = intent.getStringExtra(EXTRA_OUTPUT_TREE_URI)
        val outTreeUri = outTreeStr?.let { Uri.parse(it) }
        val appLanguage = intent.getStringExtra(EXTRA_APP_LANGUAGE)
            ?.let { raw -> AppLanguage.entries.firstOrNull { it.name == raw } }
            ?: AppLanguage.INDONESIAN
        val themeMode = intent.getStringExtra(EXTRA_THEME_MODE)
            ?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } }
            ?: ThemeMode.SYSTEM

        setContent {
            val vm: VideoEditorViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val strings = remember(appLanguage) { resolveAppStrings(appLanguage) }
            var externalCenterToast by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(videoUri) {
                vm.initialize(videoUri, videoName, outTreeUri)
            }

            val footagePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    vm.addFootageClip(it) {
                        externalCenterToast = strings.editorAddedFootageToast
                    }
                }
            }

            val audioPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    vm.addAudioClip(it) {
                        externalCenterToast = strings.editorAddedAudioToast
                    }
                }
            }

            Html2Mp4Theme(themeMode = themeMode) {
                VideoEditorScreen(
                    strings = strings,
                    state = state,
                    previewUri = videoUri,
                    externalToastMsg = externalCenterToast,
                    onClose = { finish() },
                    onTogglePlayPause = vm::togglePlayPause,
                    onSeekPlayhead = vm::updatePlayhead,
                    onSelectSegment = vm::selectSegment,
                    onBeginGestureSnapshot = vm::beginGestureSnapshot,
                    onSwapSegment = vm::swapSegments,
                    onSetAudioVolume = vm::setAudioVolume,
                    onTrimSegment = vm::trimSegment,
                    onCutAtPlayhead = vm::cutAtPlayhead,
                    onDeleteSelected = vm::deleteSelectedSegment,
                    onUndo = vm::undo,
                    onRedo = vm::redo,
                    onPickFootage = { footagePicker.launch(arrayOf("video/*")) },
                    onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                    onAddText = vm::addTextOverlay,
                    onAddCaption = vm::addManualCaption,
                    onAutoCaption = {
                        vm.generateSmartAutoCaptions(strings.editorAutoCaptionDoneFormat) { msg ->
                            externalCenterToast = msg
                        }
                    },
                    onSmartAutoSync = {
                        vm.runSmartAutoSync(strings.editorSyncDoneFormat) { }
                    },
                    onNudgeAudioOffset = vm::nudgeAudioOffset,
                    onZoomIn = vm::zoomIn,
                    onZoomOut = vm::zoomOut,
                    onZoomScale = vm::updateZoom,
                    onSave = { customName ->
                        vm.saveEditedMp4(customName) {
                            Toast.makeText(this, strings.editorSavedToast, Toast.LENGTH_SHORT).show()
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                    }
                )
            }
        }
    }
}
