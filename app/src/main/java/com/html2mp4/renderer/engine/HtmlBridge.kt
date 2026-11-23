package com.html2mp4.renderer.engine

import android.util.Base64
import android.webkit.JavascriptInterface
import com.html2mp4.renderer.model.VideoOrientation
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentLinkedQueue

data class AudioPcmChunk(
    val pcmBytes: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val ptsMs: Long
)

data class HtmlProbeResult(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val orientation: VideoOrientation
)

/**
 * Injected as window.AndroidHtml2Mp4 inside the offscreen WebView.
 * Receives auto-detected durations, natural HTML resolution/orientation, and tapped PCM audio frames.
 */
class HtmlBridge(
    private val targetSampleRate: Int,
    private val targetChannels: Int
) {
    val probeDeferred = CompletableDeferred<HtmlProbeResult>()
    val audioChunkQueue = ConcurrentLinkedQueue<AudioPcmChunk>()

    @Volatile
    var isCapturingAudio: Boolean = false

    @JavascriptInterface
    fun onMetadataDetected(durationMs: Long, width: Int, height: Int) {
        if (!probeDeferred.isCompleted) {
            val sanitizedDur = durationMs.coerceIn(1_000L, 6L * 3600L * 1000L)
            val safeW = width.coerceIn(320, 1920)
            val safeH = height.coerceIn(240, 1920)
            val orientation = if (safeH > safeW) VideoOrientation.PORTRAIT else VideoOrientation.LANDSCAPE
            probeDeferred.complete(
                HtmlProbeResult(
                    durationMs = sanitizedDur,
                    width = safeW,
                    height = safeH,
                    orientation = orientation
                )
            )
        }
    }

    @JavascriptInterface
    fun onDurationDetected(durationMs: Long) {
        onMetadataDetected(durationMs, 1280, 720)
    }

    @JavascriptInterface
    fun onAudioPcmChunk(base64Int16Pcm: String, sampleRate: Int, channels: Int, ptsMs: Long) {
        if (!isCapturingAudio || base64Int16Pcm.isEmpty()) return
        // Bound queue size strictly to avoid heap growth on Exynos 850
        if (audioChunkQueue.size > 160) {
            audioChunkQueue.poll()
        }
        runCatching {
            val decoded = Base64.decode(base64Int16Pcm, Base64.NO_WRAP)
            audioChunkQueue.add(
                AudioPcmChunk(
                    pcmBytes = decoded,
                    sampleRate = sampleRate.takeIf { it > 0 } ?: targetSampleRate,
                    channels = channels.takeIf { it in 1..2 } ?: targetChannels,
                    ptsMs = ptsMs.coerceAtLeast(0L)
                )
            )
        }
    }

    companion object {
        /**
         * Document-start probe script used during HTML import.
         * Silences AudioContext immediately and exposes window.__html2mp4ProbeNow() to detect
         * duration, resolution, and portrait/landscape orientation once DOM & media finish loading.
         */
        fun buildProbeOnlyScript(): String = """
            (function() {
              try {
                if (!window.__html2mp4AudioSilenced && (window.AudioContext || window.webkitAudioContext)) {
                  window.__html2mp4AudioSilenced = true;
                  const OrigCtx = window.AudioContext || window.webkitAudioContext;
                  const SilentCtx = function() {
                    const ctx = new OrigCtx();
                    try { ctx.suspend(); } catch (e) {}
                    return ctx;
                  };
                  SilentCtx.prototype = OrigCtx.prototype;
                  window.AudioContext = SilentCtx;
                  if (window.webkitAudioContext) window.webkitAudioContext = SilentCtx;
                }
              } catch (e) {}

              window.__html2mp4ProbeNow = function() {
                document.querySelectorAll('video, audio').forEach((el) => {
                  try {
                    el.muted = true;
                    el.volume = 0;
                    el.pause();
                  } catch (e) {}
                });

                let maxMs = 0;
                const metaDur = document.querySelector('meta[name="html2mp4-duration"]');
                if (metaDur && metaDur.content) {
                  const sec = parseFloat(metaDur.content);
                  if (!isNaN(sec) && sec > 0) maxMs = Math.max(maxMs, Math.round(sec * 1000));
                }

                document.querySelectorAll('video, audio').forEach((el) => {
                  if (el.duration && isFinite(el.duration) && el.duration > 0) {
                    maxMs = Math.max(maxMs, Math.round(el.duration * 1000));
                  }
                });

                if (document.getAnimations) {
                  document.getAnimations().forEach((anim) => {
                    try {
                      const timing = anim.effect && anim.effect.getComputedTiming ? anim.effect.getComputedTiming() : null;
                      if (timing && isFinite(timing.endTime) && timing.endTime > 0) {
                        maxMs = Math.max(maxMs, Math.round(timing.endTime));
                      }
                    } catch (e) {}
                  });
                }

                // Auto-detect intended resolution & orientation (Portrait vs Landscape)
                let detW = 0;
                let detH = 0;

                const metaRes = document.querySelector('meta[name="html2mp4-resolution"]');
                if (metaRes && metaRes.content) {
                  const m = metaRes.content.match(/(\d+)\s*[xX*]\s*(\d+)/);
                  if (m) {
                    detW = parseInt(m[1], 10);
                    detH = parseInt(m[2], 10);
                  }
                }

                if (!detW || !detH) {
                  const metaOrient = document.querySelector('meta[name="html2mp4-orientation"]');
                  if (metaOrient && metaOrient.content) {
                    const v = metaOrient.content.toLowerCase().trim();
                    if (v === 'portrait' || v === 'vertical') { detW = 720; detH = 1280; }
                    else if (v === 'landscape' || v === 'horizontal') { detW = 1280; detH = 720; }
                  }
                }

                if (!detW || !detH) {
                  const vp = document.querySelector('meta[name="viewport"]');
                  if (vp && vp.content) {
                    const mw = vp.content.match(/width\s*=\s*(\d+)/i);
                    const mh = vp.content.match(/height\s*=\s*(\d+)/i);
                    if (mw && mh) {
                      detW = parseInt(mw[1], 10);
                      detH = parseInt(mh[1], 10);
                    }
                  }
                }

                if (!detW || !detH) {
                  const vid = document.querySelector('video');
                  if (vid && vid.videoWidth > 0 && vid.videoHeight > 0) {
                    detW = vid.videoWidth;
                    detH = vid.videoHeight;
                  }
                }

                if (!detW || !detH) {
                  const cvs = document.querySelector('canvas');
                  if (cvs && cvs.width > 0 && cvs.height > 0) {
                    const cr = cvs.getBoundingClientRect();
                    if (cr.width > 0 && cr.height > 0 && Math.abs(cr.width - cr.height) > 20) {
                      detW = Math.round(cr.width);
                      detH = Math.round(cr.height);
                    } else {
                      detW = cvs.width;
                      detH = cvs.height;
                    }
                  }
                }

                if (!detW || !detH) {
                  const svg = document.querySelector('svg[viewBox]');
                  if (svg) {
                    const vb = svg.getAttribute('viewBox').trim().split(/[\s,]+/);
                    if (vb.length === 4) {
                      const sw = parseFloat(vb[2]);
                      const sh = parseFloat(vb[3]);
                      if (sw > 0 && sh > 0) {
                        detW = Math.round(sw);
                        detH = Math.round(sh);
                      }
                    }
                  }
                }

                if (!detW || !detH) {
                  const candidates = [
                    document.body && document.body.firstElementChild,
                    document.body,
                    document.documentElement
                  ].filter(Boolean);

                  for (const el of candidates) {
                    try {
                      const cs = getComputedStyle(el);
                      const pw = parseFloat(cs.width);
                      const ph = parseFloat(cs.height);
                      if (cs.aspectRatio && cs.aspectRatio.includes('/')) {
                        const parts = cs.aspectRatio.split('/').map(Number);
                        if (parts.length === 2 && parts[0] > 0 && parts[1] > 0) {
                          if (parts[1] > parts[0]) { detW = 720; detH = 1280; }
                          else { detW = 1280; detH = 720; }
                          break;
                        }
                      }
                      const rect = el.getBoundingClientRect();
                      if (rect.width >= 200 && rect.height >= 200) {
                        if (rect.height > rect.width * 1.15) {
                          detW = Math.round(rect.width);
                          detH = Math.round(rect.height);
                          break;
                        } else if (rect.width > rect.height * 1.15 && pw !== 1000) {
                          detW = Math.round(rect.width);
                          detH = Math.round(rect.height);
                          break;
                        }
                      }
                    } catch (e) {}
                  }
                }

                if (!detW || !detH || Math.abs(detW - detH) < 16) {
                  detW = 1280;
                  detH = 720;
                }

                const resolvedDur = maxMs > 0 ? maxMs : 10000;
                if (window.AndroidHtml2Mp4) {
                  window.AndroidHtml2Mp4.onMetadataDetected(resolvedDur, detW, detH);
                }
                return { durationMs: resolvedDur, width: detW, height: detH };
              };
            })();
        """.trimIndent()

        /**
         * Injected script during rendering:
         * Routes AudioContext & HTMLMediaElement into AndroidHtml2Mp4 PCM tap while silencing
         * speaker output via a zero-gain node so no audio leaks during background rendering.
         */
        fun buildBootstrapScript(sampleRate: Int, channels: Int, captureAudio: Boolean): String = """
            (function() {
              if (window.__html2mp4Bootstrapped) return;
              window.__html2mp4Bootstrapped = true;

              const TARGET_SR = $sampleRate;
              const TARGET_CH = $channels;
              const CAPTURE_AUDIO = $captureAudio;

              function arrayBufferToBase64(buffer) {
                const bytes = new Uint8Array(buffer);
                let binary = '';
                const chunk = 0x8000;
                for (let i = 0; i < bytes.length; i += chunk) {
                  binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
                }
                return btoa(binary);
              }

              if (CAPTURE_AUDIO && (window.AudioContext || window.webkitAudioContext)) {
                const NativeCtx = window.AudioContext || window.webkitAudioContext;
                const hookedContexts = new Set();
                const hookedElements = new WeakSet();

                function attachTap(ctx) {
                  if (!ctx || hookedContexts.has(ctx)) return;
                  hookedContexts.add(ctx);
                  try {
                    const tapGain = ctx.createGain();
                    const processor = ctx.createScriptProcessor(2048, TARGET_CH, TARGET_CH);
                    const silentOut = ctx.createGain();
                    silentOut.gain.value = 0.0;
                    silentOut.__isHtml2Mp4Silent = true;
                    const startWall = performance.now();

                    processor.onaudioprocess = function(ev) {
                      if (!window.__html2mp4Active) return;
                      const outFrames = ev.inputBuffer.length;
                      const chCount = Math.min(TARGET_CH, ev.inputBuffer.numberOfChannels);
                      const int16 = new Int16Array(outFrames * TARGET_CH);
                      for (let i = 0; i < outFrames; i++) {
                        for (let c = 0; c < TARGET_CH; c++) {
                          const srcCh = c < chCount ? c : 0;
                          const sample = Math.max(-1, Math.min(1, ev.inputBuffer.getChannelData(srcCh)[i]));
                          int16[i * TARGET_CH + c] = sample < 0 ? sample * 0x8000 : sample * 0x7FFF;
                        }
                      }
                      const ptsMs = Math.max(0, Math.round(performance.now() - startWall));
                      if (window.AndroidHtml2Mp4) {
                        window.AndroidHtml2Mp4.onAudioPcmChunk(
                          arrayBufferToBase64(int16.buffer),
                          ctx.sampleRate || TARGET_SR,
                          TARGET_CH,
                          ptsMs
                        );
                      }
                    };

                    tapGain.__isHtml2Mp4Tap = true;
                    processor.__isHtml2Mp4Tap = true;
                    tapGain.connect(processor);
                    processor.connect(silentOut);
                    silentOut.connect(ctx.destination);
                    ctx.__html2mp4Tap = tapGain;
                  } catch (e) {}
                }

                const origConnect = AudioNode.prototype.connect;
                AudioNode.prototype.connect = function(dest, outIdx, inIdx) {
                  try {
                    if (dest && this.context && dest === this.context.destination && !this.__isHtml2Mp4Silent && !this.__isHtml2Mp4Tap) {
                      attachTap(this.context);
                      if (this.context.__html2mp4Tap && this !== this.context.__html2mp4Tap) {
                        return origConnect.call(this, this.context.__html2mp4Tap);
                      }
                    }
                  } catch (e) {}
                  return origConnect.apply(this, arguments);
                };

                window.__html2mp4HookMediaElements = function() {
                  const mediaEls = document.querySelectorAll('video, audio');
                  if (!mediaEls.length) return;
                  const sharedCtx = window.__html2mp4SharedCtx || new NativeCtx({ sampleRate: TARGET_SR });
                  window.__html2mp4SharedCtx = sharedCtx;
                  if (sharedCtx.state === 'suspended') {
                    sharedCtx.resume().catch(() => {});
                  }
                  attachTap(sharedCtx);
                  mediaEls.forEach((el) => {
                    if (hookedElements.has(el)) return;
                    hookedElements.add(el);
                    try {
                      const src = sharedCtx.createMediaElementSource(el);
                      src.connect(sharedCtx.__html2mp4Tap || sharedCtx.destination);
                    } catch (e) {}
                  });
                };
              }

              window.__html2mp4StartPlayback = function() {
                window.__html2mp4Active = true;
                if (window.__html2mp4HookMediaElements) window.__html2mp4HookMediaElements();
                document.querySelectorAll('video, audio').forEach((el) => {
                  try {
                    el.currentTime = 0;
                    el.muted = !CAPTURE_AUDIO;
                    el.play().catch(() => {});
                  } catch (e) {}
                });
              };
            })();
        """.trimIndent()
    }
}
