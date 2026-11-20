package com.t2m.monitor.domain

import com.t2m.monitor.data.collector.Collector
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorResult
import com.t2m.monitor.data.collector.MetricStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `keeps fast results when a slow collector times out`() = runTest {
        val fast = FakeCollector(CollectorId.BATTERY, intervalMillis = 1_000L)
        val slow = object : Collector<Any> {
            override val id: CollectorId = CollectorId.APP_STORAGE
            override val intervalMillis: Long = 60_000L
            override val timeoutMillis: Long = 1_000L
            override suspend fun collect(): Any {
                delay(10_000L)
                return Any()
            }
        }
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L)
        val byId = mutableMapOf<CollectorId, MutableList<CollectorResult>>()

        val job = launch {
            scheduler.run(
                collectors = listOf(fast, slow),
                now = { testScheduler.currentTime },
                onResults = { results ->
                    results.forEach { result ->
                        byId.getOrPut(result.id) { mutableListOf() } += result
                    }
                },
            )
        }
        advanceTimeBy(2_100L)
        runCurrent()
        job.cancel()

        val batteryResults = byId[CollectorId.BATTERY].orEmpty()
        assertTrue("expected repeated fast samples", batteryResults.size >= 2)
        assertTrue(batteryResults.all { result -> result.error == null && result.value != null })
        val slowResults = byId[CollectorId.APP_STORAGE].orEmpty()
        assertTrue(slowResults.isNotEmpty())
        assertTrue(slowResults.all { result -> result.error is CollectorTimeoutException })
        assertTrue(slowResults.all { result -> result.status == MetricStatus.TIMEOUT })
    }

    @Test
    fun `publishes skipped results even when another collector times out`() = runTest {
        val fast = FakeCollector(CollectorId.BATTERY, intervalMillis = 1_000L)
        val slow = object : Collector<Any> {
            override val id: CollectorId = CollectorId.APP_STORAGE
            override val intervalMillis: Long = 60_000L
            override val timeoutMillis: Long = 1_000L
            override suspend fun collect(): Any {
                delay(10_000L)
                return Any()
            }
        }
        val unavailable = object : Collector<Int> {
            override val id: CollectorId = CollectorId.USAGE
            override val intervalMillis: Long = 5_000L
            override fun isAvailable(): Boolean = false
            override suspend fun collect(): Int = error("must not run")
        }
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L)
        val callbacks = mutableListOf<List<CollectorResult>>()

        val job = launch {
            scheduler.run(
                collectors = listOf(fast, slow, unavailable),
                now = { testScheduler.currentTime },
                onResults = { results -> callbacks += results },
            )
        }
        advanceTimeBy(1_100L)
        runCurrent()
        job.cancel()

        val skipped = callbacks.flatten().first { result -> result.id == CollectorId.USAGE }
        assertTrue(skipped.skipped)
        assertEquals(MetricStatus.SKIPPED, skipped.status)
        assertTrue(callbacks.flatten().any { result -> result.id == CollectorId.BATTERY && result.value != null })
    }

    @Test
    fun `does not retry a timed out collector before its interval`() = runTest {
        val slow = object : Collector<Any> {
            override val id: CollectorId = CollectorId.APP_STORAGE
            override val intervalMillis: Long = 5_000L
            override val timeoutMillis: Long = 1_000L
            override suspend fun collect(): Any {
                delay(10_000L)
                return Any()
            }
        }
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L)
        var attempts = 0

        val job = launch {
            scheduler.run(
                collectors = listOf(slow),
                now = { testScheduler.currentTime },
                onResults = { results ->
                    attempts += results.count { result -> result.id == CollectorId.APP_STORAGE }
                },
            )
        }
        advanceTimeBy(6_200L)
        runCurrent()
        job.cancel()

        assertEquals(2, attempts)
    }

    @Test
    fun `reports empty values with an empty status`() = runTest {
        val empty = object : Collector<Any> {
            override val id: CollectorId = CollectorId.STORAGE
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): Any? = null
        }
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L)
        var captured: CollectorResult? = null

        val job = launch {
            scheduler.run(
                collectors = listOf(empty),
                now = { testScheduler.currentTime },
                onResults = { results -> captured = results.single() },
            )
        }
        advanceTimeBy(100L)
        runCurrent()
        job.cancel()

        assertEquals(MetricStatus.EMPTY, captured?.status)
    }

    @Test
    fun `marks an unsupported successful value as an error`() = runTest {
        val wrongType = object : Collector<Any> {
            override val id: CollectorId = CollectorId.BATTERY
            override val intervalMillis: Long = 1_000L
            override suspend fun collect(): Any = "not a battery snapshot"
        }
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L)
        var captured: CollectorResult? = null

        val job = launch {
            scheduler.run(
                collectors = listOf(wrongType),
                now = { testScheduler.currentTime },
                onResults = { results -> captured = results.single() },
            )
        }
        advanceTimeBy(100L)
        runCurrent()
        job.cancel()

        assertEquals(MetricStatus.ERROR, captured?.status)
        assertNull(captured?.metric)
    }

    @Test
    fun `uses the collector privilege reason for unavailable capabilities`() = runTest {
        val unavailable = object : Collector<Int> {
            override val id: CollectorId = CollectorId.USAGE
            override val intervalMillis: Long = 1_000L
            override fun isAvailable(): Boolean = false
            override fun unavailableStatus(): MetricStatus = MetricStatus.PRIVILEGE_DENIED
            override suspend fun collect(): Int = error("must not run")
        }
        val scheduler = SamplingScheduler(tickIntervalMillis = 1_000L)
        var captured: CollectorResult? = null

        val job = launch {
            scheduler.run(
                collectors = listOf(unavailable),
                now = { testScheduler.currentTime },
                onResults = { results -> captured = results.single() },
            )
        }
        advanceTimeBy(100L)
        runCurrent()
        job.cancel()

        assertEquals(MetricStatus.PRIVILEGE_DENIED, captured?.status)
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
        assertEquals(MetricStatus.ERROR, result.status)
    }

    private class FakeCollector(
        override val id: CollectorId,
        override val intervalMillis: Long,
    ) : Collector<Int> {

        override suspend fun collect(): Int = 1
    }
}
