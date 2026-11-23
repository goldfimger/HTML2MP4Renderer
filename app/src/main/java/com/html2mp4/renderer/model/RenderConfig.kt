package com.html2mp4.renderer.model

import android.media.MediaFormat
import android.net.Uri

enum class ResolutionPreset(val label: String, val width: Int, val height: Int) {
    P480("480p (854×480)", 854, 480),
    P720("720p HD (1280×720)", 1280, 720),
    P1080("1080p FHD (1920×1080)", 1920, 1080),
    CUSTOM("Custom", 1280, 720)
}

enum class FrameRateOption(val fps: Int, val label: String) {
    FPS_24(24, "24 FPS (Sinematik)"),
    FPS_30(30, "30 FPS (Optimal Exynos 850)"),
    FPS_60(60, "60 FPS (Halus)")
}

enum class VideoCodecOption(val label: String, val mimeType: String) {
    H264("H.264 / AVC (Optimal Exynos 850)", MediaFormat.MIMETYPE_VIDEO_AVC),
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

data class HtmlProjectMetadata(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val detectedDurationMs: Long,
    val assetsTreeUri: Uri? = null,
    val isDurationAutoDetected: Boolean = false
)

data class RenderConfig(
    val resolutionPreset: ResolutionPreset = ResolutionPreset.P720,
    val customWidth: Int = 1280,
    val customHeight: Int = 720,
    val frameRate: FrameRateOption = FrameRateOption.FPS_30,
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
        get() = alignTo16(
            if (resolutionPreset == ResolutionPreset.CUSTOM) customWidth.coerceIn(320, 1920)
            else resolutionPreset.width
        )

    val resolvedHeight: Int
        get() = alignTo16(
            if (resolutionPreset == ResolutionPreset.CUSTOM) customHeight.coerceIn(240, 1920)
            else resolutionPreset.height
        )

    val resolvedVideoBitrateBps: Int
        get() {
            val kbps = when (bitratePreset) {
                VideoBitratePreset.AUTO -> {
                    val pixels = resolvedWidth * resolvedHeight
                    val base = when {
                        pixels <= 854 * 480 -> 2_200
                        pixels <= 1280 * 720 -> 4_800
                        else -> 8_500
                    }
                    if (frameRate == FrameRateOption.FPS_60) (base * 1.35f).toInt() else base
                }
                VideoBitratePreset.CUSTOM -> customBitrateKbps.coerceIn(500, 25_000)
                else -> bitratePreset.kbps ?: 5_000
            }
            return kbps * 1_000
        }

    private fun alignTo16(value: Int): Int = ((value + 8) / 16) * 16
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
    data class Completed(val outputUri: Uri, val fileName: String, val durationMs: Long) : RenderStatus
    data class Cancelled(val reason: String) : RenderStatus
    data class Failed(val errorMessage: String) : RenderStatus
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
