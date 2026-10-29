package com.t2m.monitor.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 数据保留策略的截止时间计算。
 */
class RetentionPolicyTest {

    private val now = 100L * 24 * 60 * 60 * 1000

    @Test
    fun `raw samples keep seven days`() {
        assertEquals(now - 7L * 24 * 60 * 60 * 1000, RetentionPolicy.rawCutoff(now))
    }

    @Test
    fun `hourly aggregates keep ninety days`() {
        assertEquals(now - 90L * 24 * 60 * 60 * 1000, RetentionPolicy.hourlyCutoff(now))
    }

    @Test
    fun `csv metric order is stable`() {
        assertEquals(
            listOf(
                MetricKeys.BATTERY_LEVEL,
                MetricKeys.BATTERY_TEMPERATURE,
                MetricKeys.CPU_USAGE,
                MetricKeys.MEMORY_USED_PERCENT,
                MetricKeys.STORAGE_USED_PERCENT,
                MetricKeys.THERMAL_MAX,
            ),
            MetricKeys.ALL,
        )
    }
}
