package com.html2mp4.renderer.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.html2mp4.renderer.ui.i18n.AppStrings
import com.html2mp4.renderer.ui.viewmodel.VideoEditorUiState
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    strings: AppStrings,
    state: VideoEditorUiState,
    previewUri: Uri,
    externalToastMsg: String? = null,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekPlayhead: (Long) -> Unit,
    onSelectSegment: (Boolean, Int) -> Unit,
    onBeginGestureSnapshot: () -> Unit,
    onSwapSegment: (Boolean, Int, Int) -> Boolean,
    onSetAudioVolume: (Float) -> Unit,
    onTrimSegment: (Boolean, Int, Long, Long) -> Unit,
    onCutAtPlayhead: () -> Boolean,
    onDeleteSelected: () -> Boolean,
    onUndo: () -> Boolean,
    onRedo: () -> Boolean,
    onPickFootage: () -> Unit,
    onPickAudio: () -> Unit,
    onAddText: (String) -> Boolean,
    onAddCaption: (String) -> Boolean,
    onAutoCaption: () -> Unit,
    onSmartAutoSync: () -> Unit,
    onNudgeAudioOffset: (Long) -> Long,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomScale: (Float) -> Unit,
    onSave: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Minimalist centered translucent Toast (only for Cut, Delete, Add Media, Text changes, and long-press hints)
    var centerToastText by remember { mutableStateOf<String?>(null) }
    var centerToastTick by remember { mutableIntStateOf(0) }
    val showCenterToast: (String) -> Unit = { msg ->
        centerToastText = msg
        centerToastTick++
    }
    LaunchedEffect(externalToastMsg) {
        if (!externalToastMsg.isNullOrBlank()) {
            centerToastText = externalToastMsg
            centerToastTick++
        }
    }
    LaunchedEffect(centerToastTick) {
        if (centerToastText != null) {
            delay(1600L)
            centerToastText = null
        }
    }

    val player = remember(previewUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(previewUri))
            prepare()
        }
    }

    var showAudioSettingsPanel by remember { mutableStateOf(false) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var renameDraft by remember(state.defaultEditedFileName) { mutableStateOf(state.defaultEditedFileName) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var showCaptionInputDialog by remember { mutableStateOf(false) }
    var draftInputText by remember { mutableStateOf("") }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(state.audioVolume) {
        player.volume = state.audioVolume.coerceIn(0f, 1f)
    }

    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(state.isPlaying, state.totalVideoDurationMs) {
        while (state.isPlaying) {
            onSeekPlayhead(player.currentPosition.coerceIn(0L, state.totalVideoDurationMs.coerceAtLeast(100L)))
            delay(33L)
        }
    }

    val totalVideoMs = state.totalVideoDurationMs.coerceAtLeast(500L)
    val totalAudioMs = state.totalAudioDurationMs.coerceAtLeast(500L)
    val timelinePxPerSec = 56f * state.timelineZoom
    val pxPerMs = timelinePxPerSec / 1000f
    val density = LocalDensity.current

    var isReorderingAnyClip by remember { mutableStateOf(false) }
    val latestPlayheadMs by rememberUpdatedState(state.playheadMs)
    val latestPxPerMs by rememberUpdatedState(pxPerMs)
    val latestTotalVideoMs by rememberUpdatedState(totalVideoMs)
    val latestTotalAudioMs by rememberUpdatedState(totalAudioMs)
    val latestZoom by rememberUpdatedState(state.timelineZoom)
    val latestIsReordering by rememberUpdatedState(isReorderingAnyClip)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            renameDraft = state.defaultEditedFileName
                            showSaveConfirmDialog = true
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = strings.closeButton)
                        }
                    },
                    title = {
                        Column {
                            Text(strings.editorTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                state.sourceFileName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { onUndo() },
                            enabled = state.canUndo
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = strings.editorUndoLabel, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { onRedo() },
                            enabled = state.canRedo
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = strings.editorRedoLabel, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = {
                                renameDraft = state.defaultEditedFileName
                                showSaveConfirmDialog = true
                            },
                            enabled = state.exportProgress == null && state.videoSegments.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.editorSaveButton, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                if (state.exportProgress != null) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Text(
                                text = strings.editorSavingFormat(state.exportProgress),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { state.exportProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                state.exportError?.let { err ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${strings.failedPrefix}: $err",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // 1. SPACIOUS TOP VIDEO PREVIEW WITH LIVE TEXT & CAPTION OVERLAYS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                this.player = player
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    val activeText = state.textOverlays.firstOrNull { state.playheadMs in it.startMs..it.endMs }
                    val activeCaption = state.captions.firstOrNull { state.playheadMs in it.startMs..it.endMs }

                    if (activeText != null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 18.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Text(
                                text = activeText.text,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (activeCaption != null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Text(
                                text = activeCaption.text,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 2. BOTTOM-LEFT PLAY/PAUSE ICON + MILLISECOND TIMESTAMP (NO PROGRESS BAR)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = onTogglePlayPause,
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (state.isPlaying) strings.editorPauseLabel else strings.editorPlayLabel,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Text(
                            text = "${formatEditorMsPrecise(state.playheadMs)} / ${formatEditorMsPrecise(totalVideoMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = String.format(Locale.US, "%.1fx", state.timelineZoom),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 3. COMPACT ICON-ONLY FUNCTION TOOLBAR (LONG PRESS SHOWS LABEL TOAST)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconOnlyToolButton(
                        icon = Icons.Rounded.ContentCut,
                        tooltip = strings.editorCutLabel,
                        onClick = { if (onCutAtPlayhead()) showCenterToast(strings.editorCutToast) },
                        onLongPressInfo = { showCenterToast(strings.editorCutLabel) }
                    )
                    IconOnlyToolButton(
                        icon = Icons.Rounded.Delete,
                        tooltip = strings.editorDeleteLabel,
                        enabled = (state.selectedTrackIsVideo && state.videoSegments.size > 1) ||
                            (!state.selectedTrackIsVideo && state.audioSegments.isNotEmpty()),
                        onClick = { if (onDeleteSelected()) showCenterToast(strings.editorDeleteToast) },
                        onLongPressInfo = { showCenterToast(strings.editorDeleteLabel) }
                    )
                    IconOnlyToolButton(
                        icon = Icons.Rounded.VideoCall,
                        tooltip = strings.editorAddFootageLabel,
                        onClick = onPickFootage,
                        onLongPressInfo = { showCenterToast(strings.editorAddFootageLabel) }
                    )
                    IconOnlyToolButton(
                        icon = Icons.Rounded.AudioFile,
                        tooltip = strings.editorAddAudioLabel,
                        onClick = onPickAudio,
                        onLongPressInfo = { showCenterToast(strings.editorAddAudioLabel) }
                    )
                    IconOnlyToolButton(
                        icon = Icons.Rounded.Title,
                        tooltip = strings.editorAddTextLabel,
                        onClick = {
                            draftInputText = ""
                            showTextInputDialog = true
                        },
                        onLongPressInfo = { showCenterToast(strings.editorAddTextLabel) }
                    )
                    IconOnlyToolButton(
                        icon = Icons.Rounded.ClosedCaption,
                        tooltip = strings.editorAddCaptionLabel,
                        onClick = {
                            draftInputText = ""
                            showCaptionInputDialog = true
                        },
                        onLongPressInfo = { showCenterToast(strings.editorAddCaptionLabel) }
                    )
                    IconOnlyToolButton(
                        icon = Icons.Rounded.Subtitles,
                        tooltip = strings.editorAutoCaptionLabel,
                        enabled = !state.isGeneratingCaptions && state.audioSegments.isNotEmpty(),
                        loading = state.isGeneratingCaptions,
                        onClick = onAutoCaption,
                        onLongPressInfo = { showCenterToast(strings.editorAutoCaptionLabel) }
                    )
                    // Audio Settings Icon Button -> Houses Volume, Audio Timeline & Smart Sync
                    IconOnlyToolButton(
                        icon = Icons.Rounded.GraphicEq,
                        tooltip = strings.editorAudioSyncPanelLabel,
                        active = showAudioSettingsPanel,
                        onClick = { showAudioSettingsPanel = !showAudioSettingsPanel },
                        onLongPressInfo = { showCenterToast(strings.editorAudioSyncPanelLabel) }
                    )
                    IconOnlyToolButton(
                        icon = Icons.Rounded.ZoomOut,
                        tooltip = strings.editorZoomOutLabel,
                        onClick = onZoomOut,
                        onLongPressInfo = { showCenterToast(strings.editorZoomOutLabel) }
                    )
                    IconOnlyToolButton(
                        icon = Icons.Rounded.ZoomIn,
                        tooltip = strings.editorZoomInLabel,
                        onClick = onZoomIn,
                        onLongPressInfo = { showCenterToast(strings.editorZoomInLabel) }
                    )
                }

                // CONDITIONAL AUDIO SETTINGS DRAWER (Volume + Audio Timeline + Smart A/V Sync)
                // Hidden on initial view; only revealed when pressing the Audio icon button
                if (showAudioSettingsPanel) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Audio Volume Slider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = strings.editorAudioVolumeLabel,
                                    modifier = Modifier.size(16.dp)
                                )
                                Slider(
                                    value = state.audioVolume,
                                    onValueChange = { onSetAudioVolume(it) },
                                    valueRange = 0f..1.5f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(24.dp)
                                )
                                Text(
                                    text = "${(state.audioVolume * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Audio Timeline inside Audio Settings with imaginary boundaries & long-press-only reorder
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clipToBounds()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            var prevX: Float? = null
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val pressed = event.changes.filter { it.pressed }
                                                if (pressed.isEmpty()) break
                                                if (latestIsReordering) {
                                                    prevX = null
                                                    continue
                                                }
                                                if (pressed.size >= 2) {
                                                    val zoomChange = event.calculateZoom()
                                                    if (abs(zoomChange - 1f) > 0.01f) {
                                                        onZoomScale(latestZoom * zoomChange)
                                                    }
                                                    pressed.forEach { it.consume() }
                                                    prevX = null
                                                } else {
                                                    val p = pressed.first()
                                                    val x = p.position.x
                                                    if (!p.isConsumed) {
                                                        prevX?.let { lx ->
                                                            val dx = x - lx
                                                            val dMs = (-dx / latestPxPerMs).toLong()
                                                            val clamped = (latestPlayheadMs + dMs).coerceIn(0L, latestTotalAudioMs)
                                                            onSeekPlayhead(clamped)
                                                            player.seekTo(clamped)
                                                        }
                                                    }
                                                    prevX = x
                                                    p.consume()
                                                }
                                            }
                                        }
                                    }
                            ) {
                                val halfWpx = with(density) { (maxWidth / 2).toPx() }
                                val shiftPx = (halfWpx - (state.playheadMs.coerceIn(0L, totalAudioMs) * pxPerMs)).roundToInt()

                                if (state.audioSegments.isEmpty()) {
                                    Text(
                                        text = "Muted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .offset { IntOffset(shiftPx, 0) },
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        state.audioSegments.forEachIndexed { idx, seg ->
                                            val segW = with(density) {
                                                (seg.activeDurationMs * pxPerMs).coerceAtLeast(42f).toDp()
                                            }
                                            MiniTrimSegmentBar(
                                                label = seg.label,
                                                widthDp = segW,
                                                heightDp = 24.dp,
                                                fullDurationMs = seg.fullDurationMs,
                                                trimStartMs = seg.trimStartMs,
                                                trimEndMs = seg.trimEndMs,
                                                isSelected = !state.selectedTrackIsVideo && state.selectedSegmentIndex == idx,
                                                fillColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                handleColor = MaterialTheme.colorScheme.tertiary,
                                                waveformPeaks = seg.waveformPeaks,
                                                segmentIndex = idx,
                                                segmentCount = state.audioSegments.size,
                                                onSelect = { onSelectSegment(false, idx) },
                                                onBeginGesture = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onBeginGestureSnapshot()
                                                },
                                                onReorderActiveChange = { active -> isReorderingAnyClip = active },
                                                onSwapToNeighbor = { fromIdx, toIdx ->
                                                    onSwapSegment(false, fromIdx, toIdx)
                                                },
                                                onTrimChange = { s, e -> onTrimSegment(false, idx, s, e) }
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(Color(0xFFFF5252))
                                )
                            }

                            // Smart A/V Sync + ms nudges
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconOnlyToolButton(
                                    icon = Icons.Rounded.AutoFixHigh,
                                    tooltip = strings.editorSmartSyncLabel,
                                    enabled = !state.isAnalyzingSync,
                                    loading = state.isAnalyzingSync,
                                    onClick = onSmartAutoSync,
                                    onLongPressInfo = { showCenterToast(strings.editorSmartSyncLabel) }
                                )
                                Text(
                                    text = "${if (state.audioOffsetMs > 0) "+" else ""}${state.audioOffsetMs} ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                listOf(-50L, -10L, 10L, 50L).forEach { delta ->
                                    OutlinedButton(
                                        onClick = { onNudgeAudioOffset(delta) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text(if (delta > 0) "+${delta}ms" else "${delta}ms", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Breathing space above timeline so function buttons sit higher while keeping timeline size unchanged
                Spacer(Modifier.height(14.dp))

                // 4. MINIMALIST BOTTOM VIDEO TIMELINE (ONLY VIDEO IN INITIAL VIEW)
                // - Imaginary start (0ms) and end (totalVideoMs) boundary walls clamp playhead cleanly at center line
                // - Two-finger pinch zoom down to millisecond precision
                // - Clips only move/swap positions on long-press drag (no wild movement)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clipToBounds()
                            .padding(vertical = 6.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var prevX: Float? = null
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (latestIsReordering) {
                                            prevX = null
                                            continue
                                        }
                                        if (pressed.size >= 2) {
                                            val zoomChange = event.calculateZoom()
                                            if (abs(zoomChange - 1f) > 0.01f) {
                                                onZoomScale(latestZoom * zoomChange)
                                            }
                                            pressed.forEach { it.consume() }
                                            prevX = null
                                        } else {
                                            val p = pressed.first()
                                            val x = p.position.x
                                            if (!p.isConsumed) {
                                                prevX?.let { lx ->
                                                    val dx = x - lx
                                                    val dMs = (-dx / latestPxPerMs).toLong()
                                                    val clamped = (latestPlayheadMs + dMs).coerceIn(0L, latestTotalVideoMs)
                                                    onSeekPlayhead(clamped)
                                                    player.seekTo(clamped)
                                                }
                                            }
                                            prevX = x
                                            p.consume()
                                        }
                                    }
                                }
                            }
                    ) {
                        val halfWpx = with(density) { (maxWidth / 2).toPx() }
                        val clampedPlayhead = state.playheadMs.coerceIn(0L, totalVideoMs)
                        val shiftPx = (halfWpx - (clampedPlayhead * pxPerMs)).roundToInt()

                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset { IntOffset(shiftPx, 0) },
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            state.videoSegments.forEachIndexed { idx, seg ->
                                val segW = with(density) {
                                    (seg.activeDurationMs * pxPerMs).coerceAtLeast(42f).toDp()
                                }
                                MiniTrimSegmentBar(
                                    label = seg.label,
                                    widthDp = segW,
                                    heightDp = 28.dp,
                                    fullDurationMs = seg.fullDurationMs,
                                    trimStartMs = seg.trimStartMs,
                                    trimEndMs = seg.trimEndMs,
                                    isSelected = state.selectedTrackIsVideo && state.selectedSegmentIndex == idx,
                                    fillColor = MaterialTheme.colorScheme.primaryContainer,
                                    handleColor = MaterialTheme.colorScheme.primary,
                                    waveformPeaks = null,
                                    segmentIndex = idx,
                                    segmentCount = state.videoSegments.size,
                                    onSelect = { onSelectSegment(true, idx) },
                                    onBeginGesture = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onBeginGestureSnapshot()
                                    },
                                    onReorderActiveChange = { active -> isReorderingAnyClip = active },
                                    onSwapToNeighbor = { fromIdx, toIdx ->
                                        onSwapSegment(true, fromIdx, toIdx)
                                    },
                                    onTrimChange = { s, e -> onTrimSegment(true, idx, s, e) }
                                )
                            }
                        }

                        // STATIONARY CENTER VERTICAL LINE (IMAGINARY START/END BOUNDARY REFERENCE)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(Color(0xFFFF5252))
                        )
                    }
                }
            }
        }

        // CENTERED MINIMALIST TRANSLUCENT TOAST OVERLAY
        AnimatedVisibility(
            visible = centerToastText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.56f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = centerToastText ?: "",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Save or Discard Confirmation Dialog + Output Filename Rename
    if (showSaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            title = { Text(strings.editorSaveConfirmTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.editorSaveConfirmBody, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        label = { Text(strings.editorRenameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSaveConfirmDialog = false
                    onSave(renameDraft)
                }) {
                    Text(strings.editorConfirmSaveAction)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveConfirmDialog = false
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.editorConfirmDiscardAction)
                }
            }
        )
    }

    if (showTextInputDialog) {
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = { Text(strings.editorTextDialogTitle) },
            text = {
                OutlinedTextField(
                    value = draftInputText,
                    onValueChange = { draftInputText = it },
                    placeholder = { Text(strings.editorInputHint) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (onAddText(draftInputText)) showCenterToast(strings.editorAddedTextToast)
                    showTextInputDialog = false
                }) { Text(strings.okButton) }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) { Text(strings.cancelButton) }
            }
        )
    }

    if (showCaptionInputDialog) {
        AlertDialog(
            onDismissRequest = { showCaptionInputDialog = false },
            title = { Text(strings.editorCaptionDialogTitle) },
            text = {
                OutlinedTextField(
                    value = draftInputText,
                    onValueChange = { draftInputText = it },
                    placeholder = { Text(strings.editorInputHint) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (onAddCaption(draftInputText)) showCenterToast(strings.editorAddedCaptionToast)
                    showCaptionInputDialog = false
                }) { Text(strings.okButton) }
            },
            dismissButton = {
                TextButton(onClick = { showCaptionInputDialog = false }) { Text(strings.cancelButton) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconOnlyToolButton(
    icon: ImageVector,
    tooltip: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
    onLongPressInfo: () -> Unit
) {
    val bg = when {
        active -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        active -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongPressInfo
            ),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.8.dp, color = fg)
        } else {
            Icon(icon, contentDescription = tooltip, tint = fg, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MiniTrimSegmentBar(
    label: String,
    widthDp: Dp,
    heightDp: Dp,
    fullDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    isSelected: Boolean,
    fillColor: Color,
    handleColor: Color,
    waveformPeaks: List<Float>?,
    segmentIndex: Int,
    segmentCount: Int,
    onSelect: () -> Unit,
    onBeginGesture: () -> Unit,
    onReorderActiveChange: (Boolean) -> Unit,
    onSwapToNeighbor: (Int, Int) -> Boolean,
    onTrimChange: (Long, Long) -> Unit
) {
    val density = LocalDensity.current
    val trackWidthPx = with(density) { widthDp.toPx() }.coerceAtLeast(42f)
    val safeFull = fullDurationMs.coerceAtLeast(500L)
    var leftDragMs by remember(trimStartMs) { mutableFloatStateOf(trimStartMs.toFloat()) }
    var rightDragMs by remember(trimEndMs) { mutableFloatStateOf(trimEndMs.toFloat()) }
    var dragAccumPx by remember { mutableFloatStateOf(0f) }
    var isDraggingReorder by remember { mutableStateOf(false) }

    val latestIdx by rememberUpdatedState(segmentIndex)
    val latestCount by rememberUpdatedState(segmentCount)
    val latestTrackW by rememberUpdatedState(trackWidthPx)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestOnBegin by rememberUpdatedState(onBeginGesture)
    val latestOnSwap by rememberUpdatedState(onSwapToNeighbor)
    val latestOnReorderActive by rememberUpdatedState(onReorderActiveChange)

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .graphicsLayer {
                translationX = if (isDraggingReorder) dragAccumPx else 0f
                scaleY = if (isDraggingReorder) 1.06f else 1f
            }
            .clip(RoundedCornerShape(6.dp))
            .background(fillColor)
            .border(
                width = if (isDraggingReorder || isSelected) 1.5.dp else 0.5.dp,
                color = if (isDraggingReorder || isSelected) handleColor else handleColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(6.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { latestOnSelect() })
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDraggingReorder = true
                        dragAccumPx = 0f
                        latestOnReorderActive(true)
                        latestOnBegin()
                    },
                    onDragEnd = {
                        isDraggingReorder = false
                        dragAccumPx = 0f
                        latestOnReorderActive(false)
                    },
                    onDragCancel = {
                        isDraggingReorder = false
                        dragAccumPx = 0f
                        latestOnReorderActive(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumPx += dragAmount.x
                        val swapThreshold = (latestTrackW * 0.55f).coerceIn(28f, 96f)
                        if (dragAccumPx > swapThreshold && latestIdx < latestCount - 1) {
                            if (latestOnSwap(latestIdx, latestIdx + 1)) {
                                dragAccumPx = 0f
                            }
                        } else if (dragAccumPx < -swapThreshold && latestIdx > 0) {
                            if (latestOnSwap(latestIdx, latestIdx - 1)) {
                                dragAccumPx = 0f
                            }
                        }
                    }
                )
            }
    ) {
        if (!waveformPeaks.isNullOrEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                val count = waveformPeaks.size
                val barSlot = size.width / count.toFloat()
                val barW = (barSlot * 0.65f).coerceAtLeast(1.5f)
                val waveColor = handleColor.copy(alpha = 0.68f)
                for (i in 0 until count) {
                    val amp = waveformPeaks[i].coerceIn(0.08f, 1f)
                    val h = size.height * amp
                    val y = (size.height - h) / 2f
                    drawRect(
                        color = waveColor,
                        topLeft = Offset(i * barSlot, y),
                        size = Size(barW, h)
                    )
                }
            }
        } else {
            Text(
                text = label,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 12.dp)
            )
        }

        // Left trim handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(9.dp)
                .fillMaxHeight()
                .background(handleColor)
                .pointerInput(safeFull) {
                    detectHorizontalDragGestures(
                        onDragStart = { onBeginGesture() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount / trackWidthPx) * safeFull
                            leftDragMs = (leftDragMs + deltaMs).coerceIn(0f, rightDragMs - 150f)
                            onTrimChange(leftDragMs.toLong(), rightDragMs.toLong())
                        }
                    )
                }
        )

        // Right trim handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(9.dp)
                .fillMaxHeight()
                .background(handleColor)
                .pointerInput(safeFull) {
                    detectHorizontalDragGestures(
                        onDragStart = { onBeginGesture() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount / trackWidthPx) * safeFull
                            rightDragMs = (rightDragMs + deltaMs).coerceIn(leftDragMs + 150f, safeFull.toFloat())
                            onTrimChange(leftDragMs.toLong(), rightDragMs.toLong())
                        }
                    )
                }
        )
    }
}

private fun formatEditorMsPrecise(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val remMs = safe % 1000L
    return String.format(Locale.US, "%02d:%02d.%03d", totalSec / 60L, totalSec % 60L, remMs)
}
