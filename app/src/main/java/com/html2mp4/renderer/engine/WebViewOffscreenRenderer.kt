package com.html2mp4.renderer.engine

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.html2mp4.renderer.model.RenderConfig
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
            delay(350L)
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

        val bridge = HtmlBridge(config.audioSampleRate.hz, config.audioChannels.count)
        val resourceHandler = SafWebResourceHandler(context, htmlUri, assetsTreeUri)
        val pipeline = MediaCodecMuxerPipeline(config, tempOutputFile)
        val pageLoaded = CompletableDeferred<Unit>()

        var webView: WebView? = null
        var virtualDisplay: VirtualDisplay? = null
        var presentation: Presentation? = null
        var reusableBitmap: Bitmap? = null
        var bitmapCanvas: Canvas? = null
        var usingPresentation = false

        try {
            pipeline.prepare()

            withContext(Dispatchers.Main) {
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val densityDpi = context.resources.displayMetrics.densityDpi
                val probeTex = SurfaceTexture(0).apply { setDefaultBufferSize(width, height) }
                val probeSurface = Surface(probeTex)

                val vdAttempt = runCatching {
                    dm.createVirtualDisplay(
                        "HTML2MP4_Offscreen",
                        width,
                        height,
                        densityDpi,
                        probeSurface,
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
                        }
                        val container = FrameLayout(pres.context).apply {
                            setBackgroundColor(Color.WHITE)
                            addView(
                                wv,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                        pres.setContentView(container)
                        pres.show()
                        vdAttempt.setSurface(pipeline.inputSurface)
                        Pair(pres, wv)
                    }

                    if (presAttempt.isSuccess) {
                        val (pres, wv) = presAttempt.getOrThrow()
                        virtualDisplay = vdAttempt
                        presentation = pres
                        webView = wv
                        usingPresentation = true
                    } else {
                        runCatching { vdAttempt.setSurface(null) }
                        runCatching { vdAttempt.release() }
                    }
                }

                runCatching { probeSurface.release() }
                runCatching { probeTex.release() }

                if (!usingPresentation) {
                    // 100% crash-free Software Layer fallback with single reusable Bitmap
                    reusableBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmapCanvas = Canvas(reusableBitmap!!)
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
            delay(250L)

            withContext(Dispatchers.Main) {
                val bootstrap = HtmlBridge.buildBootstrapScript(
                    sampleRate = config.audioSampleRate.hz,
                    channels = config.audioChannels.count,
                    captureAudio = !config.isAudioMuted
                )
                webView?.evaluateJavascript(
                    "$bootstrap; window.__html2mp4StartPlayback();",
                    null
                )
            }

            bridge.isCapturingAudio = !config.isAudioMuted
            val frameDurationMs = 1000L / fps
            val startWallMs = System.currentTimeMillis()
            val silenceFrameBytes = ByteArray((config.audioSampleRate.hz / fps) * 2 * config.audioChannels.count)

            for (frameIdx in 0 until totalFrames) {
                if (!coroutineContext.isActive) break

                if (memoryGuard.shouldThrottleFrameQueue()) {
                    delay(8L)
                }

                val targetElapsedMs = (frameIdx + 1) * frameDurationMs
                val actualElapsedMs = System.currentTimeMillis() - startWallMs
                if (targetElapsedMs > actualElapsedMs) {
                    delay(targetElapsedMs - actualElapsedMs)
                }

                withContext(Dispatchers.Main) {
                    val wv = webView ?: return@withContext
                    if (usingPresentation) {
                        wv.invalidate()
                    } else {
                        val bmp = reusableBitmap ?: return@withContext
                        val bCanvas = bitmapCanvas ?: return@withContext
                        bmp.eraseColor(Color.WHITE)
                        wv.draw(bCanvas)
                        val surfCanvas = pipeline.inputSurface.lockCanvas(null)
                        try {
                            surfCanvas.drawBitmap(bmp, 0f, 0f, null)
                        } finally {
                            pipeline.inputSurface.unlockCanvasAndPost(surfCanvas)
                        }
                    }
                }

                pipeline.drainVideo(endOfStream = false)

                if (!config.isAudioMuted) {
                    var drainedAny = false
                    while (true) {
                        val chunk = bridge.audioChunkQueue.poll() ?: break
                        pipeline.feedAudioPcm(chunk.pcmBytes)
                        drainedAny = true
                    }
                    if (!drainedAny && frameIdx % 2 == 0) {
                        pipeline.feedAudioPcm(silenceFrameBytes)
                    }
                }

                if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                    val elapsed = (System.currentTimeMillis() - startWallMs).coerceAtLeast(1L)
                    val pct = (((frameIdx + 1).toFloat() / totalFrames) * 100f).roundToInt().coerceIn(0, 99)
                    val msPerFrame = elapsed.toDouble() / (frameIdx + 1)
                    val remainingMs = ((totalFrames - (frameIdx + 1)) * msPerFrame).toLong().coerceAtLeast(0L)
                    onProgress(
                        RenderProgress(
                            percent = pct,
                            currentFrame = frameIdx + 1,
                            totalFrames = totalFrames,
                            etaFormatted = formatEta(remainingMs),
                            elapsedMs = elapsed,
                            phaseLabel = "Encoding Frame ${frameIdx + 1}/$totalFrames"
                        )
                    )
                }
            }

            bridge.isCapturingAudio = false
            onProgress(
                RenderProgress(
                    percent = 99,
                    currentFrame = totalFrames,
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
                runCatching { reusableBitmap?.recycle() }
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
