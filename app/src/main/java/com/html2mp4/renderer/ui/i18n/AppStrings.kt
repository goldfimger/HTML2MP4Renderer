package com.html2mp4.renderer.ui.i18n

import com.html2mp4.renderer.model.AppLanguage
import com.html2mp4.renderer.model.AudioChannelOption
import com.html2mp4.renderer.model.AudioCodecOption
import com.html2mp4.renderer.model.FrameRateOption
import com.html2mp4.renderer.model.ResolutionPreset
import com.html2mp4.renderer.model.ThemeMode
import com.html2mp4.renderer.model.VideoBitratePreset
import com.html2mp4.renderer.model.VideoCodecOption
import com.html2mp4.renderer.model.VideoOrientation
import java.util.Locale

data class AppStrings(
    val appTitle: String,
    val changeTheme: String,
    val changeLanguage: String,
    val languageSystemOption: String,
    val tabProject: String,
    val tabResults: String,
    val tabAbout: String,
    val splashPoweredBy: String,
    val aboutOfflineTagline: String,
    val aboutDeveloperLabel: String,
    val aboutGithubTitle: String,
    val aboutThanksTitle: String,
    val aboutThanksBody: String,
    val aboutHardwareTitle: String,
    val aboutChipsetLabel: String,
    val aboutFormFactorLabel: String,
    val aboutEncoderLabel: String,
    val aboutRamLabel: String,
    val aboutSupportedDevices: String,
    val htmlSourceTitle: String,
    val htmlSourceHint: String,
    val detectingMeta: String,
    val sizePrefix: String,
    val durationPrefix: String,
    val autoDetectedPrefix: String,
    val portraitLabel: String,
    val landscapeLabel: String,
    val assetsConnected: String,
    val pickHtmlButton: String,
    val changeHtmlButton: String,
    val localAssetsButton: String,
    val preparingPipeline: String,
    val renderingTitleFormat: (Int) -> String,
    val cancelButton: String,
    val renderCompleted: String,
    val viewResults: String,
    val failedPrefix: String,
    val closeButton: String,
    val okButton: String,
    val videoSettingsTitle: String,
    val videoOrientationLabel: String,
    val resolutionLabel: String,
    val customResolutionOption: String,
    val widthPxLabel: String,
    val heightPxLabel: String,
    val frameRateLabel: String,
    val videoCodecLabel: String,
    val videoBitrateLabel: String,
    val targetBitrateFormat: (Int) -> String,
    val audioSettingsTitle: String,
    val muteAudioLabel: String,
    val audioCodecLabel: String,
    val sampleRateLabel: String,
    val channelLabel: String,
    val audioBitrateLabel: String,
    val outputLocationTitle: String,
    val changeFolderButton: String,
    val renderingCta: String,
    val startRenderCta: String,
    val resultsCountFormat: (Int) -> String,
    val refreshListCd: String,
    val noVideosTitle: String,
    val noVideosSubtitle: String,
    val playVideoCd: String,
    val closePlayerCd: String,
    val playWithTitle: String,
    val systemPlayerButton: String,
    val isIndonesian: Boolean
) {
    fun orientationLabel(o: VideoOrientation): String = when (o) {
        VideoOrientation.LANDSCAPE -> if (isIndonesian) "Lanskap (16:9)" else "Landscape (16:9)"
        VideoOrientation.PORTRAIT -> if (isIndonesian) "Vertikal (9:16)" else "Portrait (9:16)"
    }

    fun resolutionLabel(p: ResolutionPreset): String = when (p) {
        ResolutionPreset.CUSTOM -> if (isIndonesian) "Custom" else "Custom"
        else -> p.label
    }

    fun fpsLabel(f: FrameRateOption): String = when (f) {
        FrameRateOption.FPS_24 -> if (isIndonesian) "24 FPS (Sinematik)" else "24 FPS (Cinematic)"
        FrameRateOption.FPS_30 -> if (isIndonesian) "30 FPS (Optimal Hardware)" else "30 FPS (Hardware Optimal)"
        FrameRateOption.FPS_60 -> if (isIndonesian) "60 FPS (Halus)" else "60 FPS (Smooth)"
    }

    fun videoCodecLabel(c: VideoCodecOption): String = when (c) {
        VideoCodecOption.H264 -> if (isIndonesian) "H.264 / AVC (Optimal Hardware)" else "H.264 / AVC (Hardware Optimal)"
        VideoCodecOption.H265 -> if (isIndonesian) "H.265 / HEVC (Efisien)" else "H.265 / HEVC (Efficient)"
    }

    fun bitratePresetLabel(b: VideoBitratePreset): String = when (b) {
        VideoBitratePreset.AUTO -> if (isIndonesian) "Auto (Adaptif Resolusi)" else "Auto (Resolution Adaptive)"
        VideoBitratePreset.LOW -> if (isIndonesian) "Rendah (2.5 Mbps)" else "Low (2.5 Mbps)"
        VideoBitratePreset.MEDIUM -> if (isIndonesian) "Sedang (5 Mbps)" else "Medium (5 Mbps)"
        VideoBitratePreset.HIGH -> if (isIndonesian) "Tinggi (10 Mbps)" else "High (10 Mbps)"
        VideoBitratePreset.CUSTOM -> "Custom Kbps"
    }

    fun audioCodecLabel(a: AudioCodecOption): String = when (a) {
        AudioCodecOption.AAC -> if (isIndonesian) "AAC-LC (Standar MP4)" else "AAC-LC (MP4 Standard)"
        AudioCodecOption.MP3 -> if (isIndonesian) "MP3 (Fallback AAC jika tidak didukung)" else "MP3 (AAC fallback if unsupported)"
    }

    fun audioChannelLabel(c: AudioChannelOption): String = when (c) {
        AudioChannelOption.MONO -> "Mono (1 ch)"
        AudioChannelOption.STEREO -> "Stereo (2 ch)"
    }

    fun themeModeLabel(t: ThemeMode): String = when (t) {
        ThemeMode.SYSTEM -> if (isIndonesian) "Otomatis Sistem" else "System Default"
        ThemeMode.LIGHT -> if (isIndonesian) "Mode Terang" else "Light Mode"
        ThemeMode.DARK -> if (isIndonesian) "Mode Gelap" else "Dark Mode"
    }
}

