package com.html2mp4.renderer.util

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.html2mp4.renderer.model.MemoryDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Guards devices across 3-4 GB entry SoCs (Exynos 850 / Helio) and 6-16 GB flagship
 * Snapdragon/Tensor phones, Tablets, and Chromebooks by dynamically scaling heap/queue thresholds.
 */
class MemoryGuard(private val context: Context) : ComponentCallbacks2 {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _isLowMemory = MutableStateFlow(false)
    val isLowMemory: StateFlow<Boolean> = _isLowMemory.asStateFlow()

    private val peakUsedMb = AtomicLong(0L)
    private val minAvailMb = AtomicLong(Long.MAX_VALUE)
    private val lowMemThrottleCount = AtomicInteger(0)

    private val isTightRamDevice: Boolean by lazy {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        (memInfo.totalMem / (1024L * 1024L)) <= 4500L
    }

    fun resetSessionStats() {
        peakUsedMb.set(0L)
        minAvailMb.set(Long.MAX_VALUE)
        lowMemThrottleCount.set(0)
        sampleSessionMemory()
    }

    fun sampleSessionMemory() {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val runtime = Runtime.getRuntime()
        val jvmUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val nativeMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)
        val totalUsedMb = jvmUsedMb + nativeMb

        while (true) {
            val prev = peakUsedMb.get()
            if (totalUsedMb <= prev || peakUsedMb.compareAndSet(prev, totalUsedMb)) break
        }
        while (true) {
            val prev = minAvailMb.get()
            if (availMb >= prev || minAvailMb.compareAndSet(prev, availMb)) break
        }
    }

    fun snapshotSessionDiagnostics(): MemoryDiagnostics {
        sampleSessionMemory()
        val avail = minAvailMb.get()
        return MemoryDiagnostics(
            peakUsedRamMb = peakUsedMb.get(),
            minAvailableRamMb = if (avail == Long.MAX_VALUE) availableMemoryMb() else avail,
            lowMemoryThrottleCount = lowMemThrottleCount.get()
        )
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
        if (low) {
            lowMemThrottleCount.incrementAndGet()
        }
        return low
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            _isLowMemory.value = true
            lowMemThrottleCount.incrementAndGet()
            System.gc()
        } else if (level <= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            _isLowMemory.value = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in API 34")
    override fun onLowMemory() {
        _isLowMemory.value = true
        lowMemThrottleCount.incrementAndGet()
        System.gc()
    }
}
