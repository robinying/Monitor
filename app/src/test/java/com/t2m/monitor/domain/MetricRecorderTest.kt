package com.t2m.monitor.domain

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.collector.Collector
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorRegistry
import com.t2m.monitor.data.db.TestMetricDao
import com.t2m.monitor.data.db.TransactionRunner
import com.t2m.monitor.data.model.MemorySnapshot
import com.t2m.monitor.data.model.MetricKeys
import com.t2m.monitor.data.repository.MetricRepository
import com.t2m.monitor.domain.diagnostics.DiagnosticsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记录器生命周期：停止/快速重启、在途写入取消、落库失败重试均不得丢失窗口。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetricRecorderTest {

    @Test
    fun `stop followed by immediate start persists the unfinished window once`() = runTest {
        val env = createEnv()

        env.engine.start()
        env.recorder.start()
        advanceTimeBy(2_100L)
        runCurrent()

        env.engine.stop()
        env.recorder.stop()
        env.recorder.start()
        runCurrent()

        assertEquals(1, env.dao.inserted.size)
        val firstTimestamp = env.dao.inserted.single().timestampMillis
        assertEquals(WALL_CLOCK_BASE + 2_100L, firstTimestamp)
        assertEquals(60f, env.dao.inserted.single().value, 0.01f)

        env.engine.start()
        advanceTimeBy(10_100L)
        runCurrent()
        env.recorder.stop()
        env.engine.stop()
        runCurrent()

        assertEquals(2, env.dao.inserted.size)
        assertEquals(firstTimestamp, env.dao.inserted.first().timestampMillis)
    }

    @Test
    fun `stop does not lose a window whose database insert is still running`() = runTest {
        val env = createEnv()
        env.dao.insertBehavior = { samples ->
            delay(500L)
            env.dao.inserted += samples
        }

        env.engine.start()
        env.recorder.start()
        advanceTimeBy(10_100L)
        runCurrent()

        env.recorder.stop()
        env.engine.stop()
        runCurrent()
        advanceTimeBy(600L)
        runCurrent()

        assertEquals(1, env.dao.inserted.size)
        assertEquals(MetricKeys.MEMORY_USED_PERCENT, env.dao.inserted.single().metric)
    }

    @Test
    fun `failed insert is retried on the next flush`() = runTest {
        val env = createEnv()
        var failures = 1
        env.dao.insertBehavior = { samples ->
            if (failures > 0) {
                failures--
                throw IllegalStateException("database unavailable")
            }
            env.dao.inserted += samples
        }

        env.engine.start()
        env.recorder.start()
        advanceTimeBy(10_100L)
        runCurrent()

        assertTrue(env.dao.inserted.isEmpty())

        env.recorder.flushNowAndWait()
        assertEquals(1, env.dao.inserted.size)

        env.recorder.stop()
        env.engine.stop()
        runCurrent()
    }

    private fun TestScope.createEnv(): Environment {
        val dao = TestMetricDao()
        val clock = object : MonitorClock {
            override fun elapsedRealtimeMillis(): Long = testScheduler.currentTime

            override fun currentTimeMillis(): Long = WALL_CLOCK_BASE + testScheduler.currentTime
        }
        val logger = object : MonitorLogger {
            val messages = mutableListOf<String>()

            override fun warn(message: String, throwable: Throwable?) {
                messages += message
            }
        }
        val collector = object : Collector<MemorySnapshot> {
            override val id: CollectorId = CollectorId.MEMORY
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): MemorySnapshot =
                MemorySnapshot(totalBytes = 100L, availBytes = 40L, lowMemory = false)
        }
        val engine = MonitorEngine(
            registry = CollectorRegistry(listOf(collector)),
            clock = clock,
            scheduler = SamplingScheduler(tickIntervalMillis = 1_000L),
            logger = logger,
            diagnostics = DiagnosticsRepository(clock),
            scope = backgroundScope,
        )
        val repository = MetricRepository(dao, DirectTransactionRunner)
        val recorder = MetricRecorder(engine, repository, clock, logger, backgroundScope)
        return Environment(dao, engine, recorder)
    }

    private class Environment(
        val dao: TestMetricDao,
        val engine: MonitorEngine,
        val recorder: MetricRecorder,
    )

    private object DirectTransactionRunner : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private companion object {
        const val WALL_CLOCK_BASE = 1_700_000_000_000L
    }
}
