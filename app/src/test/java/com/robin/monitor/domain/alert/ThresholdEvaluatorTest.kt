package com.robin.monitor.domain.alert

import com.robin.monitor.data.collector.CollectorId
import com.robin.monitor.data.model.AlertEvent
import com.robin.monitor.data.model.AlertType
import com.robin.monitor.data.model.BatterySnapshot
import com.robin.monitor.data.model.BatteryStatus
import com.robin.monitor.data.model.MemorySnapshot
import com.robin.monitor.data.model.MonitorSnapshot
import com.robin.monitor.data.model.PlugState
import com.robin.monitor.data.model.StorageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阈值判定：连续采样要求、回落重置、去抖窗口与各告警类型。
 */
class ThresholdEvaluatorTest {

    private val evaluator = ThresholdEvaluator()

    @Test
    fun `alerts only after three consecutive high temperatures`() {
        assertTrue(evaluator.evaluate(snapshot(temperature = 46.0), 1_000L).isEmpty())
        assertTrue(evaluator.evaluate(snapshot(temperature = 46.0), 2_000L).isEmpty())

        val events = evaluator.evaluate(snapshot(temperature = 46.0), 3_000L)
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(AlertType.BATTERY_TEMPERATURE_HIGH, event.type)
        assertEquals(45f, event.threshold, 0.01f)
        assertEquals(46f, event.actualValue, 0.01f)
    }

    @Test
    fun `resets consecutive count when value returns to normal`() {
        evaluator.evaluate(snapshot(temperature = 46.0), 1_000L)
        evaluator.evaluate(snapshot(temperature = 46.0), 2_000L)
        evaluator.evaluate(snapshot(temperature = 35.0), 3_000L)
        evaluator.evaluate(snapshot(temperature = 46.0), 4_000L)
        evaluator.evaluate(snapshot(temperature = 46.0), 5_000L)
        assertEquals(1, evaluator.evaluate(snapshot(temperature = 46.0), 6_000L).size)
    }

    @Test
    fun `debounce suppresses repeated alerts within window`() {
        evaluator.evaluate(snapshot(temperature = 46.0), 1_000L)
        evaluator.evaluate(snapshot(temperature = 46.0), 2_000L)
        assertEquals(1, evaluator.evaluate(snapshot(temperature = 46.0), 3_000L).size)

        // 按 10 秒间隔持续采样，避免把测试的时间跳变误当成暂停。
        val suppressed = evaluateEveryTenSeconds(fromMillis = 3_000L, untilMillis = 3_000L + 5 * 60_000L)
        assertTrue(suppressed.all { events -> events.isEmpty() })

        val afterWindow = evaluateEveryTenSeconds(
            fromMillis = 3_000L + 5 * 60_000L,
            untilMillis = 3_000L + 31 * 60_000L,
        )
        assertEquals(1, afterWindow.sumOf { events -> events.size })
    }

    private fun evaluateEveryTenSeconds(
        fromMillis: Long,
        untilMillis: Long,
    ): List<List<AlertEvent>> {
        val results = mutableListOf<List<AlertEvent>>()
        var timestamp = fromMillis
        while (timestamp < untilMillis) {
            timestamp += 10_000L
            results += evaluator.evaluate(snapshot(temperature = 46.0), timestamp)
        }
        return results
    }

    @Test
    fun `battery low alerts once per debounce window`() {
        val events = evaluator.evaluate(snapshot(batteryPercent = 15), 1_000L)
        assertEquals(1, events.size)
        assertEquals(AlertType.BATTERY_LOW, events.single().type)

        assertTrue(evaluator.evaluate(snapshot(batteryPercent = 15), 1_000L + 10 * 60_000L).isEmpty())
    }

    @Test
    fun `memory alerts after three consecutive samples`() {
        evaluator.evaluate(snapshot(memoryUsedPercent = 95f), 1_000L)
        evaluator.evaluate(snapshot(memoryUsedPercent = 95f), 2_000L)
        val events = evaluator.evaluate(snapshot(memoryUsedPercent = 95f), 3_000L)
        assertEquals(1, events.size)
        assertEquals(AlertType.MEMORY_LOW, events.single().type)
    }

