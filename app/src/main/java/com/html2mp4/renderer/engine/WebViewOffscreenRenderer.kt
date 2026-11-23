package com.html2mp4.renderer.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.HardwareRenderer
import android.graphics.RenderNode
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
 * Renders HTML offscreen using hardware-accelerated WebView + API 29 HardwareRenderer
 * directly into MediaCodec's InputSurface, keeping JVM heap usage minimal on Exynos 850.
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
    suspend fun inspectDurationOnly(htmlUri: Uri, assetsTreeUri: Uri?): Long {
        val bridge = HtmlBridge(48000, 2)
        val resourceHandler = SafWebResourceHandler(context, htmlUri, assetsTreeUri)
        val pageLoaded = CompletableDeferred<Unit>()
        var webView: WebView? = null

        return try {
            withContext(Dispatchers.Main) {
                webView = createConfiguredWebView(bridge, resourceHandler) {
                    if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                }
                webView?.layout(0, 0, 1280, 720)
                webView?.loadUrl(SafWebResourceHandler.ENTRY_URL)
            }

            withTimeoutOrNull(8_000L) { pageLoaded.await() }
            delay(400L)
            withContext(Dispatchers.Main) {
                val script = HtmlBridge.buildBootstrapScript(48000, 2, captureAudio = false)
                webView?.evaluateJavascript(script + "; window.__html2mp4DetectDuration();", null)
            }
            withTimeoutOrNull(3_500L) { bridge.durationDeferred.await() } ?: 10_000L
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
        var hardwareRenderer: HardwareRenderer? = null
        var renderNode: RenderNode? = null

        try {
            pipeline.prepare()

            withContext(Dispatchers.Main) {
                webView = createConfiguredWebView(bridge, resourceHandler) {
                    if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                }.apply {
                    measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, width, height)
                }

                renderNode = RenderNode("html2mp4_frame").apply {
                    setPosition(0, 0, width, height)
                }
                hardwareRenderer = HardwareRenderer().apply {
                    setSurface(pipeline.inputSurface)
                    setContentRoot(renderNode)
                }

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
            val frameDurationNs = 1_000_000_000L / fps
            val frameDurationMs = 1000L / fps
            val startWallMs = System.currentTimeMillis()
            val silenceFrameBytes = ByteArray((config.audioSampleRate.hz / fps) * 2 * config.audioChannels.count)

            for (frameIdx in 0 until totalFrames) {
                if (!coroutineContext.isActive) break

                // Throttle slightly if Exynos 850 RAM gets tight
                if (memoryGuard.shouldThrottleFrameQueue()) {
                    delay(8L)
                }

                val targetElapsedMs = (frameIdx + 1) * frameDurationMs
                val actualElapsedMs = System.currentTimeMillis() - startWallMs
                if (targetElapsedMs > actualElapsedMs) {
                    delay(targetElapsedMs - actualElapsedMs)
                }

                val ptsNs = frameIdx * frameDurationNs
                withContext(Dispatchers.Main) {
                    val node = renderNode ?: return@withContext
                    val renderer = hardwareRenderer ?: return@withContext
                    val wv = webView ?: return@withContext

                    val canvas = node.beginRecording(width, height)
                    wv.draw(canvas)
                    node.endRecording()

                    renderer.createRenderRequest()
                        .setVsyncTime(ptsNs)
                        .syncAndDraw()
                }

                pipeline.drainVideo(endOfStream = false)

                if (!config.isAudioMuted) {
                    var drainedAny = false
                    while (true) {
                        val chunk = bridge.audioChunkQueue.poll() ?: break
                        pipeline.feedAudioPcm(chunk.pcmBytes)
                        drainedAny = true
                    }
                    // Keep audio track timeline synchronized even during silent HTML segments
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
                runCatching { hardwareRenderer?.destroy() }
                runCatching { renderNode?.discardDisplayList() }
                destroyWebViewSafely(webView)
            }
            pipeline.release()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(
        bridge: HtmlBridge,
        resourceHandler: SafWebResourceHandler,
        onFinished: () -> Unit
    ): WebView {
        return WebView(context).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
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
