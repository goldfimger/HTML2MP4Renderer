package com.html2mp4.renderer

import com.html2mp4.renderer.engine.AudioPcmMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSyncMathTest {

    @Test
    fun validatesSampleRateChannelsAndInt16Alignment() {
        assertTrue(AudioPcmMath.isValidPcmFormat(byteCount = 4096, sampleRate = 48000, channels = 2))
        assertTrue(AudioPcmMath.isValidPcmFormat(byteCount = 2048, sampleRate = 44100, channels = 1))
        assertFalse(AudioPcmMath.isValidPcmFormat(byteCount = 0, sampleRate = 48000, channels = 2))
        assertFalse(AudioPcmMath.isValidPcmFormat(byteCount = 3, sampleRate = 48000, channels = 2))
        assertFalse(AudioPcmMath.isValidPcmFormat(byteCount = 4096, sampleRate = 4000, channels = 2))
        assertFalse(AudioPcmMath.isValidPcmFormat(byteCount = 4096, sampleRate = 48000, channels = 3))
    }

    @Test
    fun detectsMissingSequenceAndDuplicates() {
        val (d0, seq0) = AudioPcmMath.evaluateSequenceOrder(-1L, 0L)
        assertEquals(0L, d0)
        assertEquals(0L, seq0)

        val (dGap, seqAfterGap) = AudioPcmMath.evaluateSequenceOrder(seq0, 4L)
        assertEquals(3L, dGap)
        assertEquals(4L, seqAfterGap)

        val (dDup, seqAfterDup) = AudioPcmMath.evaluateSequenceOrder(seqAfterGap, 3L)
        assertEquals(1L, dDup)
        assertEquals(4L, seqAfterDup)
    }

    @Test
    fun computesBoundedSilenceCompensationOnlyAboveThreshold() {
        assertEquals(0L, AudioPcmMath.computeGapSilenceMs(relSourceMs = 140L, writtenMs = 100L))
        assertEquals(120L, AudioPcmMath.computeGapSilenceMs(relSourceMs = 220L, writtenMs = 100L))
        assertEquals(1_000L, AudioPcmMath.computeGapSilenceMs(relSourceMs = 3_500L, writtenMs = 100L))
    }

    @Test
    fun adaptsMonoAndSampleRateToTarget48kStereo() {
        // 441 samples (~10ms) mono @ 44.1kHz -> ~480 stereo frames (1920 bytes) @ 48kHz
        val srcMono = ByteArray(441 * 2) { idx -> if (idx % 2 == 0) 0x20 else 0x10 }
        val adapted = AudioPcmMath.adaptPcmToTarget(
            srcBytes = srcMono,
            srcRate = 44100,
            srcChannels = 1,
            targetRate = 48000,
            targetChannels = 2
        )
        assertEquals(0, adapted.size % 4)
        assertTrue(adapted.size in 1880..1960)
    }

    @Test
    fun capsAndTrimsAudioFramesToTargetDuration() {
        val maxFrames = AudioPcmMath.computeMaxSampleFrames(targetDurationMs = 97_560L, sampleRate = 48_000)
        assertEquals(4_682_880L, maxFrames)

        // 2048 stereo frames (8192 bytes) capped down to 500 frames -> 2000 bytes
        val chunk = ByteArray(2048 * 4) { 0x11 }
        val trimmed = AudioPcmMath.trimPcmToMaxFrames(chunk, channels = 2, maxFrames = 500L)
        assertEquals(2000, trimmed.size)

        val empty = AudioPcmMath.trimPcmToMaxFrames(chunk, channels = 2, maxFrames = 0L)
        assertEquals(0, empty.size)
    }
}
