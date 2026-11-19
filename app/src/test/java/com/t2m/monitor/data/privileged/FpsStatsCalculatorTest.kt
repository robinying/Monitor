package com.t2m.monitor.data.privileged

import com.t2m.monitor.data.model.FpsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * timestats 增量换算：FPS、卡顿率、首样本回退与计数器重置。
 */
class FpsStatsCalculatorTest {

    @Test
    fun `computes fps and jank from counter deltas`() {
        val snapshot = FpsStatsCalculator.fromTimeStats(
            stats = FpsParser.TimeStats(
                totalFrames = 160L,
                missedFrames = 2L,
                averageFps = 59f,
                frameTimeP95Millis = 17,
                frameTimeP99Millis = 33,
            ),
            previousTotalFrames = 100L,
            previousMissedFrames = 0L,
            elapsedNanos = 1_000_000_000L,
        )!!

        assertEquals(60f, snapshot.fps, 0.01f)
        assertEquals(3.225f, snapshot.jankPercent!!, 0.01f)
        assertEquals(17, snapshot.frameTimeP95Millis)
        assertEquals(33, snapshot.frameTimeP99Millis)
        assertEquals(FpsSource.TIMESTATS, snapshot.source)
    }

    @Test
    fun `uses averageFPS for the first sample`() {
        val snapshot = FpsStatsCalculator.fromTimeStats(
            stats = FpsParser.TimeStats(
                totalFrames = 120L,
                missedFrames = 2L,
                averageFps = 59.5f,
                frameTimeP95Millis = null,
                frameTimeP99Millis = null,
            ),
            previousTotalFrames = null,
            previousMissedFrames = null,
            elapsedNanos = 5_000_000_000L,
        )!!

        assertEquals(59.5f, snapshot.fps, 0.01f)
        assertEquals(1.639f, snapshot.jankPercent!!, 0.01f)
        assertNull(snapshot.frameTimeP95Millis)
    }

    @Test
    fun `falls back to averageFPS when counters reset`() {
        val snapshot = FpsStatsCalculator.fromTimeStats(
            stats = FpsParser.TimeStats(
                totalFrames = 10L,
                missedFrames = 0L,
                averageFps = 48f,
                frameTimeP95Millis = null,
                frameTimeP99Millis = null,
            ),
            previousTotalFrames = 900L,
            previousMissedFrames = 0L,
            elapsedNanos = 1_000_000_000L,
        )!!

        assertEquals(48f, snapshot.fps, 0.01f)
        assertNull(snapshot.jankPercent)
    }

    @Test
    fun `returns null without frames or average`() {
        assertNull(
            FpsStatsCalculator.fromTimeStats(
                stats = FpsParser.TimeStats(null, null, null, null, null),
                previousTotalFrames = null,
                previousMissedFrames = null,
                elapsedNanos = 1_000_000_000L,
            ),
        )
    }
}
