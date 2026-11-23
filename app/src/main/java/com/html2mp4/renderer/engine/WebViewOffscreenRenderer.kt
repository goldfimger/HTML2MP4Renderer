package com.html2mp4.renderer.engine

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.html2mp4.renderer.model.RenderConfig
import com.html2mp4.renderer.util.ChipsetOptimizer
import com.html2mp4.renderer.util.MemoryGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Crash-proof offscreen WebView -> MediaCodec Surface renderer for Android 15 (Exynos 850).
 *
 * 1. During HTML import (inspectDurationOnly), WebView is muted at Chromium level + JS AudioContext
 *    is suspended so zero sound leaks from the speaker.
 * 2. During rendering, attempts VirtualDisplay + Presentation (real hardware-accelerated Window).
 *    If OEM/AOSP blocks Presentation from a Service, automatically falls back to LAYER_TYPE_SOFTWARE
 *    with a single reusable Bitmap drawn onto MediaCodec.inputSurface.lockCanvas() — eliminating
 *    unattached-WebView RenderThread SIGSEGV crashes.
 */
class WebViewOffscreenRenderer(
    private val context: Context,
    private val memoryGuard: MemoryGuard
) {
    data class RenderProgress(
        val percent: Int,
        val currentFrame: Int,
        val totalFrames: Int,
        val etaFormatted: String,
        val elapsedMs: Long,
        val phaseLabel: String
    )

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun inspectProjectMetadata(htmlUri: Uri, assetsTreeUri: Uri?): HtmlProbeResult {
        val bridge = HtmlBridge(48000, 2)
        val resourceHandler = SafWebResourceHandler(context, htmlUri, assetsTreeUri)
        val pageLoaded = CompletableDeferred<Unit>()
        var webView: WebView? = null

        return try {
            withContext(Dispatchers.Main) {
                webView = createConfiguredWebView(
                    hostContext = context,
                    bridge = bridge,
                    resourceHandler = resourceHandler,
                    hardwareLayer = false,
                    muteAudioAtBrowser = true,
                    requireUserGesture = true,
                    documentStartScript = HtmlBridge.buildProbeOnlyScript()
                ) {
                    if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                }.apply {
                    // Neutral 1000x1000 square viewport prevents biasing responsive CSS toward landscape or portrait
                    measure(
                        View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, 1000, 1000)
                    loadUrl(SafWebResourceHandler.ENTRY_URL)
                }
            }

            withTimeoutOrNull(8_000L) { pageLoaded.await() }
            delay(500L)
            withContext(Dispatchers.Main) {
                val probeCall = "${HtmlBridge.buildProbeOnlyScript()}; window.__html2mp4ProbeNow();"
                webView?.evaluateJavascript(probeCall, null)
            }
            withTimeoutOrNull(3_500L) { bridge.probeDeferred.await() }
                ?: HtmlProbeResult(
                    durationMs = 10_000L,
                    width = 1280,
                    height = 720,
                    orientation = com.html2mp4.renderer.model.VideoOrientation.LANDSCAPE
                )
        } finally {
            withContext(Dispatchers.Main) {
                destroyWebViewSafely(webView)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun renderToMp4(
        htmlUri: Uri,
        assetsTreeUri: Uri?,
        config: RenderConfig,
        durationMs: Long,
        tempOutputFile: File,
        onProgress: (RenderProgress) -> Unit
    ) {
        val width = config.resolvedWidth
        val height = config.resolvedHeight
        val fps = config.frameRate.fps
        val totalFrames = ceil((durationMs / 1000.0) * fps).toInt().coerceAtLeast(fps)

        val deviceProfile = ChipsetOptimizer.detect(context)
        val bridge = HtmlBridge(config.audioSampleRate.hz, config.audioChannels.count)
        val resourceHandler = SafWebResourceHandler(context, htmlUri, assetsTreeUri)
        val pipeline = MediaCodecMuxerPipeline(config, deviceProfile, tempOutputFile)
        val pageLoaded = CompletableDeferred<Unit>()

        var webView: WebView? = null
        var virtualDisplay: VirtualDisplay? = null
        var presentation: Presentation? = null
        var pulseView: View? = null
        var usingPresentation = false

        try {
            pipeline.prepare()

            withContext(Dispatchers.Main) {
                runCatching { WebView.enableSlowWholeDocumentDraw() }

                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val densityDpi = DisplayMetrics.DENSITY_MEDIUM

                val vdAttempt = runCatching {
                    dm.createVirtualDisplay(
                        "HTML2MP4_Offscreen",
                        width,
                        height,
                        densityDpi,
                        pipeline.inputSurface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    )
                }.getOrNull()

                val docStartBootstrap = HtmlBridge.buildBootstrapScript(
                    sampleRate = config.audioSampleRate.hz,
                    channels = config.audioChannels.count,
                    captureAudio = !config.isAudioMuted
                )

                if (vdAttempt != null) {
                    val presAttempt = runCatching {
                        val disp = vdAttempt.display
                        val pres = Presentation(context, disp)
                        pres.window?.apply {
                            setBackgroundDrawable(ColorDrawable(Color.WHITE))
                            setLayout(width, height)
                            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                            addFlags(
                                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            )
                        }

                        val wv = createConfiguredWebView(
                            hostContext = pres.context,
                            bridge = bridge,
                            resourceHandler = resourceHandler,
                            hardwareLayer = true,
                            muteAudioAtBrowser = config.isAudioMuted,
                            requireUserGesture = false,
                            documentStartScript = docStartBootstrap
                        ) {
                            if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                        }.apply {
                            setBackgroundColor(Color.WHITE)
                        }

                        val ticker = View(pres.context).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            alpha = 0.01f
                        }

                        val container = FrameLayout(pres.context).apply {
                            setBackgroundColor(Color.WHITE)
                            addView(
                                wv,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            addView(
                                ticker,
                                FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START)
                            )
                        }
                        pres.setContentView(container)
                        pres.show()
                        pres.window?.setLayout(width, height)
                        Triple(pres, wv, ticker)
                    }

                    if (presAttempt.isSuccess) {
                        val (pres, wv, ticker) = presAttempt.getOrThrow()
                        virtualDisplay = vdAttempt
                        presentation = pres
                        webView = wv
                        pulseView = ticker
                        usingPresentation = true
                    } else {
                        runCatching { vdAttempt.setSurface(null) }
                        runCatching { vdAttempt.release() }
                    }
                }

                if (!usingPresentation) {
                    // Crash-free Software Layer fallback drawing directly to MediaCodec Surface
                    webView = createConfiguredWebView(
                        hostContext = context,
                        bridge = bridge,
                        resourceHandler = resourceHandler,
                        hardwareLayer = false,
                        muteAudioAtBrowser = config.isAudioMuted,
                        requireUserGesture = false,
                        documentStartScript = docStartBootstrap
                    ) {
                        if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                    }.apply {
                        setBackgroundColor(Color.WHITE)
                        dispatchWindowVisibilityChanged(View.VISIBLE)
                        measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                        )
                        layout(0, 0, width, height)
                    }
                }

                webView?.onResume()
                webView?.loadUrl(SafWebResourceHandler.ENTRY_URL)
            }

            withTimeoutOrNull(12_000L) { pageLoaded.await() }
            delay(120L)

            bridge.audioChunkQueue.clear()
            bridge.isCapturingAudio = !config.isAudioMuted

            withContext(Dispatchers.Main) {
                val bootstrap = HtmlBridge.buildBootstrapScript(
                    sampleRate = config.audioSampleRate.hz,
                    channels = config.audioChannels.count,
                    captureAudio = !config.isAudioMuted
                )
                webView?.evaluateJavascript(
                    "$bootstrap; if (window.__html2mp4PrepareAndStart) { window.__html2mp4PrepareAndStart(); } else { window.__html2mp4StartPlayback(); if (window.AndroidHtml2Mp4) window.AndroidHtml2Mp4.onPlaybackStarted(); }",
                    null
                )
            }

            // Wait until HTML fonts finish loading, media resets to 0.00s, and Frame 0 playback starts
            withTimeoutOrNull(3_000L) { bridge.playbackStartedDeferred.await() }

            val frameDurationMs = (1000L / fps).coerceAtLeast(16L)
            val startWallMs = System.currentTimeMillis()
            val silenceFrameBytes = ByteArray((config.audioSampleRate.hz / fps) * 2 * config.audioChannels.count)
            var hasReceivedRealAudio = false
            var frameIdx = 0

            while (coroutineContext.isActive) {
                val nowElapsedMs = System.currentTimeMillis() - startWallMs
                if (nowElapsedMs >= durationMs && frameIdx > 0) break

                if (memoryGuard.shouldThrottleFrameQueue()) {
                    delay(8L)
                }

                withContext(Dispatchers.Main) {
                    val wv = webView ?: return@withContext
                    if (usingPresentation) {
                        pulseView?.let { pv ->
                            pv.alpha = if (frameIdx % 2 == 0) 0.01f else 0.02f
                            pv.invalidate()
                        }
                        wv.invalidate()
                    } else {
                        val surfCanvas = pipeline.inputSurface.lockCanvas(null)
                        try {
                            surfCanvas.drawColor(Color.WHITE)
                            wv.draw(surfCanvas)
                        } finally {
                            pipeline.inputSurface.unlockCanvasAndPost(surfCanvas)
                        }
                    }
                }

                pipeline.drainVideo(endOfStream = false)

                val afterFrameElapsedMs = System.currentTimeMillis() - startWallMs
                if (!config.isAudioMuted) {
                    while (true) {
                        val chunk = bridge.audioChunkQueue.poll() ?: break
                        hasReceivedRealAudio = true
                        pipeline.feedAudioPcm(chunk.pcmBytes)
                    }
                    // Never inject synthetic silence gaps into an active live audio stream (causes stuttering).
                    // Only keep track alive with silence if the page genuinely produces zero audio.
                    if (!hasReceivedRealAudio && afterFrameElapsedMs > 1_000L) {
                        var padGuard = 0
                        while (pipeline.audioWrittenDurationMs() + 400L < afterFrameElapsedMs && padGuard < 4) {
                            pipeline.feedAudioPcm(silenceFrameBytes)
                            padGuard++
                        }
                    }
                }

                frameIdx++
                val targetNextMs = frameIdx * frameDurationMs
                val sleepCheckElapsedMs = System.currentTimeMillis() - startWallMs
                if (targetNextMs > sleepCheckElapsedMs && sleepCheckElapsedMs < durationMs) {
                    delay((targetNextMs - sleepCheckElapsedMs).coerceAtMost(durationMs - sleepCheckElapsedMs))
                }

                if (frameIdx == 1 || frameIdx % 4 == 0 || afterFrameElapsedMs >= durationMs) {
                    val elapsed = afterFrameElapsedMs.coerceAtLeast(1L)
                    val pct = ((elapsed.toFloat() / durationMs.toFloat()) * 100f).roundToInt().coerceIn(0, 99)
                    val remainingMs = (durationMs - elapsed).coerceAtLeast(0L)
                    onProgress(
                        RenderProgress(
                            percent = pct,
                            currentFrame = frameIdx,
                            totalFrames = totalFrames,
                            etaFormatted = formatEta(remainingMs),
                            elapsedMs = elapsed,
                            phaseLabel = "Encoding Frame $frameIdx (~$totalFrames)"
                        )
                    )
                }
            }

            if (!config.isAudioMuted) {
                // Allow the final ScriptProcessor audio callback (~85ms window) to cross the JS bridge
                delay(90L)
                while (true) {
                    val chunk = bridge.audioChunkQueue.poll() ?: break
                    pipeline.feedAudioPcm(chunk.pcmBytes)
                }
                var endPadGuard = 0
                while (pipeline.audioWrittenDurationMs() + 60L < durationMs && endPadGuard < 32) {
                    pipeline.feedAudioPcm(silenceFrameBytes)
                    endPadGuard++
                }
            }

            bridge.isCapturingAudio = false
            onProgress(
                RenderProgress(
                    percent = 99,
                    currentFrame = frameIdx,
                    totalFrames = totalFrames,
                    etaFormatted = "00:01",
                    elapsedMs = System.currentTimeMillis() - startWallMs,
                    phaseLabel = "Finalisasi MP4 Muxer..."
                )
            )

            if (!config.isAudioMuted) {
                pipeline.finishAudioStream()
            }
            pipeline.drainVideo(endOfStream = true)
        } finally {
            bridge.isCapturingAudio = false
            withContext(Dispatchers.Main) {
                destroyWebViewSafely(webView)
                runCatching { presentation?.dismiss() }
                runCatching { virtualDisplay?.setSurface(null) }
                runCatching { virtualDisplay?.release() }
            }
            pipeline.release()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(
        hostContext: Context,
        bridge: HtmlBridge,
        resourceHandler: SafWebResourceHandler,
        hardwareLayer: Boolean,
        muteAudioAtBrowser: Boolean,
        requireUserGesture: Boolean,
        documentStartScript: String?,
        onFinished: () -> Unit
    ): WebView {
        return WebView(hostContext).apply {
            setLayerType(
                if (hardwareLayer) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_SOFTWARE,
                null
            )
            if (muteAudioAtBrowser && WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
                runCatching { WebViewCompat.setAudioMuted(this, true) }
            }
            if (!documentStartScript.isNullOrBlank() &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            ) {
                runCatching {
                    WebViewCompat.addDocumentStartJavaScript(this, documentStartScript, setOf("*"))
                }
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = requireUserGesture
                allowFileAccess = false
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            addJavascriptInterface(bridge, "AndroidHtml2Mp4")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return resourceHandler.handleRequest(request) ?: super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Handler(Looper.getMainLooper()).postDelayed({ onFinished() }, 150L)
                }
            }
        }
    }

    private fun destroyWebViewSafely(webView: WebView?) {
        webView ?: return
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(false)
            webView.removeAllViews()
            webView.destroy()
        }
    }

    private fun formatEta(remainingMs: Long): String {
        val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
