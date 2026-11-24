package com.html2mp4.renderer

import com.html2mp4.renderer.engine.DeterministicTimelineEngine
import com.html2mp4.renderer.model.HtmlAnimationCapabilities
import com.html2mp4.renderer.model.RenderTimelineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicTimelineEngineTest {

    @Test
    fun deterministicTimestampsMatch30FpsSpecification() {
        val engine = DeterministicTimelineEngine(fps = 30, durationMs = 1_000L)
        assertEquals(30, engine.totalFrames)

        val f0 = engine.setTime(0)
        val f1 = engine.setTime(1)
        val f2 = engine.setTime(2)
        val f3 = engine.setTime(3)

        assertEquals(0.0, f0.htmlAnimationTimeMs, 0.01)
        assertEquals(33.333, f1.htmlAnimationTimeMs, 0.02)
        assertEquals(66.667, f2.htmlAnimationTimeMs, 0.02)
        assertEquals(100.0, f3.htmlAnimationTimeMs, 0.01)

        assertTrue(f1.encoderPresentationTimeNs > f0.encoderPresentationTimeNs)
        assertTrue(f2.encoderPresentationTimeNs > f1.encoderPresentationTimeNs)
        assertTrue(f3.encoderPresentationTimeNs > f2.encoderPresentationTimeNs)
    }

    @Test
    fun monotonicPtsNeverMovesBackwards() {
        val engine = DeterministicTimelineEngine(fps = 60, durationMs = 2_000L)
        val p5 = engine.allocateMonotonicPtsNs(5)
        val pDuplicate = engine.allocateMonotonicPtsNs(3)
        assertTrue(pDuplicate > p5)
    }

    @Test
    fun advanceStopsAtTotalFramesAndTracksAccounting() {
        val engine = DeterministicTimelineEngine(fps = 24, durationMs = 1_000L)
        var count = 0
        while (engine.hasNextFrame()) {
            val tick = engine.advance() ?: break
            assertEquals(count, tick.frameIndex)
            engine.recordSubmittedFrame()
            count++
        }
        assertEquals(24, count)
        assertNull(engine.advance())

        engine.recordRepeatedFrame()
        engine.recordDroppedOrFailedFrame()
        val summary = engine.buildSummary(
            mode = RenderTimelineMode.DETERMINISTIC,
            usedBitmapFallback = false,
            renderWallDurationMs = 950L,
            capabilities = HtmlAnimationCapabilities(usesRaf = true)
        )
        assertEquals(24, summary.expectedFrames)
        assertEquals(25, summary.submittedFrames)
        assertEquals(1, summary.repeatedFrames)
        assertEquals(1, summary.droppedOrFailedFrames)
    }
}
