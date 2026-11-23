package com.html2mp4.renderer.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.html2mp4.renderer.model.AudioCodecOption
import com.html2mp4.renderer.model.RenderConfig
import java.io.File
import java.nio.ByteBuffer

/**
 * Hardware-accelerated MediaCodec + MediaMuxer pipeline tuned for Exynos 850 MFC.
 * Enforces strictly monotonic PTS and safe encoder fallback so MediaMuxer never throws.
 */
class MediaCodecMuxerPipeline(
    private val config: RenderConfig,
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

    private val expectAudioTrack = !config.isAudioMuted
    private var totalAudioSamplesWritten = 0L
    private var lastVideoPtsUs = -1L
    private var lastAudioPtsUs = -1L

    fun prepare() {
        val width = config.resolvedWidth
        val height = config.resolvedHeight

        videoCodec = configureVideoEncoderWithFallback(width, height)
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
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
            }

            audioCodec = MediaCodec.createEncoderByType(resolvedAudioMime).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        }

        muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Seed 1 tiny silence frame early so audioTrackIndex registers alongside videoTrackIndex
        if (expectAudioTrack) {
            val warmupSilence = ByteArray(1024 * 2 * config.audioChannels.count)
            feedAudioPcm(warmupSilence)
        }
    }

    private fun configureVideoEncoderWithFallback(width: Int, height: Int): MediaCodec {
        val mime = config.videoCodec.mimeType
        return try {
            val fmt = createVideoFormat(mime, width, height)
            MediaCodec.createEncoderByType(mime).apply {
                configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
        } catch (_: Throwable) {
            // Safe fallback to H.264/AVC with 16-px macroblock alignment if custom size/HEVC is rejected
            val fallbackMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val alignedW = (((width + 8) / 16) * 16).coerceAtLeast(320)
            val alignedH = (((height + 8) / 16) * 16).coerceAtLeast(240)
            val fmt = createVideoFormat(fallbackMime, alignedW, alignedH)
            MediaCodec.createEncoderByType(fallbackMime).apply {
                configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
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
                            synchronized(muxerLock) {
                                if (muxerStarted) {
                                    if (videoBufferInfo.presentationTimeUs <= lastVideoPtsUs) {
                                        videoBufferInfo.presentationTimeUs = lastVideoPtsUs + 1
                                    }
                                    lastVideoPtsUs = videoBufferInfo.presentationTimeUs
                                    encodedData.position(videoBufferInfo.offset)
                                    encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                    muxer!!.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo)
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

    fun feedAudioPcm(pcmBytes: ByteArray) {
        val codec = audioCodec ?: return
        var offset = 0
        var retries = 0
        while (offset < pcmBytes.size && retries < 24) {
            val inIdx = codec.dequeueInputBuffer(4_000L)
            if (inIdx < 0) {
                drainAudio(endOfStream = false)
                retries++
                continue
            }
            retries = 0
            val buf = codec.getInputBuffer(inIdx) ?: break
            buf.clear()
            val toCopy = minOf(buf.remaining(), pcmBytes.size - offset)
            if (toCopy <= 0) break
            buf.put(pcmBytes, offset, toCopy)

            val bytesPerSampleFrame = 2 * config.audioChannels.count
            val framesInChunk = toCopy / bytesPerSampleFrame
            val ptsUs = (totalAudioSamplesWritten * 1_000_000L) / config.audioSampleRate.hz
            totalAudioSamplesWritten += framesInChunk

            codec.queueInputBuffer(inIdx, 0, toCopy, ptsUs, 0)
            offset += toCopy
            drainAudio(endOfStream = false)
        }
    }

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
                            synchronized(muxerLock) {
                                if (muxerStarted) {
                                    if (audioBufferInfo.presentationTimeUs <= lastAudioPtsUs) {
                                        audioBufferInfo.presentationTimeUs = lastAudioPtsUs + 1
                                    }
                                    lastAudioPtsUs = audioBufferInfo.presentationTimeUs
                                    encodedData.position(audioBufferInfo.offset)
                                    encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                    muxer!!.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo)
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
        }
    }

    fun release() {
        runCatching { if (::videoCodec.isInitialized) videoCodec.stop() }
        runCatching { if (::videoCodec.isInitialized) videoCodec.release() }
        runCatching { if (::inputSurface.isInitialized) inputSurface.release() }
        runCatching { audioCodec?.stop() }
        runCatching { audioCodec?.release() }
        runCatching {
            if (muxerStarted) muxer?.stop()
            muxer?.release()
        }
    }
}
