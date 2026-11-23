package com.html2mp4.renderer.engine

import android.util.Base64
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentLinkedQueue

data class AudioPcmChunk(
    val pcmBytes: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val ptsMs: Long
)

/**
 * Injected as window.AndroidHtml2Mp4 inside the offscreen WebView.
 * Receives auto-detected durations and tapped PCM audio frames from Web Audio / HTMLMediaElement.
 */
class HtmlBridge(
    private val targetSampleRate: Int,
    private val targetChannels: Int
) {
    val durationDeferred = CompletableDeferred<Long>()
    val audioChunkQueue = ConcurrentLinkedQueue<AudioPcmChunk>()

    @Volatile
    var isCapturingAudio: Boolean = false

    @JavascriptInterface
    fun onDurationDetected(durationMs: Long) {
        if (!durationDeferred.isCompleted) {
            val sanitized = durationMs.coerceIn(1_000L, 6L * 3600L * 1000L)
            durationDeferred.complete(sanitized)
        }
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
         * Injected script that:
         * 1. Hooks AudioContext & HTMLMediaElement to tap all audio outputs into Int16 PCM chunks.
         * 2. Computes total playback duration from <video>, <audio>, Web Animations API, or meta tags.
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

                    tapGain.connect(processor);
                    processor.connect(ctx.destination);
                    ctx.__html2mp4Tap = tapGain;
                  } catch (e) {}
                }

                const origConnect = AudioNode.prototype.connect;
                AudioNode.prototype.connect = function(dest, outIdx, inIdx) {
                  const res = origConnect.apply(this, arguments);
                  try {
                    if (dest && this.context && dest === this.context.destination && !this.__isHtml2Mp4Tap) {
                      attachTap(this.context);
                      if (this.context.__html2mp4Tap && this !== this.context.__html2mp4Tap) {
                        origConnect.call(this, this.context.__html2mp4Tap);
                      }
                    }
                  } catch (e) {}
                  return res;
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
                      src.connect(sharedCtx.destination);
                    } catch (e) {}
                  });
                };
              }

              window.__html2mp4DetectDuration = function() {
                let maxMs = 0;
                const meta = document.querySelector('meta[name="html2mp4-duration"]');
                if (meta && meta.content) {
                  const sec = parseFloat(meta.content);
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

                const resolved = maxMs > 0 ? maxMs : 10000;
                if (window.AndroidHtml2Mp4) {
                  window.AndroidHtml2Mp4.onDurationDetected(resolved);
                }
                return resolved;
              };

              window.__html2mp4StartPlayback = function() {
                window.__html2mp4Active = true;
                if (window.__html2mp4HookMediaElements) window.__html2mp4HookMediaElements();
                document.querySelectorAll('video, audio').forEach((el) => {
                  try {
                    el.currentTime = 0;
                    el.muted = false;
                    el.play().catch(() => {});
                  } catch (e) {}
                });
              };
            })();
        """.trimIndent()
    }
}
