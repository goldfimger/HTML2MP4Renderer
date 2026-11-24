package com.html2mp4.renderer.ui.i18n

import com.html2mp4.renderer.model.AppLanguage
import com.html2mp4.renderer.model.AudioChannelOption
import com.html2mp4.renderer.model.AudioCodecOption
import com.html2mp4.renderer.model.FrameRateOption
import com.html2mp4.renderer.model.RenderPhase
import com.html2mp4.renderer.model.RenderTimelineMode
import com.html2mp4.renderer.model.RenderTimelineSummary
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
    val moreOptionsCd: String,
    val menuEditVideo: String,
    val menuVideoInfo: String,
    val menuShareVideo: String,
    val videoInfoTitle: String,
    val infoFileName: String,
    val infoResolution: String,
    val infoAspectRatio: String,
    val infoOrientation: String,
    val infoDuration: String,
    val infoFrameRate: String,
    val infoVideoCodec: String,
    val infoAudioCodec: String,
    val infoFileSize: String,
    val infoUriLocation: String,
    val loadingInfoLabel: String,
    val shareTitle: String,
    val editorTitle: String,
    val editorSaveButton: String,
    val editorSavingFormat: (Int) -> String,
    val editorPlayLabel: String,
    val editorPauseLabel: String,
    val editorCutLabel: String,
    val editorDeleteLabel: String,
    val editorUndoLabel: String,
    val editorRedoLabel: String,
    val editorAddFootageLabel: String,
    val editorAddAudioLabel: String,
    val editorSmartSyncLabel: String,
    val editorAnalyzingSyncLabel: String,
    val editorVideoTrackLabel: String,
    val editorAudioTrackLabel: String,
    val editorOffsetLabel: String,
    val editorSavedToast: String,
    val editorSyncDoneFormat: (Long) -> String,
    val editorAddTextLabel: String,
    val editorAddCaptionLabel: String,
    val editorAutoCaptionLabel: String,
    val editorGeneratingCaptionsLabel: String,
    val editorSaveConfirmTitle: String,
    val editorSaveConfirmBody: String,
    val editorConfirmSaveAction: String,
    val editorConfirmDiscardAction: String,
    val editorTextDialogTitle: String,
    val editorCaptionDialogTitle: String,
    val editorInputHint: String,
    val editorAutoCaptionDoneFormat: (String, Int) -> String,
    val editorAudioSyncPanelLabel: String,
    val editorAudioVolumeLabel: String,
    val editorZoomInLabel: String,
    val editorZoomOutLabel: String,
    val editorRenameLabel: String,
    val editorTrackShiftToastFormat: (Boolean, Long) -> String,
    val editorTrimToast: String,
    val editorCutToast: String,
    val editorDeleteToast: String,
    val editorAddedFootageToast: String,
    val editorAddedAudioToast: String,
    val editorAddedTextToast: String,
    val editorAddedCaptionToast: String,
    val editorUndoToast: String,
    val editorRedoToast: String,
    val editorDiscardToast: String,
    val aboutCacheTitle: String,
    val aboutCacheWebViewLabel: String,
    val aboutCacheTempLabel: String,
    val aboutCacheTotalLabel: String,
    val aboutClearCacheButton: String,
    val aboutCacheClearedToast: String,
    val aboutReportTitle: String,
    val aboutReportBody: String,
    val aboutContactDevButton: String,
    val aboutCheckUpdateHeader: String,
    val aboutDonateButton: String,
    val aboutQrisTitle: String,
    val aboutQrisSubtitle: String,
    val timelineModeLabel: String,
    val timelineDeterministicOption: String,
    val timelineRealtimeOption: String,
    val timelineDeterministicHint: String,
    val timelineRealtimeHint: String,
    val renderSummaryFramesFormat: (Int, Int, Int, Int) -> String,
    val renderSummaryAudioFormat: (String, Long, Long, Long, Int, Int, Long, Long) -> String,
    val renderSummaryFallbackBitmap: String,
    val renderDiagTitle: String,
    val renderDiagEncoderFormat: (String, String, Int, Int, String) -> String,
    val renderDiagMemoryFormat: (Long, Long, Int) -> String,
    val renderDiagWallDurationFormat: (Long) -> String,
    val renderDiagValidationOkFormat: (Boolean, Boolean, Boolean, Long, Float, Boolean) -> String,
    val renderDiagValidationWarnTitle: String,
    val renderFailedPhaseFormat: (RenderPhase) -> String,
    val isIndonesian: Boolean
) {
    fun timelineModeOptionLabel(mode: RenderTimelineMode): String = when (mode) {
        RenderTimelineMode.DETERMINISTIC -> timelineDeterministicOption
        RenderTimelineMode.REALTIME_COMPAT -> timelineRealtimeOption
    }

    fun timelineModeHint(mode: RenderTimelineMode): String = when (mode) {
        RenderTimelineMode.DETERMINISTIC -> timelineDeterministicHint
        RenderTimelineMode.REALTIME_COMPAT -> timelineRealtimeHint
    }
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
    moreOptionsCd = "Opsi lainnya",
    menuEditVideo = "Edit",
    menuVideoInfo = "Informasi Video",
    menuShareVideo = "Bagikan (Share)",
    videoInfoTitle = "Informasi Lengkap Video",
    infoFileName = "Nama File",
    infoResolution = "Resolusi",
    infoAspectRatio = "Rasio Aspek",
    infoOrientation = "Orientasi",
    infoDuration = "Durasi",
    infoFrameRate = "Frame Rate",
    infoVideoCodec = "Codec & Bitrate Video",
    infoAudioCodec = "Track Audio",
    infoFileSize = "Ukuran File",
    infoUriLocation = "Lokasi URI",
    loadingInfoLabel = "Memindai metadata video...",
    shareTitle = "Bagikan MP4",
    editorTitle = "Editor Video",
    editorSaveButton = "Save",
    editorSavingFormat = { p -> "Menyimpan MP4 ($p%)" },
    editorPlayLabel = "Putar",
    editorPauseLabel = "Jeda",
    editorCutLabel = "Cut",
    editorDeleteLabel = "Delete",
    editorUndoLabel = "Undo",
    editorRedoLabel = "Redo",
    editorAddFootageLabel = "+ Footage",
    editorAddAudioLabel = "+ Audio",
    editorSmartSyncLabel = "Sinkron Cerdas A/V",
    editorAnalyzingSyncLabel = "Analisis A/V...",
    editorVideoTrackLabel = "Timeline Video (geser sisi kiri/kanan untuk trim)",
    editorAudioTrackLabel = "Timeline Audio (geser sisi kiri/kanan untuk trim)",
    editorOffsetLabel = "Offset Sinkron Audio",
    editorSavedToast = "Video hasil edit berhasil disimpan!",
    editorSyncDoneFormat = { ms ->
        if (ms == 0L) "Sinkron Cerdas: A/V seimbang (0 ms)"
        else "Sinkron Cerdas: ${if (ms > 0) "+" else ""}${ms} ms"
    },
    editorAddTextLabel = "Teks",
    editorAddCaptionLabel = "Caption",
    editorAutoCaptionLabel = "Auto Caption",
    editorGeneratingCaptionsLabel = "Deteksi Narasi...",
    editorSaveConfirmTitle = "Simpan Hasil Edit?",
    editorSaveConfirmBody = "Pilih Save untuk merender & menyimpan video, atau Discard untuk membuang perubahan.",
    editorConfirmSaveAction = "Save",
    editorConfirmDiscardAction = "Discard",
    editorTextDialogTitle = "Tambah Teks Video",
    editorCaptionDialogTitle = "Tambah Caption Manual",
    editorInputHint = "Tulis teks atau narasi...",
    editorAutoCaptionDoneFormat = { lang, count ->
        val langName = if (lang == "id") "Indonesia" else "English"
        "Auto Caption: $count segmen ($langName)"
    },
    editorAudioSyncPanelLabel = "Pengaturan Audio & Volume",
    editorAudioVolumeLabel = "Volume Audio",
    editorZoomInLabel = "Perbesar Timeline",
    editorZoomOutLabel = "Perkecil Timeline",
    editorRenameLabel = "Nama File Output (.mp4)",
    editorTrackShiftToastFormat = { isVideo, ms ->
        val track = if (isVideo) "Video" else "Audio"
        "Posisi track $track digeser ke ${if (ms > 0) "+" else ""}${ms} ms"
    },
    editorTrimToast = "Durasi trim diperbarui",
    editorCutToast = "Klip dipotong di garis tengah",
    editorDeleteToast = "Segmen dihapus",
    editorAddedFootageToast = "Footage video ditambahkan",
    editorAddedAudioToast = "Track audio ditambahkan",
    editorAddedTextToast = "Teks video ditambahkan",
    editorAddedCaptionToast = "Caption ditambahkan",
    editorUndoToast = "Perubahan dibatalkan (Undo)",
    editorRedoToast = "Perubahan diulang (Redo)",
    editorDiscardToast = "Perubahan dibuang",
    aboutCacheTitle = "Monitoring Cache Aplikasi",
    aboutCacheWebViewLabel = "Cache WebView & Aset",
    aboutCacheTempLabel = "Temp Render & Editor",
    aboutCacheTotalLabel = "Total Cache Terpakai",
    aboutClearCacheButton = "Bersihkan Cache",
    aboutCacheClearedToast = "Cache aplikasi berhasil dibersihkan",
    aboutReportTitle = "Report Apps & Kontak Developer",
    aboutReportBody = "Menemukan bug, masalah render HTML, atau ingin memberi masukan? Hubungi langsung profil developer Goldfimger di GitHub.",
    aboutContactDevButton = "Kontak Developer (Goldfimger)",
    aboutCheckUpdateHeader = "Check Update:",
    aboutDonateButton = "Donate",
    aboutQrisTitle = "Dukung Developer (QRIS)",
    aboutQrisSubtitle = "Ketuk tanda X di pojok kanan atas foto untuk menutup jendela.",
    timelineModeLabel = "Mode Timeline",
    timelineDeterministicOption = "Deterministik (Presisi Frame)",
    timelineRealtimeOption = "Real-Time Kompatibilitas (Klasik)",
    timelineDeterministicHint = "Waktu animasi dikunci tepat t = frameIndex / fps (rAF, WAAPI, CSS). Audio Web Audio API tetap berjalan real-time.",
    timelineRealtimeHint = "Mode klasik berbasis waktu nyata perangkat. Gunakan jika halaman HTML bergantung pada timing real-time.",
    renderSummaryFramesFormat = { submitted, expected, repeated, dropped ->
        "Frame: $submitted/$expected · Ulang: $repeated · Gagal/Lewat: $dropped"
    },
    renderSummaryAudioFormat = { engine, rx, dropOverflow, missingSeq, srHz, ch, maxDrift, finalDrift ->
        "Audio ($engine · ${srHz}Hz ${ch}ch): $rx chunk · Drop: $dropOverflow · Hilang: $missingSeq · Drift max/akhir: ${maxDrift}ms/${finalDrift}ms"
    },
    renderSummaryFallbackBitmap = "Jalur fallback Bitmap/Canvas aktif",
    renderDiagTitle = "Diagnostik & Validasi MP4",
    renderDiagEncoderFormat = { vEnc, aEnc, w, h, sizeText ->
        "Video: $vEnc (${w}×${h}) · Audio: $aEnc · Ukuran MP4: $sizeText"
    },
    renderDiagMemoryFormat = { peakMb, minAvailMb, throttles ->
        "Memori Peak: ${peakMb} MB · Sisa Min: ${minAvailMb} MB · Throttle: $throttles×"
    },
    renderDiagWallDurationFormat = { wallMs ->
        val sec = wallMs / 1000.0
        String.format(Locale.US, "Waktu Render Total: %.2f detik (%d ms)", sec, wallMs)
    },
    renderDiagValidationOkFormat = { hasV, hasA, expA, actualDurMs, measuredFps, playable ->
        val vTag = if (hasV) "Video OK" else "Video HILANG"
        val aTag = when {
            !expA -> "Audio Muted"
            hasA -> "Audio OK"
            else -> "Audio HILANG"
        }
        val playTag = if (playable) "Playable" else "Tidak Valid"
        val fpsText = String.format(Locale.US, "%.1f", measuredFps)
        "Validasi Track: $vTag · $aTag · ${actualDurMs}ms @ ${fpsText} FPS · Status: $playTag"
    },
    renderDiagValidationWarnTitle = "Peringatan Validasi:",
    renderFailedPhaseFormat = { phase -> "Fase: ${phase.labelId}" },
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
    moreOptionsCd = "More options",
    menuEditVideo = "Edit",
    menuVideoInfo = "Video Info",
    menuShareVideo = "Share",
    videoInfoTitle = "Full Video Information",
    infoFileName = "File Name",
    infoResolution = "Resolution",
    infoAspectRatio = "Aspect Ratio",
    infoOrientation = "Orientation",
    infoDuration = "Duration",
    infoFrameRate = "Frame Rate",
    infoVideoCodec = "Video Codec & Bitrate",
    infoAudioCodec = "Audio Track",
    infoFileSize = "File Size",
    infoUriLocation = "URI Location",
    loadingInfoLabel = "Scanning video metadata...",
    shareTitle = "Share MP4",
    editorTitle = "Video Editor",
    editorSaveButton = "Save",
    editorSavingFormat = { p -> "Saving MP4 ($p%)" },
    editorPlayLabel = "Play",
    editorPauseLabel = "Pause",
    editorCutLabel = "Cut",
    editorDeleteLabel = "Delete",
    editorUndoLabel = "Undo",
    editorRedoLabel = "Redo",
    editorAddFootageLabel = "+ Footage",
    editorAddAudioLabel = "+ Audio",
    editorSmartSyncLabel = "Smart A/V Sync",
    editorAnalyzingSyncLabel = "Analyzing A/V...",
    editorVideoTrackLabel = "Video Timeline (drag left/right edges to trim)",
    editorAudioTrackLabel = "Audio Timeline (drag left/right edges to trim)",
    editorOffsetLabel = "Audio Sync Offset",
    editorSavedToast = "Edited video saved!",
    editorSyncDoneFormat = { ms ->
        if (ms == 0L) "Smart Sync: A/V aligned (0 ms)"
        else "Smart Sync: ${if (ms > 0) "+" else ""}${ms} ms"
    },
    editorAddTextLabel = "Text",
    editorAddCaptionLabel = "Caption",
    editorAutoCaptionLabel = "Auto Caption",
    editorGeneratingCaptionsLabel = "Detecting Speech...",
    editorSaveConfirmTitle = "Save Edited Video?",
    editorSaveConfirmBody = "Choose Save to render & store the edited MP4, or Discard to drop all changes.",
    editorConfirmSaveAction = "Save",
    editorConfirmDiscardAction = "Discard",
    editorTextDialogTitle = "Add Video Text",
    editorCaptionDialogTitle = "Add Manual Caption",
    editorInputHint = "Type text or narration...",
    editorAutoCaptionDoneFormat = { lang, count ->
        val langName = if (lang == "id") "Indonesian" else "English"
        "Auto Caption: $count segments ($langName)"
    },
    editorAudioSyncPanelLabel = "Audio & Volume Settings",
    editorAudioVolumeLabel = "Audio Volume",
    editorZoomInLabel = "Zoom In Timeline",
    editorZoomOutLabel = "Zoom Out Timeline",
    editorRenameLabel = "Output File Name (.mp4)",
    editorTrackShiftToastFormat = { isVideo, ms ->
        val track = if (isVideo) "Video" else "Audio"
        "$track track shifted to ${if (ms > 0) "+" else ""}${ms} ms"
    },
    editorTrimToast = "Trim duration updated",
    editorCutToast = "Clip split at center line",
    editorDeleteToast = "Segment deleted",
    editorAddedFootageToast = "Video footage added",
    editorAddedAudioToast = "Audio track added",
    editorAddedTextToast = "Video text added",
    editorAddedCaptionToast = "Caption added",
    editorUndoToast = "Action undone",
    editorRedoToast = "Action redone",
    editorDiscardToast = "Changes discarded",
    aboutCacheTitle = "App Cache Monitor",
    aboutCacheWebViewLabel = "WebView & Asset Cache",
    aboutCacheTempLabel = "Render & Editor Temp",
    aboutCacheTotalLabel = "Total Cache Used",
    aboutClearCacheButton = "Clear Cache",
    aboutCacheClearedToast = "Application cache cleared",
    aboutReportTitle = "Report App & Contact Developer",
    aboutReportBody = "Found a bug, HTML rendering issue, or have a feature request? Reach out directly on Goldfimger's GitHub profile.",
    aboutContactDevButton = "Contact Developer (Goldfimger)",
    aboutCheckUpdateHeader = "Check Update:",
    aboutDonateButton = "Donate",
    aboutQrisTitle = "Support Developer (QRIS)",
    aboutQrisSubtitle = "Tap the X mark on the top-right corner of the photo to close.",
    timelineModeLabel = "Timeline Mode",
    timelineDeterministicOption = "Deterministic (Frame-Accurate)",
    timelineRealtimeOption = "Real-Time Compat (Classic)",
    timelineDeterministicHint = "Locks animation clock to t = frameIndex / fps (rAF, WAAPI, CSS). Web Audio API still runs on real-time hardware clock.",
    timelineRealtimeHint = "Classic device-clock loop. Use if the HTML relies strictly on real-time playback.",
    renderSummaryFramesFormat = { submitted, expected, repeated, dropped ->
        "Frames: $submitted/$expected · Repeated: $repeated · Dropped: $dropped"
    },
    renderSummaryAudioFormat = { engine, rx, dropOverflow, missingSeq, srHz, ch, maxDrift, finalDrift ->
        "Audio ($engine · ${srHz}Hz ${ch}ch): $rx chunks · Drop: $dropOverflow · Missing: $missingSeq · Drift max/end: ${maxDrift}ms/${finalDrift}ms"
    },
    renderSummaryFallbackBitmap = "Bitmap/Canvas fallback path active",
    renderDiagTitle = "Render Diagnostics & MP4 Validation",
    renderDiagEncoderFormat = { vEnc, aEnc, w, h, sizeText ->
        "Video: $vEnc (${w}×${h}) · Audio: $aEnc · MP4 Size: $sizeText"
    },
    renderDiagMemoryFormat = { peakMb, minAvailMb, throttles ->
        "Peak Memory: ${peakMb} MB · Min Free: ${minAvailMb} MB · Throttles: $throttles×"
    },
    renderDiagWallDurationFormat = { wallMs ->
        val sec = wallMs / 1000.0
        String.format(Locale.US, "Total Render Time: %.2f s (%d ms)", sec, wallMs)
    },
    renderDiagValidationOkFormat = { hasV, hasA, expA, actualDurMs, measuredFps, playable ->
        val vTag = if (hasV) "Video OK" else "Video MISSING"
        val aTag = when {
            !expA -> "Audio Muted"
            hasA -> "Audio OK"
            else -> "Audio MISSING"
        }
        val playTag = if (playable) "Playable" else "Invalid"
        val fpsText = String.format(Locale.US, "%.1f", measuredFps)
        "Track Check: $vTag · $aTag · ${actualDurMs}ms @ ${fpsText} FPS · Status: $playTag"
    },
    renderDiagValidationWarnTitle = "Validation Warnings:",
    renderFailedPhaseFormat = { phase -> "Phase: ${phase.labelEn}" },
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
