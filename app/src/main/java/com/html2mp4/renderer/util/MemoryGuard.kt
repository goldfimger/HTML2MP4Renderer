package com.html2mp4.renderer.util

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Guards devices across 3-4 GB entry SoCs (Exynos 850 / Helio) and 6-16 GB flagship
 * Snapdragon/Tensor phones, Tablets, and Chromebooks by dynamically scaling heap/queue thresholds.
 */
class MemoryGuard(private val context: Context) : ComponentCallbacks2 {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _isLowMemory = MutableStateFlow(false)
    val isLowMemory: StateFlow<Boolean> = _isLowMemory.asStateFlow()

    private val isTightRamDevice: Boolean by lazy {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        (memInfo.totalMem / (1024L * 1024L)) <= 4500L
    }

    fun register() {
        context.registerComponentCallbacks(this)
    }

    fun unregister() {
        runCatching { context.unregisterComponentCallbacks(this) }
    }

    fun availableMemoryMb(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024L * 1024L)
    }

    fun isSafeToStart1080p(): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val minRequiredMb = if (isTightRamDevice) 280L else 220L
        return !memInfo.lowMemory && (memInfo.availMem / (1024L * 1024L)) >= minRequiredMb
    }

    fun shouldThrottleFrameQueue(): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val throttleMb = if (isTightRamDevice) 180L else 120L
        val low = memInfo.lowMemory || (memInfo.availMem / (1024L * 1024L)) < throttleMb
        _isLowMemory.value = low
        return low
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            _isLowMemory.value = true
            System.gc()
        } else if (level <= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            _isLowMemory.value = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in API 34")
    override fun onLowMemory() {
        _isLowMemory.value = true
        System.gc()
    }
}
