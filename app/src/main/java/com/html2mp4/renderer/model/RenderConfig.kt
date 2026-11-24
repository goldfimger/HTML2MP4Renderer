package com.html2mp4.renderer.model

import android.media.MediaFormat
import android.net.Uri

enum class VideoOrientation(val label: String) {
    LANDSCAPE("Lanskap (16:9)"),
    PORTRAIT("Vertikal (9:16)")
}

enum class ResolutionPreset(val label: String, val longSide: Int, val shortSide: Int) {
    P480("480p", 854, 480),
    P720("720p HD", 1280, 720),
    P1080("1080p FHD", 1920, 1080),
    CUSTOM("Custom", 1280, 720)
}

enum class FrameRateOption(val fps: Int, val label: String) {
    FPS_24(24, "24 FPS (Sinematik)"),
    FPS_30(30, "30 FPS (Optimal Hardware)"),
    FPS_60(60, "60 FPS (Halus)")
}

enum class VideoCodecOption(val label: String, val mimeType: String) {
    H264("H.264 / AVC (Optimal Hardware)", MediaFormat.MIMETYPE_VIDEO_AVC),
    H265("H.265 / HEVC (Efisien)", MediaFormat.MIMETYPE_VIDEO_HEVC)
}

enum class VideoBitratePreset(val label: String, val kbps: Int?) {
    AUTO("Auto (Adaptif Resolusi)", null),
    LOW("Rendah (2.5 Mbps)", 2500),
    MEDIUM("Sedang (5 Mbps)", 5000),
    HIGH("Tinggi (10 Mbps)", 10000),
    CUSTOM("Custom Kbps", null)
}

enum class AudioCodecOption(val label: String, val mimeType: String) {
    AAC("AAC-LC (Standar MP4)", MediaFormat.MIMETYPE_AUDIO_AAC),
    MP3("MP3 (Fallback AAC jika tidak didukung)", MediaFormat.MIMETYPE_AUDIO_MPEG)
}

enum class AudioSampleRate(val hz: Int, val label: String) {
    HZ_44100(44100, "44.1 kHz"),
    HZ_48000(48000, "48 kHz")
}

enum class AudioBitrateOption(val kbps: Int, val label: String) {
    KBPS_128(128, "128 kbps"),
    KBPS_192(192, "192 kbps"),
    KBPS_320(320, "320 kbps")
}

enum class AudioChannelOption(val count: Int, val label: String) {
    MONO(1, "Mono (1 ch)"),
    STEREO(2, "Stereo (2 ch)")
}

enum class ThemeMode(val label: String) {
    SYSTEM("Otomatis Sistem"),
    LIGHT("Mode Terang"),
    DARK("Mode Gelap")
}

enum class AppLanguage(val label: String) {
    SYSTEM("Sistem / System"),
    INDONESIAN("Bahasa Indonesia"),
    ENGLISH("English")
}

data class HtmlProjectMetadata(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val detectedDurationMs: Long,
    val detectedWidth: Int = 1280,
    val detectedHeight: Int = 720,
    val detectedOrientation: VideoOrientation = VideoOrientation.LANDSCAPE,
    val assetsTreeUri: Uri? = null,
    val isDurationAutoDetected: Boolean = false
)

enum class RenderTimelineMode(val label: String) {
    DETERMINISTIC("Deterministik (Presisi Frame)"),
    REALTIME_COMPAT("Kompatibilitas Real-Time (Klasik)")
}

data class HtmlAnimationCapabilities(
    val usesRaf: Boolean = false,
    val usesWebAnimationsOrCss: Boolean = false,
    val usesTimers: Boolean = false,
    val usesGsapOrOpener: Boolean = false,
    val hasMediaElements: Boolean = false,
    val hasWebAudio: Boolean = false,
    val hasCrossOriginIframes: Boolean = false,
    val notes: List<String> = emptyList()
) {
    val fullyDeterministicVisuals: Boolean
        get() = !hasCrossOriginIframes && !hasWebAudio
}

data class AudioSyncSummary(
    val tapEngine: String = "ScriptProcessorNode",
    val tapEngineNote: String? = null,
    val receivedChunks: Long = 0L,
    val droppedOverflowChunks: Long = 0L,
    val missingSequenceChunks: Long = 0L,
    val invalidFormatChunks: Long = 0L,
    val adaptedFormatChunks: Long = 0L,
    val queuedPeakSize: Int = 0,
    val targetSampleRateHz: Int = 48000,
    val targetChannels: Int = 2,
    val gapSilencesInserted: Int = 0,
    val silencePaddedMs: Long = 0L,
    val maxAvDriftMs: Long = 0L,
    val finalAvDriftMs: Long = 0L
)

