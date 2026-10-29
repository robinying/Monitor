package com.t2m.monitor.domain

import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.BatteryStatus
import com.t2m.monitor.data.model.CpuSnapshot
import com.t2m.monitor.data.model.MemorySnapshot
import com.t2m.monitor.data.model.MetricKeys
import com.t2m.monitor.data.model.MonitorSnapshot
import com.t2m.monitor.data.model.PlugState
import com.t2m.monitor.data.model.StorageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10 秒窗口聚合：平均值、缺失指标跳过、窗口重置。
 */
class MetricAggregatorTest {

    private var nowMillis = 0L

    private val aggregator = MetricAggregator { nowMillis }

    @Test
    fun `averages buffered values per metric on flush`() {
        aggregator.add(snapshot(batteryPercent = 50, temperature = 30.0, cpu = 20f, memoryUsed = 40f, storageUsed = 60f))
        nowMillis = 10_000L
        assertTrue(aggregator.shouldFlush())
        aggregator.add(snapshot(batteryPercent = 60, temperature = 32.0, cpu = 40f, memoryUsed = 60f, storageUsed = 60f))

        val aggregates = aggregator.flush(timestampMillis = 123_456L).associateBy { it.metric }
        assertEquals(5, aggregates.size)
        assertEquals(55f, aggregates.getValue(MetricKeys.BATTERY_LEVEL).value, 0.01f)
        assertEquals(31f, aggregates.getValue(MetricKeys.BATTERY_TEMPERATURE).value, 0.01f)
        assertEquals(30f, aggregates.getValue(MetricKeys.CPU_USAGE).value, 0.01f)
        assertEquals(50f, aggregates.getValue(MetricKeys.MEMORY_USED_PERCENT).value, 0.01f)
        assertEquals(60f, aggregates.getValue(MetricKeys.STORAGE_USED_PERCENT).value, 0.01f)
        assertEquals(123_456L, aggregates.getValue(MetricKeys.CPU_USAGE).timestampMillis)
    }

    @Test
    fun `flush empties buffers and restarts window`() {
        aggregator.add(snapshot())
        aggregator.flush(timestampMillis = 1L)
        assertTrue(aggregator.flush(timestampMillis = 2L).isEmpty())
        nowMillis = 5_000L
        assertFalse(aggregator.shouldFlush())
    }

    @Test
    fun `skips metrics that have no samples`() {
        aggregator.add(
            MonitorSnapshot(
                timestampMillis = 1L,
                battery = null,
                cpu = null,
                memory = MemorySnapshot(totalBytes = 100L, availBytes = 40L, lowMemory = false),
                storage = null,
            ),
        )
        val aggregates = aggregator.flush(timestampMillis = 3L)
        assertEquals(1, aggregates.size)
        assertEquals(MetricKeys.MEMORY_USED_PERCENT, aggregates.single().metric)
        assertNull(aggregates.singleOrNull { it.metric == MetricKeys.CPU_USAGE })
    }

    private fun snapshot(
        batteryPercent: Int = 50,
        temperature: Double = 30.0,
        cpu: Float = 20f,
        memoryUsed: Float = 40f,
        storageUsed: Float = 60f,
    ): MonitorSnapshot = MonitorSnapshot(
        timestampMillis = nowMillis,
        battery = BatterySnapshot(
            percent = batteryPercent,
            temperatureC = temperature,
            currentMicroAmps = -500_000,
            voltageMv = 4100,
            status = BatteryStatus.DISCHARGING,
            plugged = PlugState.NONE,
            health = 2,
        ),
        cpu = CpuSnapshot(
            totalUsagePercent = cpu,
            perCoreUsagePercent = listOf(cpu),
            perCoreFrequencyKHz = listOf(1_800_000),
            maxFrequencyKHz = 2_800_000,
        ),
        memory = MemorySnapshot(
            totalBytes = 100L,
            availBytes = (100L - memoryUsed).toLong(),
            lowMemory = false,
        ),
        storage = StorageSnapshot(
            totalBytes = 100L,
            availableBytes = (100L - storageUsed).toLong(),
        ),
    )
}