    @Test
    fun `storage low uses one hour debounce`() {
        val events = evaluator.evaluate(snapshot(storageAvailableGb = 1.0), 1_000L)
        assertEquals(1, events.size)
        assertEquals(AlertType.STORAGE_LOW, events.single().type)
        assertEquals(2f, events.single().threshold, 0.01f)
        assertEquals(1f, events.single().actualValue, 0.01f)

        assertTrue(evaluator.evaluate(snapshot(storageAvailableGb = 1.0), 1_000L + 30 * 60_000L).isEmpty())
        assertEquals(
            1,
            evaluator.evaluate(snapshot(storageAvailableGb = 1.0), 1_000L + 61 * 60_000L).size,
        )
    }

    @Test
    fun `stale retained values do not count as new consecutive samples`() {
        val stale = snapshot(
            temperature = 46.0,
            sampleTimestampsMillis = mapOf(CollectorId.BATTERY to 1_000L),
        )

        repeat(3) {
            assertTrue(evaluator.evaluate(stale, 2_000L).isEmpty())
        }
    }

    @Test
    fun `debounce follows the monotonic clock when the wall clock moves`() {
        val evaluator = ThresholdEvaluator(requiredConsecutiveSamples = 1)
        val dayMillis = 24L * 60 * 60 * 1000
        val base = 1_000_000L
        assertEquals(1, evaluator.evaluate(snapshot(batteryPercent = 15), base, base).size)

        // 墙上时钟回拨一天，但单调时钟只过了 10 分钟：仍在 30 分钟冷却内。
        assertTrue(
            evaluator.evaluate(snapshot(batteryPercent = 15), base - dayMillis, base + 10 * 60_000L)
                .isEmpty(),
        )
        assertEquals(
            1,
            evaluator.evaluate(snapshot(batteryPercent = 15), base - dayMillis + 1, base + 31 * 60_000L)
                .size,
        )
    }

    @Test
    fun `long sampling gap restarts the consecutive window`() {
        evaluator.evaluate(snapshot(temperature = 46.0), 1_000L)
        evaluator.evaluate(snapshot(temperature = 46.0), 2_000L)

        // 暂停 20 秒后恢复：之前的两次不应计入新的连续窗口。
        assertTrue(evaluator.evaluate(snapshot(temperature = 46.0), 22_000L).isEmpty())
        assertTrue(evaluator.evaluate(snapshot(temperature = 46.0), 23_000L).isEmpty())
        assertEquals(1, evaluator.evaluate(snapshot(temperature = 46.0), 24_000L).size)
    }

    @Test
    fun `reset clears consecutive counts but keeps debounce`() {
        val evaluator = ThresholdEvaluator()
        evaluator.evaluate(snapshot(temperature = 46.0), 1_000L)
        evaluator.evaluate(snapshot(temperature = 46.0), 2_000L)

        evaluator.resetConsecutiveCounts()

        assertTrue(evaluator.evaluate(snapshot(temperature = 46.0), 3_000L).isEmpty())
    }

    private fun snapshot(
        batteryPercent: Int = 50,
        temperature: Double = 35.0,
        memoryUsedPercent: Float = 50f,
        storageAvailableGb: Double = 50.0,
        sampleTimestampsMillis: Map<CollectorId, Long> = emptyMap(),
    ): MonitorSnapshot {
        val memoryTotal = 1_000L * 1024 * 1024
        return MonitorSnapshot(
            timestampMillis = 1L,
            battery = BatterySnapshot(
                percent = batteryPercent,
                temperatureC = temperature,
                currentMicroAmps = -500_000,
                voltageMv = 4_100,
                status = BatteryStatus.DISCHARGING,
                plugged = PlugState.NONE,
                health = 2,
            ),
            memory = MemorySnapshot(
                totalBytes = memoryTotal,
                availBytes = (memoryTotal * (100f - memoryUsedPercent) / 100f).toLong(),
                lowMemory = false,
            ),
            storage = StorageSnapshot(
                totalBytes = 100L * 1024 * 1024 * 1024,
                availableBytes = (storageAvailableGb * 1024 * 1024 * 1024).toLong(),
            ),
            sampleTimestampsMillis = sampleTimestampsMillis,
        )
    }
}
