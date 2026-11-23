package com.html2mp4.renderer.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.html2mp4.renderer.data.RenderRepository
import com.html2mp4.renderer.engine.WebViewOffscreenRenderer
import com.html2mp4.renderer.model.AudioBitrateOption
import com.html2mp4.renderer.model.AudioChannelOption
import com.html2mp4.renderer.model.AudioCodecOption
import com.html2mp4.renderer.model.AudioSampleRate
import com.html2mp4.renderer.model.FrameRateOption
import com.html2mp4.renderer.model.HtmlProjectMetadata
import com.html2mp4.renderer.model.RenderConfig
import com.html2mp4.renderer.model.RenderStatus
import com.html2mp4.renderer.model.RenderedVideoItem
import com.html2mp4.renderer.model.ResolutionPreset
import com.html2mp4.renderer.model.ThemeMode
import com.html2mp4.renderer.model.VideoBitratePreset
import com.html2mp4.renderer.model.VideoCodecOption
import com.html2mp4.renderer.service.RenderForegroundService
import com.html2mp4.renderer.util.MemoryGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RenderRepository(application)
    private val memoryGuard = MemoryGuard(application)
    private val probeRenderer = WebViewOffscreenRenderer(application, memoryGuard)

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _projectMetadata = MutableStateFlow<HtmlProjectMetadata?>(null)
    val projectMetadata: StateFlow<HtmlProjectMetadata?> = _projectMetadata.asStateFlow()

    private val _isProbingDuration = MutableStateFlow(false)
    val isProbingDuration: StateFlow<Boolean> = _isProbingDuration.asStateFlow()

    private val _config = MutableStateFlow(RenderConfig())
    val config: StateFlow<RenderConfig> = _config.asStateFlow()

    private val _renderedVideos = MutableStateFlow<List<RenderedVideoItem>>(emptyList())
    val renderedVideos: StateFlow<List<RenderedVideoItem>> = _renderedVideos.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<Uri, Bitmap?>>(emptyMap())
    val thumbnails: StateFlow<Map<Uri, Bitmap?>> = _thumbnails.asStateFlow()

    val renderStatus: StateFlow<RenderStatus> = RenderForegroundService.status

    init {
        refreshRenderedVideos()
        viewModelScope.launch {
            renderStatus.collect { status ->
                if (status is RenderStatus.Completed) {
                    refreshRenderedVideos()
                }
            }
        }
    }

    fun cycleThemeMode() {
        _themeMode.update { current ->
            when (current) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
        }
    }

    fun onHtmlFileSelected(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModelScope.launch {
            val baseMeta = repository.readHtmlMetadata(uri, _projectMetadata.value?.assetsTreeUri)
            _projectMetadata.value = baseMeta
            _isProbingDuration.value = true
            val detectedMs = runCatching {
                probeRenderer.inspectDurationOnly(uri, baseMeta.assetsTreeUri)
            }.getOrDefault(10_000L)
            _projectMetadata.update {
                it?.copy(detectedDurationMs = detectedMs, isDurationAutoDetected = true)
            }
            _isProbingDuration.value = false
        }
    }

    fun onAssetsFolderSelected(treeUri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        _projectMetadata.update { it?.copy(assetsTreeUri = treeUri) }
    }

    fun onOutputFolderSelected(treeUri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val docName = DocumentFile.fromTreeUri(ctx, treeUri)?.name ?: "Folder Pilihan"
        _config.update {
            it.copy(
                outputTreeUri = treeUri,
                outputFolderDisplay = "/$docName/"
            )
        }
        refreshRenderedVideos()
    }

    fun updateResolution(preset: ResolutionPreset) {
        _config.update { it.copy(resolutionPreset = preset) }
    }

    fun updateCustomDimensions(width: Int, height: Int) {
        _config.update { it.copy(customWidth = width, customHeight = height) }
    }

    fun updateFrameRate(fps: FrameRateOption) {
        _config.update { it.copy(frameRate = fps) }
    }

    fun updateVideoCodec(codec: VideoCodecOption) {
        _config.update { it.copy(videoCodec = codec) }
    }

    fun updateVideoBitratePreset(preset: VideoBitratePreset) {
        _config.update { it.copy(bitratePreset = preset) }
    }

    fun updateCustomBitrate(kbps: Int) {
        _config.update { it.copy(customBitrateKbps = kbps) }
    }

    fun updateAudioMuted(muted: Boolean) {
        _config.update { it.copy(isAudioMuted = muted) }
    }

    fun updateAudioCodec(codec: AudioCodecOption) {
        _config.update { it.copy(audioCodec = codec) }
    }

    fun updateAudioSampleRate(rate: AudioSampleRate) {
        _config.update { it.copy(audioSampleRate = rate) }
    }

    fun updateAudioBitrate(bitrate: AudioBitrateOption) {
        _config.update { it.copy(audioBitrate = bitrate) }
    }

    fun updateAudioChannels(channels: AudioChannelOption) {
        _config.update { it.copy(audioChannels = channels) }
    }

    fun startRender() {
        val meta = _projectMetadata.value ?: return
        val cfg = _config.value
        val durationMs = cfg.durationOverrideSec?.times(1000L) ?: meta.detectedDurationMs

        RenderForegroundService.startRender(
            context = getApplication(),
            request = RenderForegroundService.RenderJobRequest(
                htmlUri = meta.uri,
                assetsTreeUri = meta.assetsTreeUri,
                sourceFileName = meta.fileName,
                durationMs = durationMs,
                config = cfg
            )
        )
    }

    fun cancelRender() {
        RenderForegroundService.cancelRender(getApplication())
    }

    fun dismissCompletedBanner() {
        RenderForegroundService.resetStatus()
    }

    fun refreshRenderedVideos() {
        viewModelScope.launch {
            val items = repository.loadRenderedVideos(_config.value.outputTreeUri)
            _renderedVideos.value = items
            items.take(20).forEach { item ->
                if (!_thumbnails.value.containsKey(item.uri)) {
                    val bmp = repository.extractVideoThumbnail(item.uri)
                    _thumbnails.update { it + (item.uri to bmp) }
                }
            }
        }
    }

    fun availableRamMb(): Long = memoryGuard.availableMemoryMb()
}
