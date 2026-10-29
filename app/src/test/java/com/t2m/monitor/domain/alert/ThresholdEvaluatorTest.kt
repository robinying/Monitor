package com.t2m.monitor.domain.alert

import com.t2m.monitor.data.model.AlertType
import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.BatteryStatus
import com.t2m.monitor.data.model.MemorySnapshot
import com.t2m.monitor.data.model.MonitorSnapshot
import com.t2m.monitor.data.model.PlugState
import com.t2m.monitor.data.model.StorageSnapshot
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

        val suppressed = evaluator.evaluate(snapshot(temperature = 46.0), 3_000L + 5 * 60_000L)
        assertTrue(suppressed.isEmpty())

        val afterWindow = evaluator.evaluate(snapshot(temperature = 46.0), 3_000L + 31 * 60_000L)
        assertEquals(1, afterWindow.size)
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

    private fun snapshot(
        batteryPercent: Int = 50,
        temperature: Double = 35.0,
        memoryUsedPercent: Float = 50f,
        storageAvailableGb: Double = 50.0,
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
        )
    }
}
