package com.html2mp4.renderer.engine

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.html2mp4.renderer.model.RenderConfig
import com.html2mp4.renderer.model.RenderDiagnosticsReport
import com.html2mp4.renderer.model.RenderPhase
import com.html2mp4.renderer.model.RenderPipelineException
import com.html2mp4.renderer.model.RenderTimelineMode
import com.html2mp4.renderer.model.RenderTimelineSummary
import com.html2mp4.renderer.util.ChipsetOptimizer
import com.html2mp4.renderer.util.MemoryGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
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
    ): RenderTimelineSummary {
        val fps = config.frameRate.fps
        val isDeterministic = config.timelineMode == RenderTimelineMode.DETERMINISTIC
        val timelineEngine = DeterministicTimelineEngine(fps = fps, durationMs = durationMs)
        val totalFrames = timelineEngine.totalFrames

        val deviceProfile = ChipsetOptimizer.detect(context)
        val bridge = HtmlBridge(config.audioSampleRate.hz, config.audioChannels.count).apply {
            captureAudioRequested = !config.isAudioMuted
        }
        val resourceHandler = SafWebResourceHandler(context, htmlUri, assetsTreeUri)
        val pipeline = MediaCodecMuxerPipeline(config, deviceProfile, tempOutputFile, targetDurationMs = durationMs)
        val eglRenderer = EglSurfaceRenderer()
        val pageLoaded = CompletableDeferred<Unit>()
        val fatalWebViewError = AtomicReference<String?>(null)
        var currentPhase = RenderPhase.INITIALIZING
        memoryGuard.resetSessionStats()

        var webView: WebView? = null
        var virtualDisplay: VirtualDisplay? = null
        var presentation: Presentation? = null
        var pulseView: View? = null
        var frameBitmap: Bitmap? = null
        var bitmapCanvas: Canvas? = null
        var usingPresentation = false
        var usedBitmapFallback = false

        try {
            currentPhase = RenderPhase.MEDIA_CODEC_PREPARE
            pipeline.prepare()
            val width = pipeline.encodedWidth
            val height = pipeline.encodedHeight

            currentPhase = RenderPhase.EGL_SURFACE_PREPARE
            eglRenderer.prepare(pipeline.inputSurface, width, height)
            val oesSurface = eglRenderer.oesInputSurface

            currentPhase = RenderPhase.WEBVIEW_LOAD

            val audioBootstrap = HtmlBridge.buildBootstrapScript(
                sampleRate = config.audioSampleRate.hz,
                channels = config.audioChannels.count,
                captureAudio = !config.isAudioMuted
            )
            val docStartScript = if (isDeterministic) {
                DeterministicTimelineEngine.buildVirtualClockBootstrap(fps) + "\n;" + audioBootstrap
            } else {
                audioBootstrap
            }

            withContext(Dispatchers.Main) {
                runCatching { WebView.enableSlowWholeDocumentDraw() }

                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val densityDpi = DisplayMetrics.DENSITY_MEDIUM

                val vdAttempt = if (oesSurface != null) {
                    runCatching {
                        dm.createVirtualDisplay(
                            "HTML2MP4_Offscreen",
                            width,
                            height,
                            densityDpi,
                            oesSurface,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                        )
                    }.getOrNull()
                } else {
                    null
                }

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
                            documentStartScript = docStartScript,
                            onFatalError = { err ->
                                fatalWebViewError.set(err)
                                if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                            }
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
                    usedBitmapFallback = true
                    webView = createConfiguredWebView(
                        hostContext = context,
                        bridge = bridge,
                        resourceHandler = resourceHandler,
                        hardwareLayer = false,
                        muteAudioAtBrowser = config.isAudioMuted,
                        requireUserGesture = false,
                        documentStartScript = docStartScript,
                        onFatalError = { err ->
                            fatalWebViewError.set(err)
                            if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                        }
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

            val loaded = withTimeoutOrNull(12_000L) { pageLoaded.await() }
            if (loaded == null) {
                throw RenderPipelineException(
                    phase = RenderPhase.WEBVIEW_LOAD,
                    technicalDetail = "Timeout memuat dokumen HTML di WebView (>12 detik)."
                )
            }
            fatalWebViewError.get()?.let { err ->
                throw RenderPipelineException(phase = RenderPhase.WEBVIEW_LOAD, technicalDetail = err)
            }
            delay(150L)

            currentPhase = RenderPhase.FRAME_CAPTURE

            // Flush any initial loading-stage texture before real playback begins
            eglRenderer.discardPendingOesFrames()

            withContext(Dispatchers.Main) {
                webView?.evaluateJavascript(
                    "$docStartScript; if (window.__html2mp4PrepareAndStart) { window.__html2mp4PrepareAndStart(); } else { window.__html2mp4StartPlayback(); if (window.AndroidHtml2Mp4) window.AndroidHtml2Mp4.onPlaybackStarted(); }",
                    null
                )
            }

            // Wait until fonts, images, media reset, and Frame 0 playback are armed
            withTimeoutOrNull(4_000L) { bridge.playbackStartedDeferred.await() }
            if (!bridge.isCapturingAudio && !config.isAudioMuted) {
                bridge.resetAudioQueue()
                bridge.isCapturingAudio = true
            }

            val silenceFrameBytes = ByteArray((config.audioSampleRate.hz / fps) * 2 * config.audioChannels.count)
            var hasReceivedRealAudio = false
            val startWallMs = System.currentTimeMillis()
            var lastReportedFrame = 0

            if (isDeterministic) {
                // =========================================================================
                // MODE: DETERMINISTIC (virtual clock t = frameIndex / fps, zero silent drops)
                // =========================================================================
                withContext(Dispatchers.Main) {
                    webView?.evaluateJavascript("if (window.__html2mp4EnableVirtualClock) window.__html2mp4EnableVirtualClock();", null)
                }

                while (coroutineContext.isActive && timelineEngine.hasNextFrame()) {
                    fatalWebViewError.get()?.let { err ->
                        throw RenderPipelineException(phase = RenderPhase.FRAME_CAPTURE, technicalDetail = err)
                    }
                    val tick = timelineEngine.advance() ?: break
                    lastReportedFrame = tick.frameIndex + 1

                    if (tick.frameIndex % 8 == 0) {
                        memoryGuard.sampleSessionMemory()
                    }
                    if (memoryGuard.shouldThrottleFrameQueue()) {
                        delay(8L)
                    }

                    // 1. Apply exact virtual timestamp on WebView & await JS+CSS+WAAPI readiness off UI thread
                    val preparedDeferred = bridge.armFramePrepared(tick.frameIndex)
                    withContext(Dispatchers.Main) {
                        val wv = webView ?: return@withContext
                        wv.evaluateJavascript(
                            "if (window.__html2mp4StepTo) { window.__html2mp4StepTo(${tick.htmlAnimationTimeMs}, ${tick.frameIndex}); } else if (window.AndroidHtml2Mp4) { window.AndroidHtml2Mp4.onFramePrepared(${tick.frameIndex}, '{}'); }",
                            null
                        )
                    }
                    bridge.awaitFramePrepared(
                        frameSeq = tick.frameIndex,
                        deferred = preparedDeferred,
                        timeoutMs = if (tick.frameIndex == 0) 600L else 220L
                    )

                    // 2. Capture frame using exact monotonic encoderPresentationTimeNs
                    var frameSubmitted = false
                    if (usingPresentation) {
                        withContext(Dispatchers.Main) {
                            val wv = webView ?: return@withContext
                            pulseView?.let { pv ->
                                pv.alpha = if (tick.frameIndex % 2 == 0) 0.01f else 0.02f
                                pv.invalidate()
                            }
                            wv.invalidate()
                        }

                        val waitMs = if (tick.frameIndex == 0) 280L else 48L
                        when (eglRenderer.drawOesFrameChecked(tick.encoderPresentationTimeNs, waitMs = waitMs, allowRepeatPrevious = false)) {
                            EglSurfaceRenderer.OesFrameStatus.NEW_FRAME_RENDERED -> {
                                timelineEngine.recordSubmittedFrame()
                                frameSubmitted = true
                            }
                            EglSurfaceRenderer.OesFrameStatus.REPEATED_PREVIOUS_FRAME -> {
                                timelineEngine.recordRepeatedFrame()
                                frameSubmitted = true
                            }
                            EglSurfaceRenderer.OesFrameStatus.NO_FRAME_AVAILABLE -> {
                                if (!eglRenderer.hasOesFrames()) {
                                    // GPU VirtualDisplay composition unavailable; switch cleanly to Bitmap fallback for THIS frame & onward
                                    usingPresentation = false
                                    usedBitmapFallback = true
                                    withContext(Dispatchers.Main) {
                                        webView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                    }
                                } else {
                                    // Static scene produced identical pixels without compositor swap; repeat explicitly & track count
                                    if (eglRenderer.repeatLastFrame(tick.encoderPresentationTimeNs)) {
                                        timelineEngine.recordRepeatedFrame()
                                        frameSubmitted = true
                                    } else {
                                        timelineEngine.recordDroppedOrFailedFrame()
                                    }
                                }
                            }
                        }
                    }

                    if (!usingPresentation && !frameSubmitted) {
                        val bmp = frameBitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                            frameBitmap = it
                            bitmapCanvas = Canvas(it)
                        }
                        withContext(Dispatchers.Main) {
                            val wv = webView ?: return@withContext
                            val cvs = bitmapCanvas ?: return@withContext
                            cvs.drawColor(Color.WHITE)
                            wv.draw(cvs)
                        }
                        eglRenderer.drawBitmapFrame(bmp, tick.encoderPresentationTimeNs)
                        timelineEngine.recordSubmittedFrame()
                    }

                    pipeline.drainVideo(endOfStream = false)

                    if (!config.isAudioMuted) {
                        while (!pipeline.isAudioTargetReached()) {
                            val chunk = bridge.audioChunkQueue.poll() ?: break
                            hasReceivedRealAudio = true
                            pipeline.feedAudioChunk(chunk)
                        }
                        if (pipeline.isAudioTargetReached()) {
                            bridge.isCapturingAudio = false
                            bridge.audioChunkQueue.clear()
                        } else {
                            val targetAudioMs = ((tick.frameIndex + 1).toLong() * 1000L) / fps.toLong()
                            if (!hasReceivedRealAudio && targetAudioMs > 400L) {
                                var padGuard = 0
                                while (!pipeline.isAudioTargetReached() &&
                                    pipeline.audioWrittenDurationMs() + 200L < targetAudioMs &&
                                    padGuard < 4
                                ) {
                                    pipeline.feedSilencePadding(silenceFrameBytes)
                                    padGuard++
                                }
                            }
                        }
                    }

                    if (tick.frameIndex == 0 || (tick.frameIndex + 1) % 4 == 0 || tick.frameIndex + 1 >= totalFrames) {
                        val elapsed = (System.currentTimeMillis() - startWallMs).coerceAtLeast(1L)
                        val pct = (((tick.frameIndex + 1).toFloat() / totalFrames.toFloat()) * 98f).roundToInt().coerceIn(0, 98)
                        val msPerFrame = elapsed.toDouble() / (tick.frameIndex + 1).toDouble()
                        val remainingMs = ((totalFrames - (tick.frameIndex + 1)) * msPerFrame).toLong().coerceAtLeast(0L)
                        onProgress(
                            RenderProgress(
                                percent = pct,
                                currentFrame = tick.frameIndex + 1,
                                totalFrames = totalFrames,
                                etaFormatted = formatEta(remainingMs),
                                elapsedMs = elapsed,
                                phaseLabel = "Deterministik Frame ${tick.frameIndex + 1}/$totalFrames"
                            )
                        )
                    }
                }
            } else {
                // =========================================================================
                // MODE: REALTIME_COMPAT (legacy wall-clock loop preserved as safe fallback)
                // =========================================================================
                val frameDurationMs = (1000L / fps).coerceAtLeast(16L)
                var frameIdx = 0

                while (coroutineContext.isActive) {
                    fatalWebViewError.get()?.let { err ->
                        throw RenderPipelineException(phase = RenderPhase.FRAME_CAPTURE, technicalDetail = err)
                    }
                    val nowElapsedMs = System.currentTimeMillis() - startWallMs
                    if (nowElapsedMs >= durationMs && frameIdx > 0) break

                    if (frameIdx % 8 == 0) {
                        memoryGuard.sampleSessionMemory()
                    }
                    if (memoryGuard.shouldThrottleFrameQueue()) {
                        delay(8L)
                    }

                    if (usingPresentation) {
                        withContext(Dispatchers.Main) {
                            val wv = webView ?: return@withContext
                            pulseView?.let { pv ->
                                pv.alpha = if (frameIdx % 2 == 0) 0.01f else 0.02f
                                pv.invalidate()
                            }
                            wv.invalidate()
                        }

                        val waitMs = if (frameIdx == 0) 280L else 12L
                        val ptsNs = timelineEngine.allocateMonotonicPtsNs(frameIdx)
                        val status = eglRenderer.drawOesFrameChecked(ptsNs, waitMs = waitMs, allowRepeatPrevious = true)
                        if (status == EglSurfaceRenderer.OesFrameStatus.NO_FRAME_AVAILABLE && !eglRenderer.hasOesFrames()) {
                            usingPresentation = false
                            usedBitmapFallback = true
                            withContext(Dispatchers.Main) {
                                webView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                            }
                        } else if (status == EglSurfaceRenderer.OesFrameStatus.NEW_FRAME_RENDERED) {
                            timelineEngine.recordSubmittedFrame()
                            frameIdx++
                        } else if (status == EglSurfaceRenderer.OesFrameStatus.REPEATED_PREVIOUS_FRAME) {
                            timelineEngine.recordRepeatedFrame()
                            frameIdx++
                        }
                    }

                    if (!usingPresentation) {
                        val bmp = frameBitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                            frameBitmap = it
                            bitmapCanvas = Canvas(it)
                        }
                        withContext(Dispatchers.Main) {
                            val wv = webView ?: return@withContext
                            val cvs = bitmapCanvas ?: return@withContext
                            cvs.drawColor(Color.WHITE)
                            wv.draw(cvs)
                        }
                        eglRenderer.drawBitmapFrame(bmp, timelineEngine.allocateMonotonicPtsNs(frameIdx))
                        timelineEngine.recordSubmittedFrame()
                        frameIdx++
                    }

                    val afterDrawMs = System.currentTimeMillis() - startWallMs
                    val expectedFrameIdx = ((afterDrawMs * fps) / 1000L).toInt().coerceAtMost(totalFrames)
                    var catchup = 0
                    while (frameIdx < expectedFrameIdx && catchup < 3) {
                        if (!eglRenderer.repeatLastFrame(timelineEngine.allocateMonotonicPtsNs(frameIdx))) break
                        timelineEngine.recordRepeatedFrame()
                        frameIdx++
                        catchup++
                    }

                    pipeline.drainVideo(endOfStream = false)

                    val afterFrameElapsedMs = System.currentTimeMillis() - startWallMs
                    if (!config.isAudioMuted) {
                        while (!pipeline.isAudioTargetReached()) {
                            val chunk = bridge.audioChunkQueue.poll() ?: break
                            hasReceivedRealAudio = true
                            pipeline.feedAudioChunk(chunk)
                        }
                        if (pipeline.isAudioTargetReached()) {
                            bridge.isCapturingAudio = false
                            bridge.audioChunkQueue.clear()
                        } else if (!hasReceivedRealAudio && afterFrameElapsedMs > 600L) {
                            var padGuard = 0
                            while (!pipeline.isAudioTargetReached() &&
                                pipeline.audioWrittenDurationMs() + 250L < afterFrameElapsedMs &&
                                padGuard < 4
                            ) {
                                pipeline.feedSilencePadding(silenceFrameBytes)
                                padGuard++
                            }
                        }
                    }

                    val targetNextMs = frameIdx * frameDurationMs
                    val sleepCheckElapsedMs = System.currentTimeMillis() - startWallMs
                    if (targetNextMs > sleepCheckElapsedMs && sleepCheckElapsedMs < durationMs) {
                        delay((targetNextMs - sleepCheckElapsedMs).coerceAtMost(durationMs - sleepCheckElapsedMs))
                    }

                    lastReportedFrame = frameIdx
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
                                phaseLabel = "Real-Time Frame $frameIdx (~$totalFrames)"
                            )
                        )
                    }
                }
            }

            if (!config.isAudioMuted) {
                if (!pipeline.isAudioTargetReached()) {
                    delay(90L)
                    while (!pipeline.isAudioTargetReached()) {
                        val chunk = bridge.audioChunkQueue.poll() ?: break
                        hasReceivedRealAudio = true
                        pipeline.feedAudioChunk(chunk)
                    }
                    var endPadGuard = 0
                    while (!pipeline.isAudioTargetReached() &&
                        pipeline.audioWrittenDurationMs() + 40L < durationMs &&
                        endPadGuard < 32
                    ) {
                        pipeline.feedSilencePadding(silenceFrameBytes)
                        endPadGuard++
                    }
                } else {
                    bridge.audioChunkQueue.clear()
                }
            }

            currentPhase = RenderPhase.MP4_FINALIZE
            bridge.isCapturingAudio = false
            val wallElapsed = (System.currentTimeMillis() - startWallMs).coerceAtLeast(1L)
            onProgress(
                RenderProgress(
                    percent = 99,
                    currentFrame = lastReportedFrame,
                    totalFrames = totalFrames,
                    etaFormatted = "00:01",
                    elapsedMs = wallElapsed,
                    phaseLabel = "Finalisasi & Validasi MP4..."
                )
            )

            if (!config.isAudioMuted) {
                pipeline.finishAudioStream()
            }
            pipeline.drainVideo(endOfStream = true)
            if (!config.isAudioMuted && hasReceivedRealAudio) {
                pipeline.sampleFinalTrackDrift()
            }
            val widthOut = pipeline.encodedWidth
            val heightOut = pipeline.encodedHeight
            val videoEncName = pipeline.activeVideoEncoderName
            val audioEncName = pipeline.activeAudioEncoderName
            val audioSummary = bridge.buildAudioSyncSummary(pipeline, isMuted = config.isAudioMuted)

            // Finalize MediaMuxer BEFORE reading track metadata so moov atom is written cleanly
            pipeline.release()

            currentPhase = RenderPhase.MP4_VALIDATION
            val validationReport = withContext(Dispatchers.IO) {
                Mp4PostMuxValidator.validate(
                    mp4File = tempOutputFile,
                    expectedDurationMs = durationMs,
                    expectAudioTrack = !config.isAudioMuted
                )
            }

            if (!validationReport.hasVideoTrack || !validationReport.isPlayable) {
                throw RenderPipelineException(
                    phase = RenderPhase.MP4_VALIDATION,
                    technicalDetail = "Hasil MP4 gagal validasi track/playability: ${validationReport.warnings.joinToString("; ")}"
                )
            }

            val memorySnapshot = memoryGuard.snapshotSessionDiagnostics()
            val diagnostics = RenderDiagnosticsReport(
                timelineMode = config.timelineMode,
                targetFps = fps,
                expectedFrames = totalFrames,
                submittedFrames = timelineEngine.submittedFrames,
                repeatedFrames = timelineEngine.repeatedFrames,
                droppedOrFailedFrames = timelineEngine.droppedOrFailedFrames,
                renderWallDurationMs = wallElapsed,
                videoWidth = if (validationReport.videoWidth > 0) validationReport.videoWidth else widthOut,
                videoHeight = if (validationReport.videoHeight > 0) validationReport.videoHeight else heightOut,
                videoEncoderName = videoEncName,
                audioEncoderName = audioEncName,
                mp4FileSizeBytes = validationReport.fileSizeBytes,
                usedBitmapFallback = usedBitmapFallback,
                memory = memorySnapshot,
                audioSummary = audioSummary,
                validation = validationReport
            )

            return timelineEngine.buildSummary(
                mode = config.timelineMode,
                usedBitmapFallback = usedBitmapFallback,
                renderWallDurationMs = wallElapsed,
                capabilities = bridge.latestCapabilities,
                audioSummary = audioSummary,
                diagnostics = diagnostics
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: RenderPipelineException) {
            throw e
        } catch (t: Throwable) {
            throw RenderPipelineException(
                phase = currentPhase,
                technicalDetail = "[${deviceProfile.socFamily.displayName}] ${t::class.java.simpleName}: ${t.localizedMessage ?: "error tanpa detail"}",
                cause = t
            )
        } finally {
            bridge.isCapturingAudio = false
            withContext(Dispatchers.Main) {
                destroyWebViewSafely(webView)
                runCatching { presentation?.dismiss() }
                runCatching { virtualDisplay?.setSurface(null) }
                runCatching { virtualDisplay?.release() }
            }
            runCatching { frameBitmap?.recycle() }
            eglRenderer.release()
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
        onFatalError: ((String) -> Unit)? = null,
        onFinished: () -> Unit
    ): WebView {
        return WebView(hostContext).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
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
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            addJavascriptInterface(bridge, "AndroidHtml2Mp4")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return resourceHandler.handleRequest(request) ?: super.shouldInterceptRequest(view, request)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val code = error?.errorCode ?: -1
                        val desc = error?.description?.toString() ?: "Main frame error"
                        onFatalError?.invoke("WebView main-frame error ($code): $desc")
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val crashed = detail?.didCrash() == true
                    onFatalError?.invoke(
                        if (crashed) "Proses renderer WebView crash saat rendering."
                        else "Proses renderer WebView dihentikan oleh sistem (low memory)."
                    )
                    return true
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
