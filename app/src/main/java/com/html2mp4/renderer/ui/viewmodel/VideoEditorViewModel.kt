package com.html2mp4.renderer.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.html2mp4.renderer.data.RenderRepository
import com.html2mp4.renderer.engine.VideoEditorEngine
import com.html2mp4.renderer.model.AudioClipSegment
import com.html2mp4.renderer.model.CaptionSegment
import com.html2mp4.renderer.model.TextOverlayItem
import com.html2mp4.renderer.model.VideoClipSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class EditorTimelineSnapshot(
    val videoSegments: List<VideoClipSegment>,
    val audioSegments: List<AudioClipSegment>,
    val textOverlays: List<TextOverlayItem>,
    val captions: List<CaptionSegment>,
    val videoTrackOffsetMs: Long,
    val audioOffsetMs: Long,
    val audioVolume: Float = 1.0f
)

data class VideoEditorUiState(
    val sourceFileName: String = "video.mp4",
    val videoSegments: List<VideoClipSegment> = emptyList(),
    val audioSegments: List<AudioClipSegment> = emptyList(),
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val captions: List<CaptionSegment> = emptyList(),
    val videoTrackOffsetMs: Long = 0L,
    val audioOffsetMs: Long = 0L,
    val audioVolume: Float = 1.0f,
    val timelineZoom: Float = 1.0f,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedTrackIsVideo: Boolean = true,
    val selectedSegmentIndex: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isAnalyzingSync: Boolean = false,
    val syncStatusMessage: String? = null,
    val isGeneratingCaptions: Boolean = false,
    val captionStatusMessage: String? = null,
    val detectedAudioLanguage: String = "id",
    val exportProgress: Int? = null,
    val exportError: String? = null
) {
    val totalVideoDurationMs: Long
        get() = videoSegments.sumOf { it.activeDurationMs }.coerceAtLeast(0L)

    val totalAudioDurationMs: Long
        get() = audioSegments.sumOf { it.activeDurationMs }.coerceAtLeast(0L)

    val defaultEditedFileName: String
        get() {
            val withExt = if (sourceFileName.endsWith(".mp4", ignoreCase = true)) {
                sourceFileName
            } else {
                "${sourceFileName}.mp4"
            }
            return if (withExt.startsWith("edited_", ignoreCase = true)) {
                withExt
            } else {
                "edited_$withExt"
            }
        }
}

class VideoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RenderRepository(application)
    private val _state = MutableStateFlow(VideoEditorUiState())
    val state: StateFlow<VideoEditorUiState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<EditorTimelineSnapshot>()
    private val redoStack = ArrayDeque<EditorTimelineSnapshot>()
    private var outputTreeUri: Uri? = null
    private var initialized = false

    fun initialize(videoUri: Uri, fileName: String, outTreeUri: Uri?) {
        if (initialized) return
        initialized = true
        outputTreeUri = outTreeUri
        viewModelScope.launch {
            val durMs = repository.probeSimpleDuration(videoUri).coerceAtLeast(1_000L)
            val peaks = VideoEditorEngine.extractWaveformPeaks(getApplication(), videoUri, bins = 80)
            val initialVideo = listOf(
                VideoClipSegment(
                    id = UUID.randomUUID().toString(),
                    uri = videoUri,
                    label = fileName,
                    fullDurationMs = durMs,
                    trimStartMs = 0L,
                    trimEndMs = durMs
                )
            )
            val initialAudio = listOf(
                AudioClipSegment(
                    id = UUID.randomUUID().toString(),
                    uri = videoUri,
                    label = fileName,
                    fullDurationMs = durMs,
                    trimStartMs = 0L,
                    trimEndMs = durMs,
                    waveformPeaks = peaks
                )
            )
            _state.value = VideoEditorUiState(
                sourceFileName = fileName,
                videoSegments = initialVideo,
                audioSegments = initialAudio
            )
        }
    }

    private fun pushUndoSnapshot() {
        val cur = _state.value
        undoStack.addLast(
            EditorTimelineSnapshot(
                videoSegments = cur.videoSegments,
                audioSegments = cur.audioSegments,
                textOverlays = cur.textOverlays,
                captions = cur.captions,
                videoTrackOffsetMs = cur.videoTrackOffsetMs,
                audioOffsetMs = cur.audioOffsetMs,
                audioVolume = cur.audioVolume
            )
        )
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
    }

    fun updatePlayhead(ms: Long) {
        val maxDur = _state.value.totalVideoDurationMs.coerceAtLeast(100L)
        _state.update { it.copy(playheadMs = ms.coerceIn(0L, maxDur)) }
    }

    fun togglePlayPause() {
        _state.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun updateZoom(scale: Float) {
        _state.update { it.copy(timelineZoom = scale.coerceIn(0.45f, 10.0f)) }
    }

    fun zoomIn() {
        _state.update { it.copy(timelineZoom = (it.timelineZoom * 1.45f).coerceIn(0.45f, 10.0f)) }
    }

    fun zoomOut() {
        _state.update { it.copy(timelineZoom = (it.timelineZoom / 1.45f).coerceIn(0.45f, 10.0f)) }
    }

    fun selectSegment(isVideo: Boolean, index: Int) {
        _state.update { it.copy(selectedTrackIsVideo = isVideo, selectedSegmentIndex = index) }
    }

    fun beginGestureSnapshot() {
        pushUndoSnapshot()
    }

    fun setAudioVolume(volume: Float) {
        _state.update { it.copy(audioVolume = volume.coerceIn(0f, 1.5f)) }
    }

    fun swapSegments(isVideo: Boolean, fromIndex: Int, toIndex: Int): Boolean {
        var swapped = false
        _state.update { cur ->
            if (isVideo) {
                if (fromIndex == toIndex || fromIndex !in cur.videoSegments.indices || toIndex !in cur.videoSegments.indices) {
                    return@update cur
                }
                val list = cur.videoSegments.toMutableList()
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                swapped = true
                cur.copy(
                    videoSegments = list,
                    selectedTrackIsVideo = true,
                    selectedSegmentIndex = toIndex,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            } else {
                if (fromIndex == toIndex || fromIndex !in cur.audioSegments.indices || toIndex !in cur.audioSegments.indices) {
                    return@update cur
                }
                val list = cur.audioSegments.toMutableList()
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                swapped = true
                cur.copy(
                    audioSegments = list,
                    selectedTrackIsVideo = false,
                    selectedSegmentIndex = toIndex,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
        }
        return swapped
    }

    fun shiftTrackOffset(isVideo: Boolean, deltaMs: Long): Long {
        var newOffset = 0L
        _state.update { cur ->
            if (isVideo) {
                newOffset = (cur.videoTrackOffsetMs + deltaMs).coerceIn(0L, 10_000L)
                cur.copy(videoTrackOffsetMs = newOffset, canUndo = undoStack.isNotEmpty(), canRedo = false)
            } else {
                newOffset = (cur.audioOffsetMs + deltaMs).coerceIn(-5_000L, 10_000L)
                cur.copy(audioOffsetMs = newOffset, canUndo = undoStack.isNotEmpty(), canRedo = false)
            }
        }
        return newOffset
    }

    fun trimSegment(isVideo: Boolean, index: Int, newTrimStartMs: Long, newTrimEndMs: Long) {
        _state.update { cur ->
            if (isVideo) {
                val list = cur.videoSegments.toMutableList()
                val seg = list.getOrNull(index) ?: return@update cur
                val clampedStart = newTrimStartMs.coerceIn(0L, (newTrimEndMs - 150L).coerceAtLeast(0L))
                val clampedEnd = newTrimEndMs.coerceIn((clampedStart + 150L).coerceAtMost(seg.fullDurationMs), seg.fullDurationMs)
                list[index] = seg.copy(trimStartMs = clampedStart, trimEndMs = clampedEnd)
                cur.copy(videoSegments = list, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
            } else {
                val list = cur.audioSegments.toMutableList()
                val seg = list.getOrNull(index) ?: return@update cur
                val clampedStart = newTrimStartMs.coerceIn(0L, (newTrimEndMs - 150L).coerceAtLeast(0L))
                val clampedEnd = newTrimEndMs.coerceIn((clampedStart + 150L).coerceAtMost(seg.fullDurationMs), seg.fullDurationMs)
                list[index] = seg.copy(trimStartMs = clampedStart, trimEndMs = clampedEnd)
                cur.copy(audioSegments = list, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
            }
        }
    }

    fun cutAtPlayhead(): Boolean {
        val cur = _state.value
        val targetMs = cur.playheadMs
        pushUndoSnapshot()

        if (cur.selectedTrackIsVideo) {
            var acc = cur.videoTrackOffsetMs
            val nextList = mutableListOf<VideoClipSegment>()
            var didCut = false
            for (seg in cur.videoSegments) {
                val segDur = seg.activeDurationMs
                if (!didCut && targetMs > acc + 120L && targetMs < acc + segDur - 120L) {
                    val splitSourceMs = seg.trimStartMs + (targetMs - acc)
                    nextList += seg.copy(id = UUID.randomUUID().toString(), trimEndMs = splitSourceMs)
                    nextList += seg.copy(id = UUID.randomUUID().toString(), trimStartMs = splitSourceMs)
                    didCut = true
                } else {
                    nextList += seg
                }
                acc += segDur
            }
            if (!didCut) {
                undoStack.removeLastOrNull()
                return false
            }
            _state.update {
                it.copy(videoSegments = nextList, canUndo = undoStack.isNotEmpty(), canRedo = false)
            }
            return true
        } else {
            var acc = cur.audioOffsetMs.coerceAtLeast(0L)
            val nextList = mutableListOf<AudioClipSegment>()
            var didCut = false
            for (seg in cur.audioSegments) {
                val segDur = seg.activeDurationMs
                if (!didCut && targetMs > acc + 120L && targetMs < acc + segDur - 120L) {
                    val splitSourceMs = seg.trimStartMs + (targetMs - acc)
                    val ratio = ((splitSourceMs - seg.trimStartMs).toFloat() / segDur.toFloat()).coerceIn(0.1f, 0.9f)
                    val splitBin = (seg.waveformPeaks.size * ratio).toInt().coerceIn(1, (seg.waveformPeaks.size - 1).coerceAtLeast(1))
                    val leftPeaks = if (seg.waveformPeaks.size > 2) seg.waveformPeaks.subList(0, splitBin) else seg.waveformPeaks
                    val rightPeaks = if (seg.waveformPeaks.size > 2) seg.waveformPeaks.subList(splitBin, seg.waveformPeaks.size) else seg.waveformPeaks
                    nextList += seg.copy(id = UUID.randomUUID().toString(), trimEndMs = splitSourceMs, waveformPeaks = leftPeaks)
                    nextList += seg.copy(id = UUID.randomUUID().toString(), trimStartMs = splitSourceMs, waveformPeaks = rightPeaks)
                    didCut = true
                } else {
                    nextList += seg
                }
                acc += segDur
            }
            if (!didCut) {
                undoStack.removeLastOrNull()
                return false
            }
            _state.update {
                it.copy(audioSegments = nextList, canUndo = undoStack.isNotEmpty(), canRedo = false)
            }
            return true
        }
    }

    fun deleteSelectedSegment(): Boolean {
        val cur = _state.value
        if (cur.selectedTrackIsVideo) {
            if (cur.videoSegments.size <= 1) return false
            val idx = cur.selectedSegmentIndex.coerceIn(0, cur.videoSegments.lastIndex)
            pushUndoSnapshot()
            val updated = cur.videoSegments.toMutableList().apply { removeAt(idx) }
            _state.update {
                it.copy(
                    videoSegments = updated,
                    selectedSegmentIndex = idx.coerceAtMost(updated.lastIndex),
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
            return true
        } else {
            if (cur.audioSegments.isEmpty()) return false
            val idx = cur.selectedSegmentIndex.coerceIn(0, cur.audioSegments.lastIndex)
            pushUndoSnapshot()
            val updated = cur.audioSegments.toMutableList().apply { removeAt(idx) }
            _state.update {
                it.copy(
                    audioSegments = updated,
                    selectedSegmentIndex = (idx - 1).coerceAtLeast(0),
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
            return true
        }
    }

    fun undo(): Boolean {
        val snap = undoStack.removeLastOrNull() ?: return false
        val cur = _state.value
        redoStack.addLast(
            EditorTimelineSnapshot(
                cur.videoSegments,
                cur.audioSegments,
                cur.textOverlays,
                cur.captions,
                cur.videoTrackOffsetMs,
                cur.audioOffsetMs,
                cur.audioVolume
            )
        )
        _state.update {
            it.copy(
                videoSegments = snap.videoSegments,
                audioSegments = snap.audioSegments,
                textOverlays = snap.textOverlays,
                captions = snap.captions,
                videoTrackOffsetMs = snap.videoTrackOffsetMs,
                audioOffsetMs = snap.audioOffsetMs,
                audioVolume = snap.audioVolume,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        return true
    }

    fun redo(): Boolean {
        val snap = redoStack.removeLastOrNull() ?: return false
        val cur = _state.value
        undoStack.addLast(
            EditorTimelineSnapshot(
                cur.videoSegments,
                cur.audioSegments,
                cur.textOverlays,
                cur.captions,
                cur.videoTrackOffsetMs,
                cur.audioOffsetMs,
                cur.audioVolume
            )
        )
        _state.update {
            it.copy(
                videoSegments = snap.videoSegments,
                audioSegments = snap.audioSegments,
                textOverlays = snap.textOverlays,
                captions = snap.captions,
                videoTrackOffsetMs = snap.videoTrackOffsetMs,
                audioOffsetMs = snap.audioOffsetMs,
                audioVolume = snap.audioVolume,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        return true
    }

    fun addFootageClip(uri: Uri, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val durMs = repository.probeSimpleDuration(uri).coerceAtLeast(500L)
            val label = queryDisplayName(uri) ?: "footage.mp4"
            pushUndoSnapshot()
            _state.update { cur ->
                val updated = cur.videoSegments + VideoClipSegment(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    label = label,
                    fullDurationMs = durMs
                )
                cur.copy(
                    videoSegments = updated,
                    selectedTrackIsVideo = true,
                    selectedSegmentIndex = updated.lastIndex,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
            onDone()
        }
    }

    fun addAudioClip(uri: Uri, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val durMs = repository.probeSimpleDuration(uri).coerceAtLeast(500L)
            val peaks = VideoEditorEngine.extractWaveformPeaks(getApplication(), uri, bins = 64)
            val label = queryDisplayName(uri) ?: "audio"
            pushUndoSnapshot()
            _state.update { cur ->
                val updated = cur.audioSegments + AudioClipSegment(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    label = label,
                    fullDurationMs = durMs,
                    waveformPeaks = peaks
                )
                cur.copy(
                    audioSegments = updated,
                    selectedTrackIsVideo = false,
                    selectedSegmentIndex = updated.lastIndex,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
            onDone()
        }
    }

    fun addTextOverlay(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val cur = _state.value
        val maxDur = cur.totalVideoDurationMs.coerceAtLeast(1000L)
        val start = cur.playheadMs.coerceIn(0L, (maxDur - 400L).coerceAtLeast(0L))
        val end = (start + 3_000L).coerceAtMost(maxDur)
        pushUndoSnapshot()
        _state.update {
            it.copy(
                textOverlays = it.textOverlays + TextOverlayItem(
                    id = UUID.randomUUID().toString(),
                    text = clean,
                    startMs = start,
                    endMs = end
                ),
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
        return true
    }

    fun addManualCaption(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val cur = _state.value
        val maxDur = cur.totalVideoDurationMs.coerceAtLeast(1000L)
        val start = cur.playheadMs.coerceIn(0L, (maxDur - 400L).coerceAtLeast(0L))
        val end = (start + 2_500L).coerceAtMost(maxDur)
        pushUndoSnapshot()
        _state.update {
            it.copy(
                captions = it.captions + CaptionSegment(
                    id = UUID.randomUUID().toString(),
                    text = clean,
                    startMs = start,
                    endMs = end,
                    languageCode = cur.detectedAudioLanguage
                ),
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
        return true
    }

    fun generateSmartAutoCaptions(
        formatDone: (String, Int) -> String,
        onToast: (String) -> Unit = {}
    ) {
        val cur = _state.value
        if (cur.audioSegments.isEmpty() || cur.isGeneratingCaptions) return
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingCaptions = true, captionStatusMessage = null) }
            val result = VideoEditorEngine.generateSmartAutoCaptions(
                context = getApplication(),
                audioSegments = cur.audioSegments,
                totalVideoMs = cur.totalVideoDurationMs.coerceAtLeast(1_500L),
                preferredLang = "auto"
            )
            pushUndoSnapshot()
            val msg = formatDone(result.detectedLanguage, result.captions.size)
            _state.update {
                it.copy(
                    captions = result.captions,
                    detectedAudioLanguage = result.detectedLanguage,
                    isGeneratingCaptions = false,
                    captionStatusMessage = msg,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
            onToast(msg)
        }
    }

    fun runSmartAutoSync(
        formatMessage: (Long) -> String,
        onToast: (String) -> Unit = {}
    ) {
        val cur = _state.value
        val vUri = cur.videoSegments.firstOrNull()?.uri ?: return
        val aUri = cur.audioSegments.firstOrNull()?.uri
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzingSync = true, syncStatusMessage = null) }
            val result = VideoEditorEngine.analyzeSmartAvSync(getApplication(), vUri, aUri)
            pushUndoSnapshot()
            val msg = formatMessage(result.recommendedOffsetMs)
            _state.update {
                it.copy(
                    audioOffsetMs = result.recommendedOffsetMs,
                    isAnalyzingSync = false,
                    syncStatusMessage = msg,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
            onToast(msg)
        }
    }

    fun nudgeAudioOffset(deltaMs: Long): Long {
        pushUndoSnapshot()
        val next = (_state.value.audioOffsetMs + deltaMs).coerceIn(-5_000L, 10_000L)
        _state.update {
            it.copy(
                audioOffsetMs = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
        return next
    }

    fun saveEditedMp4(customFileName: String?, onSaved: () -> Unit) {
        val cur = _state.value
        if (cur.videoSegments.isEmpty() || cur.exportProgress != null) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val tempFile = File(ctx.cacheDir, "edited_${System.currentTimeMillis()}.mp4")
            try {
                _state.update { it.copy(exportProgress = 1, exportError = null) }
                VideoEditorEngine.exportTimeline(
                    context = ctx,
                    videoSegments = cur.videoSegments,
                    audioSegments = cur.audioSegments,
                    audioOffsetMs = cur.audioOffsetMs,
                    outputFile = tempFile,
                    textOverlays = cur.textOverlays,
                    captions = cur.captions,
                    audioVolume = cur.audioVolume,
                    onProgress = { pct ->
                        _state.update { it.copy(exportProgress = pct.coerceIn(1, 99)) }
                    }
                )
                val sanitized = customFileName?.trim()?.replace(Regex("[/\\:*?\"<>|]"), "_")
                val targetBase = if (!sanitized.isNullOrEmpty()) sanitized else cur.defaultEditedFileName
                val finalOutName = if (targetBase.endsWith(".mp4", ignoreCase = true)) {
                    targetBase
                } else {
                    "${targetBase}.mp4"
                }
                repository.saveTempMp4ToOutput(tempFile, finalOutName, outputTreeUri)
                _state.update { it.copy(exportProgress = null) }
                onSaved()
            } catch (e: Exception) {
                _state.update { it.copy(exportProgress = null, exportError = e.message ?: "Export error") }
            } finally {
                runCatching { if (tempFile.exists()) tempFile.delete() }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
