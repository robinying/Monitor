package com.t2m.monitor.domain

import com.t2m.monitor.data.collector.Collector
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 采样调度：验证多速率合并、超时丢弃与异常上报。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SamplingSchedulerTest {

    @Test
    fun `runs collectors according to their own intervals`() = runTest {
        val fast = FakeCollector(CollectorId.BATTERY, intervalMillis = 1_000L)
        val slow = FakeCollector(CollectorId.STORAGE, intervalMillis = 3_000L)
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L)
        val counts = mutableMapOf<CollectorId, Int>()

        val job = launch {
            scheduler.run(
                collectors = listOf(fast, slow),
                now = { testScheduler.currentTime },
                onResults = { results ->
                    results.forEach { result ->
                        counts[result.id] = (counts[result.id] ?: 0) + 1
                    }
                },
            )
        }
        advanceTimeBy(3_100L)
        runCurrent()
        job.cancel()

        assertEquals(4, counts[CollectorId.BATTERY])
        assertEquals(2, counts[CollectorId.STORAGE])
    }

    @Test
    fun `drops tick when collection exceeds timeout`() = runTest {
        val hanging = object : Collector<Any> {
            override val id: CollectorId = CollectorId.CPU
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): Any {
                delay(10_000L)
                return Any()
            }
        }
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L, tickTimeoutMillis = 500L)
        var callbacks = 0

        val job = launch {
            scheduler.run(
                collectors = listOf(hanging),
                now = { testScheduler.currentTime },
                onResults = { callbacks++ },
            )
        }
        advanceTimeBy(2_100L)
        runCurrent()
        job.cancel()

        assertEquals(0, callbacks)
    }

    @Test
    fun `reports unavailable collectors as skipped without running them`() = runTest {
        val unavailable = object : Collector<Int> {
            override val id: CollectorId = CollectorId.MEMORY
            override val intervalMillis: Long = 1_000L
            override fun isAvailable(): Boolean = false
            override suspend fun collect(): Int = error("unavailable collector must not run")
        }
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L)
        var latestResults: List<CollectorResult>? = null

        val job = launch {
            scheduler.run(
                collectors = listOf(unavailable),
                now = { testScheduler.currentTime },
                onResults = { results -> latestResults = results },
            )
        }
        advanceTimeBy(2_100L)
        runCurrent()
        job.cancel()

        val results = latestResults ?: error("expected skipped result")
        assertEquals(1, results.size)
        assertEquals(CollectorId.MEMORY, results.single().id)
        assertTrue(results.single().skipped)
    }

    @Test
    fun `reports collector exception without killing the loop`() = runTest {
        val failing = object : Collector<Any> {
            override val id: CollectorId = CollectorId.CPU
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): Any = throw IllegalStateException("boom")
        }
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L)
        var captured: List<CollectorResult>? = null

        val job = launch {
            scheduler.run(
                collectors = listOf(failing),
                now = { testScheduler.currentTime },
                onResults = { results -> captured = results },
            )
        }
        advanceTimeBy(100L)
        runCurrent()
        job.cancel()

        val result = captured?.single()
        assertNotNull(result)
        assertNotNull(result!!.error)
    }

    private class FakeCollector(
        override val id: CollectorId,
        override val intervalMillis: Long,
    ) : Collector<Int> {

        override suspend fun collect(): Int = 1
    }
}