private val IndonesianStrings = AppStrings(
    appTitle = "HTML2MP4 Renderer",
    changeTheme = "Ganti Tema",
    changeLanguage = "Pilih Bahasa",
    languageSystemOption = "Ikuti Sistem",
    tabProject = "Proyek",
    tabResults = "Hasil",
    tabAbout = "Tentang",
    splashPoweredBy = "Powered by Gemini",
    aboutOfflineTagline = "Render HTML ke MP4 secara offline sepuasnya",
    aboutDeveloperLabel = "Developer",
    aboutGithubTitle = "Repositori GitHub",
    aboutThanksTitle = "Terima Kasih",
    aboutThanksBody = "Terima kasih kepada Google AI Studio dan GitHub atas dukungan alat pengembangan serta ekosistem build otomatis untuk aplikasi ini.",
    aboutHardwareTitle = "Kompatibilitas Chipset & Perangkat",
    aboutChipsetLabel = "Chipset / SoC",
    aboutFormFactorLabel = "Jenis Perangkat",
    aboutEncoderLabel = "Encoder Aktif",
    aboutRamLabel = "Kapasitas RAM",
    aboutSupportedDevices = "Dioptimalkan untuk Samsung Exynos, Qualcomm Snapdragon, MediaTek Dimensity/Helio, Google Tensor, Tablet Android, serta Chromebook / Googlebook.",
    htmlSourceTitle = "Sumber Dokumen HTML",
    htmlSourceHint = "Pilih file .html melalui Storage Access Framework. Isi kode HTML tidak dimuat ke memori UI agar bebas OOM.",
    detectingMeta = "Mendeteksi durasi & orientasi...",
    sizePrefix = "Ukuran",
    durationPrefix = "Durasi",
    autoDetectedPrefix = "Terdeteksi otomatis",
    portraitLabel = "Vertikal",
    landscapeLabel = "Lanskap",
    assetsConnected = "Folder aset lokal terhubung",
    pickHtmlButton = "Pilih File HTML",
    changeHtmlButton = "Ganti HTML",
    localAssetsButton = "Aset Lokal",
    preparingPipeline = "Menyiapkan Pipeline...",
    renderingTitleFormat = { pct -> "Merender Video ($pct%)" },
    cancelButton = "Batal",
    renderCompleted = "Render Selesai",
    viewResults = "Lihat Hasil",
    failedPrefix = "Gagal",
    closeButton = "Tutup",
    okButton = "OK",
    videoSettingsTitle = "Pengaturan Video (Hardware)",
    videoOrientationLabel = "Orientasi Video",
    resolutionLabel = "Resolusi",
    customResolutionOption = "Custom (Atur W × H)",
    widthPxLabel = "Lebar (px)",
    heightPxLabel = "Tinggi (px)",
    frameRateLabel = "Frame Rate",
    videoCodecLabel = "Codec Video",
    videoBitrateLabel = "Bitrate Video",
    targetBitrateFormat = { kbps -> "Target Bitrate: $kbps Kbps" },
    audioSettingsTitle = "Pengaturan Audio",
    muteAudioLabel = "Tanpa Audio",
    audioCodecLabel = "Codec Audio",
    sampleRateLabel = "Sample Rate",
    channelLabel = "Channel",
    audioBitrateLabel = "Bitrate Audio",
    outputLocationTitle = "Lokasi Output MP4",
    changeFolderButton = "Ubah Folder",
    renderingCta = "Sedang Merender...",
    startRenderCta = "Mulai Render",
    resultsCountFormat = { count -> "Video Selesai ($count)" },
    refreshListCd = "Segarkan daftar",
    noVideosTitle = "Belum ada video MP4",
    noVideosSubtitle = "Hasil render dari /Movies/HTML2MP4/ akan muncul di sini.",
    playVideoCd = "Putar Video",
    closePlayerCd = "Tutup pemutar",
    playWithTitle = "Putar dengan",
    systemPlayerButton = "Pemutar Sistem",
    isIndonesian = true
)

