package com.html2mp4.renderer.engine

import com.html2mp4.renderer.model.HtmlAnimationCapabilities
import com.html2mp4.renderer.model.RenderTimelineMode
import com.html2mp4.renderer.model.RenderTimelineSummary
import kotlin.math.ceil

/**
 * Snapshot representing one scheduled render step across three explicitly separated time domains:
 * 1) htmlAnimationTimeMs: deterministic virtual clock t = (frameIndex * 1000.0) / fps
 * 2) captureWallStartMs: host wall-clock time when frame capture started (diagnostics/ETA only)
 * 3) encoderPresentationTimeNs: strictly monotonic EGL/MediaCodec presentation timestamp
 */
data class TimelineFrameTick(
    val frameIndex: Int,
    val htmlAnimationTimeMs: Double,
    val encoderPresentationTimeNs: Long,
    val captureWallStartMs: Long
)

/**
 * Controls deterministic frame progression (t = frameIndex / fps), monotonic PTS generation,
 * frame accounting (submitted vs repeated vs dropped), and the WebView virtual clock bootstrap.
 */
class DeterministicTimelineEngine(
    fps: Int,
    durationMs: Long
) {
    val safeFps: Int = fps.coerceIn(1, 120)
    val safeDurationMs: Long = durationMs.coerceAtLeast(1_000L)
    val frameIntervalNs: Long = 1_000_000_000L / safeFps.toLong()
    val totalFrames: Int = ceil((safeDurationMs.toDouble() * safeFps.toDouble()) / 1000.0)
        .toInt()
        .coerceAtLeast(1)

    private var cursorFrameIndex: Int = 0
    private var lastAllocatedPtsNs: Long = -1L

    var submittedFrames: Int = 0
        private set
    var repeatedFrames: Int = 0
        private set
    var droppedOrFailedFrames: Int = 0
        private set

    fun hasNextFrame(): Boolean = cursorFrameIndex < totalFrames

    fun setTime(frameIndex: Int): TimelineFrameTick {
        val clamped = frameIndex.coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
        cursorFrameIndex = clamped + 1
        val htmlMs = (clamped.toDouble() * 1000.0) / safeFps.toDouble()
        val ptsNs = allocateMonotonicPtsNs(clamped)
        return TimelineFrameTick(
            frameIndex = clamped,
            htmlAnimationTimeMs = htmlMs,
            encoderPresentationTimeNs = ptsNs,
            captureWallStartMs = System.currentTimeMillis()
        )
    }

    fun advance(): TimelineFrameTick? {
        if (!hasNextFrame()) return null
        return setTime(cursorFrameIndex)
    }

    /**
     * Guarantees strictly monotonic non-decreasing encoder/EGL presentation timestamps
     * aligned with the chosen target FPS, even if catch-up or fallback occurs.
     */
    fun allocateMonotonicPtsNs(outputIndex: Int): Long {
        val candidate = outputIndex.toLong().coerceAtLeast(0L) * frameIntervalNs
        val safePts = if (lastAllocatedPtsNs >= 0L && candidate <= lastAllocatedPtsNs) {
            lastAllocatedPtsNs + frameIntervalNs
        } else {
            candidate
        }
        lastAllocatedPtsNs = safePts
        return safePts
    }

    fun recordSubmittedFrame() {
        submittedFrames++
    }

    fun recordRepeatedFrame() {
        submittedFrames++
        repeatedFrames++
    }

    fun recordDroppedOrFailedFrame() {
        droppedOrFailedFrames++
    }

    fun buildSummary(
        mode: RenderTimelineMode,
        usedBitmapFallback: Boolean,
        renderWallDurationMs: Long,
        capabilities: HtmlAnimationCapabilities,
        audioSummary: com.html2mp4.renderer.model.AudioSyncSummary? = null,
        diagnostics: com.html2mp4.renderer.model.RenderDiagnosticsReport? = null
    ): RenderTimelineSummary {
        return RenderTimelineSummary(
            timelineMode = mode,
            targetFps = safeFps,
            expectedFrames = totalFrames,
            submittedFrames = submittedFrames,
            repeatedFrames = repeatedFrames,
            droppedOrFailedFrames = droppedOrFailedFrames,
            usedBitmapFallback = usedBitmapFallback,
            renderWallDurationMs = renderWallDurationMs,
            capabilities = capabilities,
            audioSummary = audioSummary,
            diagnostics = diagnostics
        )
    }

    companion object {
        /**
         * Injects a safe, opt-in virtual clock into WebView at document_start.
         * Time APIs delegate transparently to Chromium native clocks during initial page load,
         * and switch to deterministic virtual stepping only once __html2mp4EnableVirtualClock() runs.
         */
        fun buildVirtualClockBootstrap(fps: Int): String {
            val safeFps = fps.coerceIn(1, 120)
            return """
                (function() {
                  if (window.__html2mp4VirtualClockInstalled) return;
                  window.__html2mp4VirtualClockInstalled = true;

                  const TARGET_FPS = $safeFps;
                  const realRaf = window.requestAnimationFrame.bind(window);
                  const realCancelRaf = window.cancelAnimationFrame.bind(window);
                  const realSetTimeout = window.setTimeout.bind(window);
                  const realClearTimeout = window.clearTimeout.bind(window);
                  const realSetInterval = window.setInterval.bind(window);
                  const realClearInterval = window.clearInterval.bind(window);
                  const realPerfNow = (window.performance && window.performance.now)
                    ? window.performance.now.bind(window.performance)
                    : function() { return Date.now(); };
                  const realDateNow = Date.now.bind(Date);

                  window.__html2mp4RealRaf = realRaf;
                  window.__html2mp4RealCancelRaf = realCancelRaf;
                  window.__html2mp4RealSetTimeout = realSetTimeout;
                  window.__html2mp4RealClearTimeout = realClearTimeout;
                  window.__html2mp4RealSetInterval = realSetInterval;
                  window.__html2mp4RealClearInterval = realClearInterval;

                  let virtualClockActive = false;
                  let virtualTimeMs = 0;
                  const baseDateMs = realDateNow();

                  let nextRafId = 1;
                  const virtualRafQueue = new Map();
                  let nextTimerId = 1;
                  const virtualTimerQueue = new Map();
                  const animStartOffsetMap = new WeakMap();

                  const capsState = {
                    usesRaf: false,
                    usesWaapi: false,
                    usesTimers: false,
                    usesGsap: false,
                    hasMedia: false,
                    hasWebAudio: false,
                    hasCrossOriginIframes: false
                  };

                  window.requestAnimationFrame = function(callback) {
                    capsState.usesRaf = true;
                    if (!virtualClockActive) {
                      return realRaf(callback);
                    }
                    const id = nextRafId++;
                    virtualRafQueue.set(id, callback);
                    return id;
                  };

                  window.cancelAnimationFrame = function(id) {
                    if (virtualRafQueue.has(id)) {
                      virtualRafQueue.delete(id);
                    }
                    if (!virtualClockActive) {
                      return realCancelRaf(id);
                    }
                  };

                  if (window.performance) {
                    try {
                      window.performance.now = function() {
                        return virtualClockActive ? virtualTimeMs : realPerfNow();
                      };
                    } catch (e) {}
                  }

                  Date.now = function() {
                    return virtualClockActive ? (baseDateMs + Math.round(virtualTimeMs)) : realDateNow();
                  };

                  window.setTimeout = function(fn, delay) {
                    const args = Array.prototype.slice.call(arguments, 2);
                    if (!virtualClockActive || typeof fn !== 'function') {
                      return realSetTimeout.apply(window, arguments);
                    }
                    capsState.usesTimers = true;
                    const id = nextTimerId++;
                    const d = Math.max(0, Number(delay) || 0);
                    virtualTimerQueue.set(id, {
                      fn: fn,
                      args: args,
                      dueAt: virtualTimeMs + d,
                      intervalMs: null
                    });
                    return id;
                  };

                  window.clearTimeout = function(id) {
                    if (virtualTimerQueue.has(id)) {
                      virtualTimerQueue.delete(id);
                    }
                    realClearTimeout(id);
                  };

                  window.setInterval = function(fn, delay) {
                    const args = Array.prototype.slice.call(arguments, 2);
                    if (!virtualClockActive || typeof fn !== 'function') {
                      return realSetInterval.apply(window, arguments);
                    }
                    capsState.usesTimers = true;
                    const id = nextTimerId++;
                    const step = Math.max(1000 / TARGET_FPS, Number(delay) || 16);
                    virtualTimerQueue.set(id, {
                      fn: fn,
                      args: args,
                      dueAt: virtualTimeMs + step,
                      intervalMs: step
                    });
                    return id;
                  };

                  window.clearInterval = function(id) {
                    if (virtualTimerQueue.has(id)) {
                      virtualTimerQueue.delete(id);
                    }
                    realClearInterval(id);
                  };

                  function inspectDocumentCapabilities() {
                    capsState.hasWebAudio = (window.__html2mp4AudioCtxCount || 0) > 0;
                    if (document.querySelector('video, audio')) {
                      capsState.hasMedia = true;
                    }
                    if (window.gsap || (window.OPENER && window.OPENER.tl)) {
                      capsState.usesGsap = true;
                    }
                    try {
                      const frames = document.querySelectorAll('iframe');
                      for (let i = 0; i < frames.length; i++) {
                        try {
                          const doc = frames[i].contentDocument;
                          if (!doc) capsState.hasCrossOriginIframes = true;
                        } catch (err) {
                          capsState.hasCrossOriginIframes = true;
                        }
                      }
                    } catch (e) {}
                    return {
                      usesRaf: capsState.usesRaf,
                      usesWaapi: capsState.usesWaapi,
                      usesTimers: capsState.usesTimers,
                      usesGsap: capsState.usesGsap,
                      hasMedia: capsState.hasMedia,
                      hasWebAudio: capsState.hasWebAudio,
                      hasCrossOriginIframes: capsState.hasCrossOriginIframes
                    };
                  }

                  window.__html2mp4EnableVirtualClock = function() {
                    virtualClockActive = true;
                    virtualTimeMs = 0;
                    try {
                      if (window.AndroidHtml2Mp4 && window.AndroidHtml2Mp4.onCapabilitiesDetected) {
                        window.AndroidHtml2Mp4.onCapabilitiesDetected(JSON.stringify(inspectDocumentCapabilities()));
                      }
                    } catch (e) {}
                  };

                  function flushVirtualTimers(targetMs) {
                    let guard = 0;
                    while (guard < 64) {
                      let nextId = null;
                      let earliest = targetMs + 0.0001;
                      virtualTimerQueue.forEach(function(entry, id) {
                        if (entry.dueAt <= targetMs && entry.dueAt < earliest) {
                          earliest = entry.dueAt;
                          nextId = id;
                        }
                      });
                      if (nextId === null) break;
                      const entry = virtualTimerQueue.get(nextId);
                      if (!entry) break;
                      if (entry.intervalMs !== null) {
                        entry.dueAt = entry.dueAt + entry.intervalMs;
                        if (entry.dueAt <= targetMs) {
                          entry.dueAt = targetMs + entry.intervalMs;
                        }
                      } else {
                        virtualTimerQueue.delete(nextId);
                      }
                      try {
                        entry.fn.apply(window, entry.args);
                      } catch (e) {}
                      guard++;
                    }
                  }

                  function flushVirtualRaf(targetMs) {
                    if (virtualRafQueue.size === 0) return;
                    const batch = Array.from(virtualRafQueue.entries());
                    virtualRafQueue.clear();
                    for (let i = 0; i < batch.length; i++) {
                      const cb = batch[i][1];
                      try {
                        cb(targetMs);
                      } catch (e) {}
                    }
                  }

                  function syncAnimationsAt(targetMs) {
                    try {
                      let anims = [];
                      if (typeof document.getAnimations === 'function') {
                        try {
                          anims = document.getAnimations({ subtree: true });
                        } catch (err) {
                          anims = document.getAnimations();
                        }
                      }
                      if (anims && anims.length > 0) {
                        capsState.usesWaapi = true;
                        for (let i = 0; i < anims.length; i++) {
                          const anim = anims[i];
                          try {
                            if (!animStartOffsetMap.has(anim)) {
                              const cur = (typeof anim.currentTime === 'number' && isFinite(anim.currentTime))
                                ? anim.currentTime
                                : 0;
                              const offset = (targetMs > 0 && cur >= 0 && cur < targetMs * 0.5 && targetMs - cur > 50)
                                ? targetMs - cur
                                : 0;
                              animStartOffsetMap.set(anim, offset);
                            }
                            const startOffset = animStartOffsetMap.get(anim) || 0;
                            const localMs = Math.max(0, targetMs - startOffset);
                            if (anim.playState === 'running' || anim.playState === 'paused' || anim.playState === 'pending') {
                              anim.pause();
                              anim.currentTime = localMs;
                            }
                          } catch (e) {}
                        }
                      }
                    } catch (e) {}

                    try {
                      if (window.gsap && window.gsap.globalTimeline) {
                        capsState.usesGsap = true;
                        if (typeof window.gsap.globalTimeline.pause === 'function') {
                          window.gsap.globalTimeline.pause();
                        }
                        if (typeof window.gsap.globalTimeline.time === 'function') {
                          window.gsap.globalTimeline.time(targetMs / 1000, false);
                        }
                      }
                    } catch (e) {}

                    try {
                      if (window.OPENER && window.OPENER.tl) {
                        capsState.usesGsap = true;
                        if (typeof window.OPENER.tl.pause === 'function') {
                          window.OPENER.tl.pause(targetMs / 1000, false);
                        }
                      }
                    } catch (e) {}

                    try {
                      const videos = document.querySelectorAll('video');
                      if (videos.length > 0) capsState.hasMedia = true;
                      const targetSec = targetMs / 1000;
                      for (let i = 0; i < videos.length; i++) {
                        const v = videos[i];
                        try {
                          if (!v.paused) v.pause();
                          const maxSec = (v.duration && isFinite(v.duration) && v.duration > 0) ? v.duration : targetSec;
                          const desired = Math.max(0, Math.min(targetSec, maxSec));
                          if (Math.abs((v.currentTime || 0) - desired) > 0.008) {
                            v.currentTime = desired;
                          }
                        } catch (e) {}
                      }
                    } catch (e) {}
                  }

                  window.__html2mp4StepTo = function(targetTimeMs, frameSeq) {
                    virtualTimeMs = Math.max(0, Number(targetTimeMs) || 0);
                    flushVirtualTimers(virtualTimeMs);
                    flushVirtualRaf(virtualTimeMs);
                    syncAnimationsAt(virtualTimeMs);

                    // Force lightweight style/layout flush before waiting for compositor ack
                    try {
                      if (document.body) void document.body.offsetHeight;
                    } catch (e) {}

                    let signaled = false;
                    function notifyPrepared() {
                      if (signaled) return;
                      signaled = true;
                      if (window.AndroidHtml2Mp4 && window.AndroidHtml2Mp4.onFramePrepared) {
                        window.AndroidHtml2Mp4.onFramePrepared(
                          frameSeq,
                          JSON.stringify(inspectDocumentCapabilities())
                        );
                      }
                    }

                    realRaf(function() {
                      notifyPrepared();
                    });
                    realSetTimeout(notifyPrepared, 36);
                  };
                })();
            """.trimIndent()
        }
    }
}
