package com.html2mp4.renderer.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.html2mp4.renderer.data.RenderRepository
import com.html2mp4.renderer.engine.WebViewOffscreenRenderer
import com.html2mp4.renderer.model.AppLanguage
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
import com.html2mp4.renderer.model.VideoDetailInfo
import com.html2mp4.renderer.model.VideoOrientation
import com.html2mp4.renderer.service.RenderForegroundService
import com.html2mp4.renderer.util.ChipsetOptimizer
import com.html2mp4.renderer.util.DeviceProfile
import com.html2mp4.renderer.util.MemoryGuard
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppCacheStats(
    val webViewBytes: Long = 0L,
    val tempRenderBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isClearing: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("html2mp4_prefs", Context.MODE_PRIVATE)
    private val repository = RenderRepository(application)
    private val memoryGuard = MemoryGuard(application)
    private val probeRenderer = WebViewOffscreenRenderer(application, memoryGuard)

    val deviceProfile: DeviceProfile = ChipsetOptimizer.detect(application)

    private val _themeMode = MutableStateFlow(loadSavedThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _appLanguage = MutableStateFlow(loadSavedLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

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

    private val _selectedVideoInfo = MutableStateFlow<VideoDetailInfo?>(null)
    val selectedVideoInfo: StateFlow<VideoDetailInfo?> = _selectedVideoInfo.asStateFlow()

    private val _isLoadingVideoInfo = MutableStateFlow(false)
    val isLoadingVideoInfo: StateFlow<Boolean> = _isLoadingVideoInfo.asStateFlow()

    private val _cacheStats = MutableStateFlow(AppCacheStats())
    val cacheStats: StateFlow<AppCacheStats> = _cacheStats.asStateFlow()

    val renderStatus: StateFlow<RenderStatus> = RenderForegroundService.status

    init {
        refreshRenderedVideos()
        refreshCacheStats()
        viewModelScope.launch {
            renderStatus.collect { status ->
                if (status is RenderStatus.Completed) {
                    refreshRenderedVideos()
                }
            }
        }
    }

    private fun loadSavedThemeMode(): ThemeMode {
        val raw = prefs.getString("theme_mode", null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    private fun loadSavedLanguage(): AppLanguage {
        val raw = prefs.getString("app_language", null) ?: return AppLanguage.INDONESIAN
        return AppLanguage.entries.firstOrNull { it.name == raw } ?: AppLanguage.INDONESIAN
    }

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        prefs.edit().putString("app_language", language.name).apply()
    }

    fun cycleThemeMode() {
        _themeMode.update { current ->
            val next = when (current) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            prefs.edit().putString("theme_mode", next.name).apply()
            next
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
            val probe = runCatching {
                probeRenderer.inspectProjectMetadata(uri, baseMeta.assetsTreeUri)
            }.getOrNull()

            if (probe != null) {
                _projectMetadata.update {
                    it?.copy(
                        detectedDurationMs = probe.durationMs,
                        detectedWidth = probe.width,
                        detectedHeight = probe.height,
                        detectedOrientation = probe.orientation,
                        isDurationAutoDetected = true
                    )
                }
                applyAutoDetectedResolution(probe.width, probe.height, probe.orientation)
            }
            _isProbingDuration.value = false
        }
    }

    private fun applyAutoDetectedResolution(
        width: Int,
        height: Int,
        orientation: VideoOrientation
    ) {
        val shortSide = minOf(width, height)
        val longSide = maxOf(width, height)
        val aspect = longSide.toFloat() / shortSide.coerceAtLeast(1).toFloat()

        // If aspect ratio matches ~16:9 / 9:16 (1.6 - 1.9), map cleanly to 480p/720p/1080p preset
        if (aspect in 1.55f..1.95f) {
            val preset = when {
                shortSide >= 1000 -> ResolutionPreset.P1080
                shortSide >= 640 -> ResolutionPreset.P720
                else -> ResolutionPreset.P480
            }
            _config.update {
                it.copy(
                    orientation = orientation,
                    resolutionPreset = preset,
                    customWidth = width,
                    customHeight = height
                )
            }
        } else {
            // Custom aspect ratio (e.g. 4:5 or 1:1.2) -> apply exact Custom dimensions
            _config.update {
                it.copy(
                    orientation = orientation,
                    resolutionPreset = ResolutionPreset.CUSTOM,
                    customWidth = width.coerceIn(320, 1920),
                    customHeight = height.coerceIn(240, 1920)
                )
            }
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

    fun updateOrientation(orientation: VideoOrientation) {
        _config.update { current ->
            if (current.resolutionPreset == ResolutionPreset.CUSTOM) {
                val w = current.customWidth
                val h = current.customHeight
                val swapped = if (orientation == VideoOrientation.PORTRAIT && w > h) Pair(h, w)
                else if (orientation == VideoOrientation.LANDSCAPE && h > w) Pair(h, w)
                else Pair(w, h)
                current.copy(
                    orientation = orientation,
                    customWidth = swapped.first,
                    customHeight = swapped.second
                )
            } else {
                current.copy(orientation = orientation)
            }
        }
    }

    fun updateResolution(preset: ResolutionPreset) {
        _config.update { it.copy(resolutionPreset = preset) }
    }

    fun updateCustomDimensions(width: Int, height: Int) {
        val inferred = if (height > width) VideoOrientation.PORTRAIT else VideoOrientation.LANDSCAPE
        _config.update { it.copy(customWidth = width, customHeight = height, orientation = inferred) }
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

    fun loadVideoInfo(item: RenderedVideoItem) {
        viewModelScope.launch {
            _isLoadingVideoInfo.value = true
            _selectedVideoInfo.value = repository.probeDetailedVideoInfo(item)
            _isLoadingVideoInfo.value = false
        }
    }

    fun dismissVideoInfo() {
        _selectedVideoInfo.value = null
        _isLoadingVideoInfo.value = false
    }

    fun availableRamMb(): Long = memoryGuard.availableMemoryMb()

    fun refreshCacheStats() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) { computeCacheStats(ctx) }
            _cacheStats.value = stats
        }
    }

    fun clearAppCache() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _cacheStats.update { it.copy(isClearing = true) }
            withContext(Dispatchers.IO) {
                deleteDirContents(ctx.cacheDir)
                deleteDirContents(ctx.codeCacheDir)
                ctx.externalCacheDir?.let { deleteDirContents(it) }
                File(ctx.dataDir, "app_webview/Cache").takeIf { it.exists() }?.let { deleteDirContents(it) }
            }
            val updated = withContext(Dispatchers.IO) { computeCacheStats(ctx) }
            _cacheStats.value = updated.copy(isClearing = false)
        }
    }

    private fun computeCacheStats(ctx: Context): AppCacheStats {
        val webViewDir = File(ctx.dataDir, "app_webview")
        val webViewBytes = dirSizeBytes(webViewDir) + dirSizeBytes(File(ctx.cacheDir, "WebView"))
        val cacheTotal = dirSizeBytes(ctx.cacheDir) + dirSizeBytes(ctx.codeCacheDir) +
            (ctx.externalCacheDir?.let { dirSizeBytes(it) } ?: 0L)
        val tempRenderBytes = (cacheTotal - dirSizeBytes(File(ctx.cacheDir, "WebView"))).coerceAtLeast(0L)
        return AppCacheStats(
            webViewBytes = webViewBytes,
            tempRenderBytes = tempRenderBytes,
            totalBytes = webViewBytes + tempRenderBytes
        )
    }

    private fun dirSizeBytes(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var sum = 0L
        file.listFiles()?.forEach { child ->
            sum += dirSizeBytes(child)
        }
        return sum
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteDirContents(child)
            }
            runCatching { child.delete() }
        }
    }
}