private val EnglishStrings = AppStrings(
    appTitle = "HTML2MP4 Renderer",
    changeTheme = "Switch Theme",
    changeLanguage = "App Language",
    languageSystemOption = "System Default",
    tabProject = "Project",
    tabResults = "Results",
    tabAbout = "About",
    splashPoweredBy = "Powered by Gemini",
    aboutOfflineTagline = "Render HTML to MP4 offline without limits",
    aboutDeveloperLabel = "Developer",
    aboutGithubTitle = "GitHub Repository",
    aboutThanksTitle = "Special Thanks",
    aboutThanksBody = "Special thanks to Google AI Studio and GitHub for powering the development workflow and automated APK build pipeline.",
    aboutHardwareTitle = "Chipset & Device Compatibility",
    aboutChipsetLabel = "Chipset / SoC",
    aboutFormFactorLabel = "Form Factor",
    aboutEncoderLabel = "Active Encoder",
    aboutRamLabel = "System RAM",
    aboutSupportedDevices = "Optimized for Samsung Exynos, Qualcomm Snapdragon, MediaTek Dimensity/Helio, Google Tensor, Android Tablets, and Chromebooks (Googlebook).",
    htmlSourceTitle = "HTML Document Source",
    htmlSourceHint = "Pick an .html file via Storage Access Framework. HTML text is never loaded into UI memory to prevent OOM.",
    detectingMeta = "Detecting duration & orientation...",
    sizePrefix = "Size",
    durationPrefix = "Duration",
    autoDetectedPrefix = "Auto-detected",
    portraitLabel = "Portrait",
    landscapeLabel = "Landscape",
    assetsConnected = "Local assets folder linked",
    pickHtmlButton = "Select HTML File",
    changeHtmlButton = "Change HTML",
    localAssetsButton = "Local Assets",
    preparingPipeline = "Preparing Pipeline...",
    renderingTitleFormat = { pct -> "Rendering Video ($pct%)" },
    cancelButton = "Cancel",
    renderCompleted = "Render Complete",
    viewResults = "View Results",
    failedPrefix = "Failed",
    closeButton = "Close",
    okButton = "OK",
    videoSettingsTitle = "Video Settings (Hardware)",
    videoOrientationLabel = "Video Orientation",
    resolutionLabel = "Resolution",
    customResolutionOption = "Custom (Set W × H)",
    widthPxLabel = "Width (px)",
    heightPxLabel = "Height (px)",
    frameRateLabel = "Frame Rate",
    videoCodecLabel = "Video Codec",
    videoBitrateLabel = "Video Bitrate",
    targetBitrateFormat = { kbps -> "Target Bitrate: $kbps Kbps" },
    audioSettingsTitle = "Audio Settings",
    muteAudioLabel = "Mute Audio",
    audioCodecLabel = "Audio Codec",
    sampleRateLabel = "Sample Rate",
    channelLabel = "Channels",
    audioBitrateLabel = "Audio Bitrate",
    outputLocationTitle = "MP4 Output Location",
    changeFolderButton = "Change Folder",
    renderingCta = "Rendering...",
    startRenderCta = "Start Render",
    resultsCountFormat = { count -> "Rendered Videos ($count)" },
    refreshListCd = "Refresh list",
    noVideosTitle = "No MP4 videos yet",
    noVideosSubtitle = "Rendered exports in /Movies/HTML2MP4/ will appear here.",
    playVideoCd = "Play Video",
    closePlayerCd = "Close player",
    playWithTitle = "Open with",
    systemPlayerButton = "System Player",
    isIndonesian = false
)

fun resolveAppStrings(language: AppLanguage): AppStrings {
    return when (language) {
        AppLanguage.INDONESIAN -> IndonesianStrings
        AppLanguage.ENGLISH -> EnglishStrings
        AppLanguage.SYSTEM -> {
            val lang = Locale.getDefault().language.lowercase(Locale.US)
            if (lang == "in" || lang == "id") IndonesianStrings else EnglishStrings
        }
    }
}
