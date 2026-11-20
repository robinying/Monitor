package com.robin.monitor.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 历史曲线几何：横轴按时间比例定位，长空档断线。
 */
class HistoryChartGeometryTest {

    @Test
    fun `positions use timestamp proportion instead of point index`() {
        val fractions = historyChartFractions(listOf(0L, 60_000L, 3_600_000L))

        assertEquals(0f, fractions[0], 0.0001f)
        assertEquals(1f / 60f, fractions[1], 0.0001f)
        assertEquals(1f, fractions[2], 0.0001f)
    }

    @Test
    fun `equal timestamps stay at the left edge`() {
        assertEquals(listOf(0f, 0f, 0f), historyChartFractions(listOf(5L, 5L, 5L)))
    }

    @Test
    fun `regular samples stay in one segment`() {
        assertEquals(listOf(0..3), historyChartSegments(listOf(0L, 60_000L, 120_000L, 180_000L)))
    }

    @Test
    fun `long sampling gap splits the line`() {
        val segments = historyChartSegments(
            listOf(0L, 60_000L, 120_000L, 3_600_000L, 3_660_000L),
        )

        assertEquals(listOf(0..2, 3..4), segments)
    }

    @Test
    fun `clock rollback splits the line`() {
        val segments = historyChartSegments(listOf(120_000L, 60_000L, 0L))

        assertEquals(listOf(0..0, 1..1, 2..2), segments)
    }
}
