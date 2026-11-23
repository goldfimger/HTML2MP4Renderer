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
    val playbackStartedDeferred = CompletableDeferred<Unit>()
    val audioChunkQueue = ConcurrentLinkedQueue<AudioPcmChunk>()

    @Volatile
    var isCapturingAudio: Boolean = false

    @JavascriptInterface
    fun onPlaybackStarted() {
        if (!playbackStartedDeferred.isCompleted) {
            playbackStartedDeferred.complete(Unit)
        }
    }

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
        // Large bounded queue prevents dropping audio bursts during heavy SVG/Canvas frame draws
        while (audioChunkQueue.size >= 512) {
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

                if (window.OPENER) {
                  const opDur = Number(window.OPENER.DURATION || window.OPENER.duration || 0);
                  if (isFinite(opDur) && opDur > 0) {
                    maxMs = Math.max(maxMs, Math.round(opDur * 1000));
                  } else if (window.OPENER.tl && typeof window.OPENER.tl.duration === 'function') {
                    const tlDur = Number(window.OPENER.tl.duration());
                    if (isFinite(tlDur) && tlDur > 0) {
                      maxMs = Math.max(maxMs, Math.round(tlDur * 1000));
                    }
                  }
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
                  const m = metaRes.content.match(/(\d+)\s*[xX*×,]\s*(\d+)/);
                  if (m) {
                    detW = parseInt(m[1], 10);
                    detH = parseInt(m[2], 10);
                  }
                }

                if (!detW || !detH) {
                  const metaOrient = document.querySelector('meta[name="html2mp4-orientation"]');
                  if (metaOrient && metaOrient.content) {
                    const v = metaOrient.content.toLowerCase().trim();
                    if (v === 'portrait' || v === 'vertical' || v === 'vertikal') { detW = 720; detH = 1280; }
                    else if (v === 'landscape' || v === 'horizontal' || v === 'lanskap') { detW = 1280; detH = 720; }
                  }
                }

                if ((!detW || !detH) && window.OPENER) {
                  const ow = Number(window.OPENER.W || window.OPENER.width || 0);
                  const oh = Number(window.OPENER.H || window.OPENER.height || 0);
                  if (ow >= 120 && oh >= 120) {
                    detW = Math.round(ow);
                    detH = Math.round(oh);
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
                  let bestArea = 0;
                  document.querySelectorAll('canvas').forEach((cvs) => {
                    if (cvs.width >= 120 && cvs.height >= 120) {
                      const area = cvs.width * cvs.height;
                      if (area > bestArea) {
                        bestArea = area;
                        detW = cvs.width;
                        detH = cvs.height;
                      }
                    }
                  });
                }

                if (!detW || !detH) {
                  let bestSvgArea = 0;
                  document.querySelectorAll('svg').forEach((svg) => {
                    let sw = 0;
                    let sh = 0;
                    const vb = svg.getAttribute('viewBox');
                    if (vb) {
                      const parts = vb.trim().split(/[\s,]+/);
                      if (parts.length === 4) {
                        sw = parseFloat(parts[2]) || 0;
                        sh = parseFloat(parts[3]) || 0;
                      }
                    }
                    if (!sw || !sh) {
                      const aw = svg.getAttribute('width');
                      const ah = svg.getAttribute('height');
                      if (aw && ah && !aw.includes('%') && !ah.includes('%')) {
                        sw = parseFloat(aw) || 0;
                        sh = parseFloat(ah) || 0;
                      }
                    }
                    if (!sw || !sh) {
                      try {
                        const cs = getComputedStyle(svg);
                        sw = parseFloat(cs.width) || 0;
                        sh = parseFloat(cs.height) || 0;
                      } catch (e) {}
                    }
                    if (sw >= 120 && sh >= 120 && Math.abs(sw - sh) >= 16) {
                      const area = sw * sh;
                      if (area > bestSvgArea) {
                        bestSvgArea = area;
                        detW = Math.round(sw);
                        detH = Math.round(sh);
                      }
                    }
                  });
                }

                if (!detW || !detH) {
                  const stageEls = Array.from(
                    document.querySelectorAll('#stage, #world, #app, #scene, #canvas, .stage, main, body > div')
                  );
                  const candidates = stageEls.concat(
                    [document.body, document.documentElement].filter(Boolean)
                  );

                  for (const el of candidates) {
                    if (!el) continue;
                    try {
                      const cs = getComputedStyle(el);
                      if (cs.aspectRatio && cs.aspectRatio.includes('/')) {
                        const parts = cs.aspectRatio.split('/').map(Number);
                        if (parts.length === 2 && parts[0] > 0 && parts[1] > 0 && Math.abs(parts[0] - parts[1]) > 0.05) {
                          if (parts[1] > parts[0]) { detW = 1080; detH = Math.round((1080 * parts[1]) / parts[0]); }
                          else { detH = 720; detW = Math.round((720 * parts[0]) / parts[1]); }
                          break;
                        }
                      }
                      const pw = parseFloat(cs.width);
                      const ph = parseFloat(cs.height);
                      if (pw >= 120 && ph >= 120 && Math.abs(pw - ph) >= 16 && !(pw === 1000 && ph === 1000)) {
                        detW = Math.round(pw);
                        detH = Math.round(ph);
                        break;
                      }
                      const rect = el.getBoundingClientRect();
                      if (rect.width >= 120 && rect.height >= 120 && Math.abs(rect.width - rect.height) >= 16) {
                        if (!(Math.round(rect.width) === 1000 && Math.round(rect.height) === 1000)) {
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
              window.__html2mp4Active = false;

              const TARGET_SR = $sampleRate;
              const TARGET_CH = $channels;
              const CAPTURE_AUDIO = $captureAudio;
              const pendingMediaPlays = new Set();

              // Gate early HTMLMediaElement.play() calls so audio/video waits cleanly at t=0
              // until MediaCodec + audio bridge are armed and startWallMs begins.
              const proto = window.HTMLMediaElement && window.HTMLMediaElement.prototype;
              const origMediaPlay = proto ? proto.play : null;
              if (proto && origMediaPlay) {
                proto.play = function() {
                  if (window.__html2mp4Active) {
                    return origMediaPlay.apply(this, arguments);
                  }
                  pendingMediaPlays.add(this);
                  try {
                    this.pause();
                    if (isFinite(this.currentTime) && this.currentTime > 0) {
                      this.currentTime = 0;
                    }
                  } catch (e) {}
                  try {
                    return Promise.reject(new DOMException('Deferred until capture start', 'NotAllowedError'));
                  } catch (e) {
                    return Promise.reject(new Error('Deferred until capture start'));
                  }
                };
              }

              function arrayBufferToBase64(buffer) {
                const bytes = new Uint8Array(buffer);
                let binary = '';
                const chunk = 0x8000;
                for (let i = 0; i < bytes.length; i += chunk) {
                  binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
                }
                return btoa(binary);
              }

              const allContexts = [];
              if (CAPTURE_AUDIO && (window.AudioContext || window.webkitAudioContext)) {
                const NativeCtx = window.AudioContext || window.webkitAudioContext;
                const hookedContexts = new Set();
                const hookedElements = new WeakSet();

                function attachTap(ctx) {
                  if (!ctx || hookedContexts.has(ctx)) return;
                  hookedContexts.add(ctx);
                  allContexts.push(ctx);
                  try {
                    const tapGain = ctx.createGain();
                    // 4096 buffer gives Chromium main thread 2x headroom (~85ms) preventing SVG/DOM underrun crackle
                    const processor = ctx.createScriptProcessor(4096, TARGET_CH, TARGET_CH);
                    const silentOut = ctx.createGain();
                    silentOut.gain.value = 0.0;
                    silentOut.__isHtml2Mp4Silent = true;
                    const startWall = performance.now();

                    processor.onaudioprocess = function(ev) {
                      if (!window.__html2mp4Active) return;
                      const srcRate = ctx.sampleRate || TARGET_SR;
                      const inLen = ev.inputBuffer.length;
                      const chCount = Math.min(TARGET_CH, ev.inputBuffer.numberOfChannels);
                      let int16;

                      if (srcRate === TARGET_SR) {
                        int16 = new Int16Array(inLen * TARGET_CH);
                        for (let c = 0; c < TARGET_CH; c++) {
                          const srcCh = c < chCount ? c : 0;
                          const chData = ev.inputBuffer.getChannelData(srcCh);
                          for (let i = 0; i < inLen; i++) {
                            const s = chData[i];
                            const clamped = s > 1 ? 1 : (s < -1 ? -1 : s);
                            int16[i * TARGET_CH + c] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7FFF;
                          }
                        }
                      } else {
                        const ratio = srcRate / TARGET_SR;
                        const outLen = Math.max(1, Math.round(inLen / ratio));
                        int16 = new Int16Array(outLen * TARGET_CH);
                        for (let c = 0; c < TARGET_CH; c++) {
                          const srcCh = c < chCount ? c : 0;
                          const chData = ev.inputBuffer.getChannelData(srcCh);
                          for (let i = 0; i < outLen; i++) {
                            const srcPos = Math.min(inLen - 1, i * ratio);
                            const idx0 = Math.floor(srcPos);
                            const idx1 = Math.min(inLen - 1, idx0 + 1);
                            const frac = srcPos - idx0;
                            const s = chData[idx0] * (1 - frac) + chData[idx1] * frac;
                            const clamped = s > 1 ? 1 : (s < -1 ? -1 : s);
                            int16[i * TARGET_CH + c] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7FFF;
                          }
                        }
                      }

                      const ptsMs = Math.max(0, Math.round(performance.now() - startWall));
                      if (window.AndroidHtml2Mp4) {
                        window.AndroidHtml2Mp4.onAudioPcmChunk(
                          arrayBufferToBase64(int16.buffer),
                          TARGET_SR,
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
                  attachTap(sharedCtx);
                  if (sharedCtx.state === 'suspended') {
                    sharedCtx.resume().catch(() => {});
                  }
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
                if (proto && origMediaPlay) {
                  proto.play = origMediaPlay;
                }
                window.__html2mp4Active = true;

                if (window.__html2mp4HookMediaElements) window.__html2mp4HookMediaElements();
                allContexts.forEach((c) => {
                  try {
                    if (c.state === 'suspended') c.resume().catch(() => {});
                  } catch (e) {}
                });

                const allMedia = new Set(document.querySelectorAll('video, audio'));
                pendingMediaPlays.forEach((el) => allMedia.add(el));
                allMedia.forEach((el) => {
                  try {
                    el.pause();
                    el.currentTime = 0;
                    el.muted = !CAPTURE_AUDIO;
                  } catch (e) {}
                });

                try {
                  if (document.getAnimations) {
                    document.getAnimations().forEach((anim) => {
                      try {
                        anim.currentTime = 0;
                        anim.play();
                      } catch (e) {}
                    });
                  }
                } catch (e) {}

                try {
                  if (window.OPENER && window.OPENER.tl) {
                    if (typeof window.OPENER.tl.pause === 'function') {
                      window.OPENER.tl.pause(0, false);
                    }
                    if (typeof window.OPENER.tl.play === 'function') {
                      window.OPENER.tl.play(0);
                    }
                  }
                } catch (e) {}

                try {
                  window.dispatchEvent(new Event('pointerdown'));
                  if (document.body) {
                    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
                    document.body.click();
                  }
                } catch (e) {}

                allMedia.forEach((el) => {
                  try {
                    if (el.paused) {
                      el.currentTime = 0;
                      el.play().catch(() => {});
                    }
                  } catch (e) {}
                });
              };

              window.__html2mp4PrepareAndStart = function() {
                const fontsPromise = (document.fonts && document.fonts.ready)
                  ? document.fonts.ready.catch(() => {})
                  : Promise.resolve();
                fontsPromise.then(function() {
                  // Yield 1 micro-tick so any listener registered in document.fonts.ready runs first
                  setTimeout(function() {
                    window.__html2mp4StartPlayback();
                    requestAnimationFrame(function() {
                      if (window.AndroidHtml2Mp4 && window.AndroidHtml2Mp4.onPlaybackStarted) {
                        window.AndroidHtml2Mp4.onPlaybackStarted();
                      }
                    });
                  }, 16);
                });
              };
            })();
        """.trimIndent()
    }
}
