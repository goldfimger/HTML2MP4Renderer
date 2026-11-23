package com.html2mp4.renderer.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

enum class SocFamily(val displayName: String) {
    EXYNOS("Samsung Exynos"),
    SNAPDRAGON("Qualcomm Snapdragon"),
    MEDIATEK("MediaTek Dimensity / Helio"),
    TENSOR("Google Tensor"),
    CHROMEOS_X86("Intel / AMD ChromeOS"),
    GENERIC("Hardware MediaCodec")
}

enum class DeviceFormFactor(val labelId: String, val labelEn: String) {
    PHONE("Ponsel Android", "Android Phone"),
    TABLET("Tablet Layar Besar", "Large-Screen Tablet"),
    CHROMEBOOK("Chromebook / Googlebook", "Chromebook / Googlebook")
}

data class DeviceProfile(
    val socFamily: SocFamily,
    val chipsetName: String,
    val formFactor: DeviceFormFactor,
    val isChromeOs: Boolean,
    val isTablet: Boolean,
    val totalRamMb: Long,
    val preferredAvcEncoder: String?,
    val preferredHevcEncoder: String?,
    val widthAlignment: Int,
    val heightAlignment: Int
) {
    val activeEncoderLabel: String
        get() = preferredAvcEncoder ?: socFamily.displayName

    val shortLabel: String
        get() = "${socFamily.displayName} · ${totalRamMb / 1024L}GB RAM"
}

/**
 * Automatically tunes MediaCodec encoder selection, macroblock alignment, and RAM budgets across
 * Samsung Exynos, Qualcomm Snapdragon, MediaTek Dimensity/Helio, Google Tensor, Tablets, and Chromebooks.
 */
object ChipsetOptimizer {

    @Volatile
    private var cachedProfile: DeviceProfile? = null

    fun detect(context: Context): DeviceProfile {
        cachedProfile?.let { return it }

        val pm = context.packageManager
        val abis = Build.SUPPORTED_ABIS.joinToString(" ").lowercase()
        val isChromeOs = pm.hasSystemFeature("org.chromium.arc") ||
            pm.hasSystemFeature("org.chromium.arc.device_management") ||
            pm.hasSystemFeature(PackageManager.FEATURE_PC) ||
            Build.BRAND.contains("chromium", ignoreCase = true) ||
            Build.DEVICE.contains("cheets", ignoreCase = true)

        val smallestWidthDp = context.resources.configuration.smallestScreenWidthDp
        val isTablet = !isChromeOs && smallestWidthDp >= 600
        val formFactor = when {
            isChromeOs -> DeviceFormFactor.CHROMEBOOK
            isTablet -> DeviceFormFactor.TABLET
            else -> DeviceFormFactor.PHONE
        }

        val socRaw = buildString {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                append(Build.SOC_MODEL)
                append(' ')
                append(Build.SOC_MANUFACTURER)
                append(' ')
            }
            append(Build.HARDWARE)
            append(' ')
            append(Build.BOARD)
            append(' ')
            append(abis)
        }.lowercase()

        val codecInfos = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList()
        }.getOrDefault(emptyList())

        val avcEncoderInfo = findBestHardwareEncoder(codecInfos, MediaFormat.MIMETYPE_VIDEO_AVC)
        val hevcEncoderInfo = findBestHardwareEncoder(codecInfos, MediaFormat.MIMETYPE_VIDEO_HEVC)

        val encoderNamesLower = listOfNotNull(avcEncoderInfo?.name, hevcEncoderInfo?.name)
            .joinToString(" ")
            .lowercase()

        val socFamily = when {
            isChromeOs || abis.contains("x86") || encoderNamesLower.contains("intel") || encoderNamesLower.contains("vaapi") || encoderNamesLower.contains("arc") ->
                SocFamily.CHROMEOS_X86
            socRaw.contains("qcom") || socRaw.contains("qualcomm") || socRaw.contains("snapdragon") ||
                socRaw.contains("sm8") || socRaw.contains("sm7") || socRaw.contains("sm6") ||
                encoderNamesLower.contains("qti") || encoderNamesLower.contains("qcom") ->
                SocFamily.SNAPDRAGON
            socRaw.contains("mt6") || socRaw.contains("mt8") || socRaw.contains("mediatek") ||
                socRaw.contains("dimensity") || socRaw.contains("helio") || encoderNamesLower.contains("mtk") ->
                SocFamily.MEDIATEK
            socRaw.contains("tensor") || socRaw.contains("gs101") || socRaw.contains("gs201") ||
                socRaw.contains("zuma") || Build.MANUFACTURER.equals("Google", ignoreCase = true) ->
                SocFamily.TENSOR
            socRaw.contains("exynos") || socRaw.contains("universal") || socRaw.contains("s5e") ||
                encoderNamesLower.contains("exynos") ->
                SocFamily.EXYNOS
            else -> SocFamily.GENERIC
        }

        val chipsetName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL.isNotBlank() && Build.SOC_MODEL != "unknown") {
            "${socFamily.displayName} (${Build.SOC_MODEL})"
        } else {
            "${socFamily.displayName} (${Build.HARDWARE})"
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1024L)

        val videoCaps = runCatching {
            avcEncoderInfo?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)?.videoCapabilities
        }.getOrNull()

        val wAlign = (videoCaps?.widthAlignment ?: 2).coerceIn(2, 16)
        val hAlign = (videoCaps?.heightAlignment ?: 2).coerceIn(2, 16)

        return DeviceProfile(
            socFamily = socFamily,
            chipsetName = chipsetName,
            formFactor = formFactor,
            isChromeOs = isChromeOs,
            isTablet = isTablet,
            totalRamMb = totalRamMb,
            preferredAvcEncoder = avcEncoderInfo?.name,
            preferredHevcEncoder = hevcEncoderInfo?.name,
            widthAlignment = wAlign,
            heightAlignment = hAlign
        ).also { cachedProfile = it }
    }

    private fun findBestHardwareEncoder(
        codecInfos: List<MediaCodecInfo>,
        mimeType: String
    ): MediaCodecInfo? {
        val candidates = codecInfos.filter { info ->
            info.isEncoder &&
                info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
        if (candidates.isEmpty()) return null

        val hwOnly = candidates.filter { info ->
            val lower = info.name.lowercase()
            val isGoogleSw = lower.startsWith("omx.google.") || lower.startsWith("c2.android.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                !info.isSoftwareOnly && !isGoogleSw
            } else {
                !isGoogleSw
            }
        }
        val pool = if (hwOnly.isNotEmpty()) hwOnly else candidates
        return pool.firstOrNull { info ->
            val lower = info.name.lowercase()
            lower.contains("qti") || lower.contains("qcom") ||
                lower.contains("exynos") || lower.contains("mtk") ||
                lower.contains("google") || lower.contains("intel") ||
                lower.contains("vaapi") || lower.contains("arc")
        } ?: pool.firstOrNull()
    }

    fun alignDimension(value: Int, alignment: Int): Int {
        val step = alignment.coerceAtLeast(2)
        val aligned = ((value + step / 2) / step) * step
        return ((aligned / 2) * 2).coerceIn(240, 1920)
    }
}