enum class RenderPhase(val labelId: String, val labelEn: String) {
    INITIALIZING("Inisialisasi Pipeline", "Pipeline Initialization"),
    MEDIA_CODEC_PREPARE("Persiapan Encoder MediaCodec", "MediaCodec Encoder Prepare"),
    EGL_SURFACE_PREPARE("Persiapan Surface EGL / OpenGL", "EGL / OpenGL Surface Prepare"),
    WEBVIEW_LOAD("Memuat HTML di WebView", "Loading HTML in WebView"),
    FRAME_CAPTURE("Pengambilan & Encoding Frame", "Frame Capture & Encoding"),
    AUDIO_MUXING("Sinkronisasi & Encoding Audio", "Audio Sync & Encoding"),
    MP4_FINALIZE("Finalisasi Muxer MP4", "MP4 Muxer Finalization"),
    MP4_VALIDATION("Validasi Track & Durasi MP4", "MP4 Track & Duration Validation"),
    OUTPUT_SAVE("Menyimpan MP4 ke Penyimpanan", "Saving MP4 to Storage")
}

class RenderPipelineException(
    val phase: RenderPhase,
    val technicalDetail: String?,
    cause: Throwable? = null
) : RuntimeException(technicalDetail ?: phase.labelId, cause)

data class MemoryDiagnostics(
    val peakUsedRamMb: Long = 0L,
    val minAvailableRamMb: Long = 0L,
    val lowMemoryThrottleCount: Int = 0
)

data class Mp4ValidationReport(
    val fileExists: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val hasVideoTrack: Boolean = false,
    val hasAudioTrack: Boolean = false,
    val audioExpected: Boolean = true,
    val videoMime: String? = null,
    val audioMime: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val measuredFps: Float = 0f,
    val actualDurationMs: Long = 0L,
    val expectedDurationMs: Long = 0L,
    val durationDeltaMs: Long = 0L,
    val audioSampleRateHz: Int = 0,
    val audioChannels: Int = 0,
    val firstKeyFrameDecoded: Boolean = false,
    val isPlayable: Boolean = false,
    val warnings: List<String> = emptyList()
)

data class RenderDiagnosticsReport(
    val timelineMode: RenderTimelineMode = RenderTimelineMode.DETERMINISTIC,
    val targetFps: Int = 30,
    val expectedFrames: Int = 0,
    val submittedFrames: Int = 0,
    val repeatedFrames: Int = 0,
    val droppedOrFailedFrames: Int = 0,
    val renderWallDurationMs: Long = 0L,
    val videoWidth: Int = 1280,
    val videoHeight: Int = 720,
    val videoEncoderName: String = "Hardware MediaCodec",
    val audioEncoderName: String = "AAC-LC",
    val mp4FileSizeBytes: Long = 0L,
    val usedBitmapFallback: Boolean = false,
    val memory: MemoryDiagnostics = MemoryDiagnostics(),
    val audioSummary: AudioSyncSummary? = null,
    val validation: Mp4ValidationReport? = null
)

data class RenderTimelineSummary(
    val timelineMode: RenderTimelineMode = RenderTimelineMode.DETERMINISTIC,
    val targetFps: Int = 30,
    val expectedFrames: Int = 0,
    val submittedFrames: Int = 0,
    val repeatedFrames: Int = 0,
    val droppedOrFailedFrames: Int = 0,
    val usedBitmapFallback: Boolean = false,
    val renderWallDurationMs: Long = 0L,
    val capabilities: HtmlAnimationCapabilities = HtmlAnimationCapabilities(),
    val audioSummary: AudioSyncSummary? = null,
    val diagnostics: RenderDiagnosticsReport? = null
)

