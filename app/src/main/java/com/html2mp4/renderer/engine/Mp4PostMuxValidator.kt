package com.html2mp4.renderer.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.html2mp4.renderer.model.Mp4ValidationReport
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Inspects the finalized MP4 file immediately after MediaMuxer stops so track presence,
 * duration alignment, FPS, keyframe decodability, and playability are explicitly verified.
 */
object Mp4PostMuxValidator {

    fun validate(
        mp4File: File,
        expectedDurationMs: Long,
        expectAudioTrack: Boolean
    ): Mp4ValidationReport {
        if (!mp4File.exists() || mp4File.length() <= 0L) {
            return Mp4ValidationReport(
                fileExists = mp4File.exists(),
                fileSizeBytes = 0L,
                audioExpected = expectAudioTrack,
                expectedDurationMs = expectedDurationMs,
                isPlayable = false,
                warnings = listOf("File MP4 kosong atau tidak ditemukan.")
            )
        }

        val warnings = mutableListOf<String>()
        var hasVideoTrack = false
        var hasAudioTrack = false
        var videoMime: String? = null
        var audioMime: String? = null
        var width = 0
        var height = 0
        var measuredFps = 0f
        var actualDurationMs = 0L
        var aRate = 0
        var aChannels = 0
        var videoSampleReadable = false
        var firstKeyFrameDecoded = false

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(mp4File.absolutePath)
            var videoTrackIdx = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && !hasVideoTrack) {
                    hasVideoTrack = true
                    videoTrackIdx = i
                    videoMime = mime
                    if (fmt.containsKey(MediaFormat.KEY_WIDTH)) width = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        measuredFps = fmt.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    }
                    if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        actualDurationMs = maxOf(actualDurationMs, fmt.getLong(MediaFormat.KEY_DURATION) / 1_000L)
                    }
                } else if (mime.startsWith("audio/") && !hasAudioTrack) {
                    hasAudioTrack = true
                    audioMime = mime
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) aRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) aChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        actualDurationMs = maxOf(actualDurationMs, fmt.getLong(MediaFormat.KEY_DURATION) / 1_000L)
                    }
                }
            }

            if (videoTrackIdx >= 0) {
                extractor.selectTrack(videoTrackIdx)
                val probeBuf = ByteBuffer.allocate(512 * 1024)
                val sampleSize = extractor.readSampleData(probeBuf, 0)
                videoSampleReadable = sampleSize > 0
            }
        } catch (t: Throwable) {
            warnings += "MediaExtractor gagal membaca kontainer MP4: ${t.localizedMessage ?: t::class.java.simpleName}"
        } finally {
            runCatching { extractor.release() }
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(mp4File.absolutePath)
            val retDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (retDur > 0L) actualDurationMs = retDur
            if (width <= 0) {
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            }
            if (height <= 0) {
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            }
            val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
            if (frameCount != null && frameCount > 0f && actualDurationMs > 0L) {
                measuredFps = (frameCount * 1000f) / actualDurationMs.toFloat()
            }
            val bmp = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            firstKeyFrameDecoded = bmp != null
            bmp?.recycle()
        } catch (t: Throwable) {
            warnings += "Decoder thumbnail keyframe gagal: ${t.localizedMessage ?: t::class.java.simpleName}"
        } finally {
            runCatching { retriever.release() }
        }

        if (!hasVideoTrack) warnings += "Track video tidak ditemukan di MP4."
        if (!videoSampleReadable) warnings += "Sample video tidak dapat dibaca MediaExtractor."
        if (!firstKeyFrameDecoded) warnings += "Keyframe pertama gagal didekode."
        if (expectAudioTrack && !hasAudioTrack) warnings += "Track audio diharapkan namun tidak ditemukan."

        val durationDeltaMs = abs(actualDurationMs - expectedDurationMs)
        if (expectedDurationMs > 0L && durationDeltaMs > maxOf(1_500L, expectedDurationMs / 5L)) {
            warnings += "Selisih durasi MP4 cukup besar (${actualDurationMs}ms vs target ${expectedDurationMs}ms)."
        }

        val playable = hasVideoTrack && videoSampleReadable && firstKeyFrameDecoded && actualDurationMs > 0L
        return Mp4ValidationReport(
            fileExists = true,
            fileSizeBytes = mp4File.length(),
            hasVideoTrack = hasVideoTrack,
            hasAudioTrack = hasAudioTrack,
            audioExpected = expectAudioTrack,
            videoMime = videoMime,
            audioMime = audioMime,
            videoWidth = width,
            videoHeight = height,
            measuredFps = measuredFps,
            actualDurationMs = actualDurationMs,
            expectedDurationMs = expectedDurationMs,
            durationDeltaMs = durationDeltaMs,
            audioSampleRateHz = aRate,
            audioChannels = aChannels,
            firstKeyFrameDecoded = firstKeyFrameDecoded,
            isPlayable = playable,
            warnings = warnings
        )
    }
}
