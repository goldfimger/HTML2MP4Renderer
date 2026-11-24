package com.html2mp4.renderer.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.html2mp4.renderer.model.AudioCodecOption
import com.html2mp4.renderer.model.RenderConfig
import com.html2mp4.renderer.model.VideoCodecOption
import com.html2mp4.renderer.util.ChipsetOptimizer
import com.html2mp4.renderer.util.DeviceProfile
import java.io.File
import java.nio.ByteBuffer

/**
 * Hardware-accelerated MediaCodec + MediaMuxer pipeline auto-tuned across
 * Exynos MFC, Qualcomm Snapdragon Venus, MediaTek VENC, Google Tensor, and ChromeOS x86_64.
 * Enforces strictly monotonic PTS and safe encoder fallback so MediaMuxer never throws.
 */
class MediaCodecMuxerPipeline(
    private val config: RenderConfig,
    private val deviceProfile: DeviceProfile,
    private val tempOutputFile: File
) {
    private val muxerLock = Any()

    private lateinit var videoCodec: MediaCodec
    lateinit var inputSurface: Surface
        private set

    private var audioCodec: MediaCodec? = null
    private var muxer: MediaMuxer? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false

    private val videoBufferInfo = MediaCodec.BufferInfo()
    private val audioBufferInfo = MediaCodec.BufferInfo()

    private data class PendingMuxSample(
        val isVideo: Boolean,
        val bytes: ByteArray,
        val info: MediaCodec.BufferInfo
    )

    var encodedWidth: Int = config.resolvedWidth
        private set
    var encodedHeight: Int = config.resolvedHeight
        private set
    var activeVideoEncoderName: String = deviceProfile.activeEncoderLabel
        private set
    var activeAudioEncoderName: String = if (!config.isAudioMuted) config.audioCodec.label else "Muted"
        private set

    private val expectAudioTrack = !config.isAudioMuted
    private val released = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pendingSamples = ArrayList<PendingMuxSample>(256)
    private var totalAudioSamplesWritten = 0L
    private var firstRawVideoPtsUs = -1L
    private var lastVideoPtsUs = -1L
    private var lastAudioPtsUs = -1L

    private var firstAudioSourcePtsMs = -1L
    private var lastAudioInputPtsUs = -1L
    var audioGapSilencesInserted: Int = 0
        private set
    var audioSilencePaddedMs: Long = 0L
        private set
    var maxAbsAvDriftMs: Long = 0L
        private set
    var finalAvDriftMs: Long = 0L
        private set

    fun audioWrittenDurationMs(): Long =
        (totalAudioSamplesWritten * 1000L) / config.audioSampleRate.hz

    fun prepare() {
        val width = config.resolvedWidth
        val height = config.resolvedHeight

        videoCodec = configureVideoEncoderWithFallback(width, height)
        activeVideoEncoderName = runCatching { videoCodec.name }.getOrDefault(deviceProfile.activeEncoderLabel)
        inputSurface = videoCodec.createInputSurface()
        videoCodec.start()

        if (expectAudioTrack) {
            val resolvedAudioMime = resolveSupportedAudioMime(config.audioCodec)
            val audioFormat = MediaFormat.createAudioFormat(
                resolvedAudioMime,
                config.audioSampleRate.hz,
                config.audioChannels.count
            ).apply {
                if (resolvedAudioMime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                    setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC
                    )
                }
                setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrate.kbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }

            audioCodec = MediaCodec.createEncoderByType(resolvedAudioMime).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                activeAudioEncoderName = runCatching { name }.getOrDefault(resolvedAudioMime)
                start()
            }
        } else {
            activeAudioEncoderName = "Muted"
        }

        muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private fun configureVideoEncoderWithFallback(width: Int, height: Int): MediaCodec {
        val mime = config.videoCodec.mimeType
        val preferredName = if (config.videoCodec == VideoCodecOption.H265) {
            deviceProfile.preferredHevcEncoder
        } else {
            deviceProfile.preferredAvcEncoder
        }
        val safeAlignW = maxOf(deviceProfile.widthAlignment, 2)
        val safeAlignH = maxOf(deviceProfile.heightAlignment, 2)
        val alignedW = ChipsetOptimizer.alignDimension(width, safeAlignW)
        val alignedH = ChipsetOptimizer.alignDimension(height, safeAlignH)

        if (!preferredName.isNullOrBlank()) {
            val namedAttempt = runCatching {
                val fmt = createVideoFormat(mime, alignedW, alignedH)
                MediaCodec.createByCodecName(preferredName).apply {
                    configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
            }
            if (namedAttempt.isSuccess) {
                encodedWidth = alignedW
                encodedHeight = alignedH
                return namedAttempt.getOrThrow()
            }
        }

        return try {
            val fmt = createVideoFormat(mime, alignedW, alignedH)
            MediaCodec.createEncoderByType(mime).apply {
                configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }.also {
                encodedWidth = alignedW
                encodedHeight = alignedH
            }
        } catch (_: Throwable) {
            // Safe fallback to H.264/AVC with 16-px macroblock alignment if custom size/HEVC is rejected
            val fallbackMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val mbW = ChipsetOptimizer.alignDimension(width, 16).coerceAtLeast(320)
            val mbH = ChipsetOptimizer.alignDimension(height, 16).coerceAtLeast(240)
            val fmt = createVideoFormat(fallbackMime, mbW, mbH)
            MediaCodec.createEncoderByType(fallbackMime).apply {
                configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }.also {
                encodedWidth = mbW
                encodedHeight = mbH
            }
        }
    }

    private fun createVideoFormat(mime: String, width: Int, height: Int): MediaFormat {
        return MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, config.resolvedVideoBitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            runCatching {
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                )
            }
        }
    }

    private fun resolveSupportedAudioMime(requested: AudioCodecOption): String {
        if (requested == AudioCodecOption.AAC) return MediaFormat.MIMETYPE_AUDIO_AAC
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val hasMp3Encoder = list.codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_AUDIO_MPEG, true) }
        }
        return if (hasMp3Encoder) MediaFormat.MIMETYPE_AUDIO_MPEG else MediaFormat.MIMETYPE_AUDIO_AAC
    }

    fun drainVideo(endOfStream: Boolean) {
        if (endOfStream) {
            runCatching { videoCodec.signalEndOfInputStream() }
        }

        var emptyLoops = 0
        while (emptyLoops < 120) {
            val status = videoCodec.dequeueOutputBuffer(videoBufferInfo, if (endOfStream) 10_000L else 0L)
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    emptyLoops++
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxerLock) {
                        if (videoTrackIndex == -1) {
                            videoTrackIndex = muxer!!.addTrack(videoCodec.outputFormat)
                            tryStartMuxerLocked()
                        }
                    }
                }
                status >= 0 -> {
                    emptyLoops = 0
                    val encodedData = videoCodec.getOutputBuffer(status)
                    if (encodedData != null && (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        if (videoBufferInfo.size > 0) {
                            val rawPtsUs = videoBufferInfo.presentationTimeUs
                            if (firstRawVideoPtsUs < 0L) {
                                firstRawVideoPtsUs = rawPtsUs
                            }
                            var normalizedPtsUs = (rawPtsUs - firstRawVideoPtsUs).coerceAtLeast(0L)
                            val minStepUs = (1_000_000L / config.frameRate.fps).coerceAtLeast(1_000L)
                            if (normalizedPtsUs <= lastVideoPtsUs) {
                                normalizedPtsUs = lastVideoPtsUs + minStepUs
                            } else if (lastVideoPtsUs >= 0L && (normalizedPtsUs - lastVideoPtsUs) > 2_000_000L) {
                                normalizedPtsUs = lastVideoPtsUs + minStepUs
                            }
                            lastVideoPtsUs = normalizedPtsUs

                            encodedData.position(videoBufferInfo.offset)
                            encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)

                            synchronized(muxerLock) {
                                if (muxerStarted) {
                                    videoBufferInfo.presentationTimeUs = normalizedPtsUs
                                    muxer!!.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo)
                                } else if (pendingSamples.size < 1024) {
                                    val copy = ByteArray(videoBufferInfo.size)
                                    encodedData.get(copy)
                                    val infoCopy = MediaCodec.BufferInfo().apply {
                                        set(0, copy.size, normalizedPtsUs, videoBufferInfo.flags)
                                    }
                                    pendingSamples.add(PendingMuxSample(isVideo = true, bytes = copy, info = infoCopy))
                                }
                            }
                        }
                    }
                    videoCodec.releaseOutputBuffer(status, false)
                    if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    /**
     * Feeds an incoming AudioPcmChunk preserving original source timestamp (ptsMs),
     * validating/resampling sampleRate & channels, compensating >45ms lost-chunk gaps with
     * bounded silence, and guaranteeing monotonic encoder PTS.
     */
    fun feedAudioChunk(chunk: AudioPcmChunk) {
        val normalized = adaptPcmToTarget(
            srcBytes = chunk.pcmBytes,
            srcRate = chunk.sampleRate,
            srcChannels = chunk.channels
        )
        if (normalized.isEmpty()) return

        if (firstAudioSourcePtsMs < 0L) {
            firstAudioSourcePtsMs = chunk.ptsMs
        }
        val relSourceMs = (chunk.ptsMs - firstAudioSourcePtsMs).coerceAtLeast(0L)
        val writtenMs = audioWrittenDurationMs()

        // Compensate genuine gaps > 45ms so subsequent audio stays anchored to original ptsMs
        val gapMs = AudioPcmMath.computeGapSilenceMs(relSourceMs, writtenMs)
        if (gapMs > 0L) {
            val gapFrames = ((gapMs * config.audioSampleRate.hz) / 1000L).toInt()
            if (gapFrames > 0) {
                val silenceBytes = ByteArray(gapFrames * 2 * config.audioChannels.count)
                feedAudioPcmInternal(silenceBytes, explicitPtsUs = null)
                audioGapSilencesInserted++
                audioSilencePaddedMs += gapMs
            }
        }

        val sampleTimelineUs = (totalAudioSamplesWritten * 1_000_000L) / config.audioSampleRate.hz
        val sourcePtsUs = maxOf(sampleTimelineUs, relSourceMs * 1_000L)
        feedAudioPcmInternal(normalized, explicitPtsUs = sourcePtsUs)
    }

    fun feedSilencePadding(silenceBytes: ByteArray) {
        if (silenceBytes.isEmpty()) return
        val bytesPerSampleFrame = 2 * config.audioChannels.count
        val addedFrames = silenceBytes.size / bytesPerSampleFrame
        if (addedFrames > 0) {
            audioSilencePaddedMs += (addedFrames * 1000L) / config.audioSampleRate.hz
        }
        feedAudioPcmInternal(silenceBytes, explicitPtsUs = null)
    }

    fun sampleAvDrift(videoTimelineMs: Long) {
        val audioMs = if (lastAudioPtsUs >= 0L) (lastAudioPtsUs / 1_000L) else audioWrittenDurationMs()
        val driftMs = audioMs - videoTimelineMs
        finalAvDriftMs = driftMs
        val absDrift = kotlin.math.abs(driftMs)
        if (absDrift > maxAbsAvDriftMs) {
            maxAbsAvDriftMs = absDrift
        }
    }

    fun feedAudioPcm(pcmBytes: ByteArray) {
        feedAudioPcmInternal(pcmBytes, explicitPtsUs = null)
    }

    private fun feedAudioPcmInternal(pcmBytes: ByteArray, explicitPtsUs: Long?) {
        val codec = audioCodec ?: return
        val bytesPerSampleFrame = 2 * config.audioChannels.count
        var offset = 0
        var retries = 0
        while (offset + bytesPerSampleFrame <= pcmBytes.size && retries < 24) {
            val inIdx = codec.dequeueInputBuffer(4_000L)
            if (inIdx < 0) {
                drainAudio(endOfStream = false)
                retries++
                continue
            }
            retries = 0
            val buf = codec.getInputBuffer(inIdx) ?: break
            buf.clear()
            val rawCopy = minOf(buf.remaining(), pcmBytes.size - offset)
            val toCopy = (rawCopy / bytesPerSampleFrame) * bytesPerSampleFrame
            if (toCopy <= 0) break
            buf.put(pcmBytes, offset, toCopy)

            val framesInChunk = toCopy / bytesPerSampleFrame
            val chunkStartSampleUs = (totalAudioSamplesWritten * 1_000_000L) / config.audioSampleRate.hz
            val offsetDeltaUs = ((offset / bytesPerSampleFrame).toLong() * 1_000_000L) / config.audioSampleRate.hz
            var ptsUs = explicitPtsUs?.let { it + offsetDeltaUs } ?: chunkStartSampleUs
            if (ptsUs <= lastAudioInputPtsUs) {
                ptsUs = lastAudioInputPtsUs + 1L
            }
            lastAudioInputPtsUs = ptsUs
            totalAudioSamplesWritten += framesInChunk

            codec.queueInputBuffer(inIdx, 0, toCopy, ptsUs, 0)
            offset += toCopy
            drainAudio(endOfStream = false)
        }
    }

    private fun adaptPcmToTarget(srcBytes: ByteArray, srcRate: Int, srcChannels: Int): ByteArray =
        AudioPcmMath.adaptPcmToTarget(
            srcBytes = srcBytes,
            srcRate = srcRate,
            srcChannels = srcChannels,
            targetRate = config.audioSampleRate.hz,
            targetChannels = config.audioChannels.count
        )

    fun finishAudioStream() {
        val codec = audioCodec ?: return
        val inIdx = codec.dequeueInputBuffer(10_000L)
        if (inIdx >= 0) {
            val ptsUs = (totalAudioSamplesWritten * 1_000_000L) / config.audioSampleRate.hz
            codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainAudio(endOfStream = true)
    }

    private fun drainAudio(endOfStream: Boolean) {
        val codec = audioCodec ?: return
        var emptyLoops = 0
        while (emptyLoops < 120) {
            val status = codec.dequeueOutputBuffer(audioBufferInfo, if (endOfStream) 10_000L else 0L)
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    emptyLoops++
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxerLock) {
                        if (audioTrackIndex == -1) {
                            audioTrackIndex = muxer!!.addTrack(codec.outputFormat)
                            tryStartMuxerLocked()
                        }
                    }
                }
                status >= 0 -> {
                    emptyLoops = 0
                    val encodedData: ByteBuffer? = codec.getOutputBuffer(status)
                    if (encodedData != null && (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        if (audioBufferInfo.size > 0) {
                            var normalizedAudioPtsUs = audioBufferInfo.presentationTimeUs.coerceAtLeast(0L)
                            if (normalizedAudioPtsUs <= lastAudioPtsUs) {
                                normalizedAudioPtsUs = lastAudioPtsUs + 1
                            }
                            lastAudioPtsUs = normalizedAudioPtsUs

                            encodedData.position(audioBufferInfo.offset)
                            encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size)

                            synchronized(muxerLock) {
                                if (muxerStarted) {
                                    audioBufferInfo.presentationTimeUs = normalizedAudioPtsUs
                                    muxer!!.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo)
                                } else if (pendingSamples.size < 1024) {
                                    val copy = ByteArray(audioBufferInfo.size)
                                    encodedData.get(copy)
                                    val infoCopy = MediaCodec.BufferInfo().apply {
                                        set(0, copy.size, normalizedAudioPtsUs, audioBufferInfo.flags)
                                    }
                                    pendingSamples.add(PendingMuxSample(isVideo = false, bytes = copy, info = infoCopy))
                                }
                            }
                        }
                    }
                    codec.releaseOutputBuffer(status, false)
                    if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun tryStartMuxerLocked() {
        if (muxerStarted) return
        val videoReady = videoTrackIndex >= 0
        val audioReady = !expectAudioTrack || audioTrackIndex >= 0
        if (videoReady && audioReady) {
            muxer?.start()
            muxerStarted = true
            for (sample in pendingSamples) {
                val trackIdx = if (sample.isVideo) videoTrackIndex else audioTrackIndex
                if (trackIdx >= 0) {
                    muxer?.writeSampleData(trackIdx, ByteBuffer.wrap(sample.bytes), sample.info)
                }
            }
            pendingSamples.clear()
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { if (::videoCodec.isInitialized) videoCodec.stop() }
        runCatching { if (::videoCodec.isInitialized) videoCodec.release() }
        runCatching { if (::inputSurface.isInitialized) inputSurface.release() }
        runCatching { audioCodec?.stop() }
        runCatching { audioCodec?.release() }
        runCatching {
            if (muxerStarted) muxer?.stop()
            muxer?.release()
        }
        muxer = null
    }
}