data class RenderConfig(
    val orientation: VideoOrientation = VideoOrientation.LANDSCAPE,
    val resolutionPreset: ResolutionPreset = ResolutionPreset.P720,
    val customWidth: Int = 1280,
    val customHeight: Int = 720,
    val frameRate: FrameRateOption = FrameRateOption.FPS_30,
    val timelineMode: RenderTimelineMode = RenderTimelineMode.DETERMINISTIC,
    val videoCodec: VideoCodecOption = VideoCodecOption.H264,
    val bitratePreset: VideoBitratePreset = VideoBitratePreset.AUTO,
    val customBitrateKbps: Int = 6000,
    val isAudioMuted: Boolean = false,
    val audioCodec: AudioCodecOption = AudioCodecOption.AAC,
    val audioSampleRate: AudioSampleRate = AudioSampleRate.HZ_48000,
    val audioBitrate: AudioBitrateOption = AudioBitrateOption.KBPS_192,
    val audioChannels: AudioChannelOption = AudioChannelOption.STEREO,
    val outputTreeUri: Uri? = null,
    val outputFolderDisplay: String = "/Movies/HTML2MP4/",
    val durationOverrideSec: Int? = null
) {
    val resolvedWidth: Int
        get() = alignToEven(
            when {
                resolutionPreset == ResolutionPreset.CUSTOM -> customWidth.coerceIn(320, 1920)
                orientation == VideoOrientation.PORTRAIT -> resolutionPreset.shortSide
                else -> resolutionPreset.longSide
            }
        )

    val resolvedHeight: Int
        get() = alignToEven(
            when {
                resolutionPreset == ResolutionPreset.CUSTOM -> customHeight.coerceIn(240, 1920)
                orientation == VideoOrientation.PORTRAIT -> resolutionPreset.longSide
                else -> resolutionPreset.shortSide
            }
        )

    val resolvedVideoBitrateBps: Int
        get() {
            val kbps = when (bitratePreset) {
                VideoBitratePreset.AUTO -> {
                    val pixels = resolvedWidth * resolvedHeight
                    val base = when {
                        pixels <= 854 * 480 -> 1_800
                        pixels <= 1280 * 720 -> 3_500
                        else -> 5_500
                    }
                    val fpsScaled = if (frameRate == FrameRateOption.FPS_60) (base * 1.35f) else base.toFloat()
                    val codecScaled = if (videoCodec == VideoCodecOption.H265) (fpsScaled * 0.72f) else fpsScaled
                    codecScaled.toInt()
                }
                VideoBitratePreset.CUSTOM -> customBitrateKbps.coerceIn(500, 25_000)
                else -> bitratePreset.kbps ?: 5_000
            }
            return kbps * 1_000
        }

    private fun alignToEven(value: Int): Int = (value / 2) * 2
}

sealed interface RenderStatus {
    data object Idle : RenderStatus
    data class Preparing(val message: String) : RenderStatus
    data class Rendering(
        val progressPercent: Int,
        val currentFrame: Int,
        val totalFrames: Int,
        val etaFormatted: String,
        val elapsedMs: Long,
        val phaseLabel: String
    ) : RenderStatus
    data class Completed(
        val outputUri: Uri,
        val fileName: String,
        val durationMs: Long,
        val timelineSummary: RenderTimelineSummary? = null
    ) : RenderStatus
    data class Cancelled(val reason: String) : RenderStatus
    data class Failed(
        val errorMessage: String,
        val failedPhase: RenderPhase? = null,
        val technicalDetail: String? = null
    ) : RenderStatus
}

data class RenderedVideoItem(
    val uri: Uri,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val dateAddedSec: Long
)

data class VideoDetailInfo(
    val fileName: String,
    val uriString: String,
    val width: Int,
    val height: Int,
    val aspectRatioLabel: String,
    val orientationLabel: String,
    val durationMs: Long,
    val fps: Float,
    val videoCodec: String,
    val videoBitrateBps: Long,
    val audioCodec: String,
    val audioSampleRateHz: Int,
    val audioChannels: Int,
    val audioBitrateBps: Long,
    val fileSizeBytes: Long,
    val dateAddedSec: Long
)

data class VideoClipSegment(
    val id: String,
    val uri: Uri,
    val label: String,
    val fullDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = fullDurationMs,
    val laneOffsetMs: Long = 0L
) {
    val activeDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(100L)
}

data class AudioClipSegment(
    val id: String,
    val uri: Uri,
    val label: String,
    val fullDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = fullDurationMs,
    val laneOffsetMs: Long = 0L,
    val waveformPeaks: List<Float> = emptyList()
) {
    val activeDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(100L)
}

data class SmartSyncResult(
    val recommendedOffsetMs: Long,
    val firstMotionMs: Long,
    val firstAudioOnsetMs: Long,
    val containerSkewMs: Long
)

data class TextOverlayItem(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val fontSizeSp: Float = 22f
)

data class CaptionSegment(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val languageCode: String = "id"
)

data class AutoCaptionResult(
    val captions: List<CaptionSegment>,
    val detectedLanguage: String
)
