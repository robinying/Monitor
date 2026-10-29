package com.t2m.monitor.domain

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.collector.Collector
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorRegistry
import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.BatteryStatus
import com.t2m.monitor.data.model.MemorySnapshot
import com.t2m.monitor.data.model.PlugState
import com.t2m.monitor.domain.diagnostics.DiagnosticsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 采集中枢：验证结果合并、失败保留旧值、异常记录与启停状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorEngineTest {

    @Test
    fun `merges results and retains last value when a collector yields nothing`() = runTest {
        var batteryValue: BatterySnapshot? = battery(percent = 50)
        val batteryCollector = object : Collector<BatterySnapshot> {
            override val id: CollectorId = CollectorId.BATTERY
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): BatterySnapshot? = batteryValue
        }
        val memoryCollector = object : Collector<MemorySnapshot> {
            override val id: CollectorId = CollectorId.MEMORY
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): MemorySnapshot =
                MemorySnapshot(totalBytes = 8L * GIB, availBytes = 4L * GIB, lowMemory = false)
        }
        val engine = createEngine(
            collectors = listOf(batteryCollector, memoryCollector),
            logger = RecordingLogger(),
        )
        engine.start()
        advanceTimeBy(1_100L)
        runCurrent()

        val first = engine.snapshot.value
        assertEquals(50, first.battery?.percent)
        assertNotNull(first.memory)
        assertTrue(engine.isRunning.value)

        batteryValue = null
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(50, engine.snapshot.value.battery?.percent)
        engine.stop()
        assertFalse(engine.isRunning.value)
    }

    @Test
    fun `logs collector failure and keeps other metrics`() = runTest {
        val failing = object : Collector<Any> {
            override val id: CollectorId = CollectorId.CPU
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): Any = throw IllegalStateException("boom")
        }
        val batteryCollector = object : Collector<BatterySnapshot> {
            override val id: CollectorId = CollectorId.BATTERY
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): BatterySnapshot = battery(percent = 77)
        }
        val logger = RecordingLogger()
        val engine = createEngine(collectors = listOf(failing, batteryCollector), logger = logger)

        engine.start()
        advanceTimeBy(1_100L)
        runCurrent()

        assertEquals(77, engine.snapshot.value.battery?.percent)
        assertTrue(logger.messages.any { message -> message.contains("CPU") })
        engine.stop()
    }

    @Test
    fun `start is idempotent and stop clears running state`() = runTest {
        val batteryCollector = object : Collector<BatterySnapshot> {
            override val id: CollectorId = CollectorId.BATTERY
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): BatterySnapshot = battery(percent = 60)
        }
        val engine = createEngine(collectors = listOf(batteryCollector), logger = RecordingLogger())
        engine.start()
        engine.start()
        advanceTimeBy(1_100L)
        runCurrent()
        assertTrue(engine.isRunning.value)

        engine.stop()
        engine.stop()
        assertFalse(engine.isRunning.value)
    }

    private fun TestScope.createEngine(
        collectors: List<Collector<*>>,
        logger: MonitorLogger,
    ): MonitorEngine = MonitorEngine(
        registry = CollectorRegistry(collectors),
        clock = TestMonitorClock(testScheduler),
        scheduler = SamplingScheduler(tickIntervalMillis = 1_000L),
        logger = logger,
        diagnostics = DiagnosticsRepository(TestMonitorClock(testScheduler)),
        scope = backgroundScope,
    )

    private fun battery(percent: Int): BatterySnapshot = BatterySnapshot(
        percent = percent,
        temperatureC = 33.0,
        currentMicroAmps = -500_000,
        voltageMv = 4100,
        status = BatteryStatus.DISCHARGING,
        plugged = PlugState.NONE,
        health = 2,
    )

    private class RecordingLogger : MonitorLogger {
        val messages = mutableListOf<String>()

        override fun warn(message: String, throwable: Throwable?) {
            messages += message
        }
    }

    private class TestMonitorClock(private val scheduler: TestCoroutineScheduler) : MonitorClock {
        override fun elapsedRealtimeMillis(): Long = scheduler.currentTime

        override fun currentTimeMillis(): Long = WALL_CLOCK_BASE + scheduler.currentTime
    }

    private companion object {
        const val GIB = 1024L * 1024 * 1024
        const val WALL_CLOCK_BASE = 1_700_000_000_000L
    }
}
