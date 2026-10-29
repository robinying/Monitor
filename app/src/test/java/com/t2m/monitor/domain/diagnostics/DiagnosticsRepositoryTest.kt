package com.t2m.monitor.domain.diagnostics

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诊断统计：成功率、连续失败、错误环形缓冲与重置。
 */
class DiagnosticsRepositoryTest {

    private val clock = FakeClock()

    private val repository = DiagnosticsRepository(clock)

    @Test
    fun `records success and failure counts`() {
        repository.record(CollectorResult(CollectorId.CPU, value = 42f, durationMillis = 5L))
        repository.record(CollectorResult(CollectorId.CPU, value = 43f, durationMillis = 7L))
        repository.record(
            CollectorResult(CollectorId.CPU, value = null, error = IllegalStateException("boom"), durationMillis = 9L),
        )

        val diagnostic = repository.diagnostics.value.single()
        assertEquals(2L, diagnostic.successCount)
        assertEquals(1L, diagnostic.failureCount)
        assertEquals(1, diagnostic.consecutiveFailures)
        assertEquals(9L, diagnostic.lastDurationMillis)
        assertEquals("boom", diagnostic.lastError)
        assertEquals(66.6f, diagnostic.successRatePercent!!, 0.1f)
    }

    @Test
    fun `null value counts as failure and resets consecutive counter on success`() {
        repository.record(CollectorResult(CollectorId.MEMORY, value = null))
        repository.record(CollectorResult(CollectorId.MEMORY, value = null))
        assertEquals(2, repository.diagnostics.value.single().consecutiveFailures)

        repository.record(CollectorResult(CollectorId.MEMORY, value = "ok"))
        val diagnostic = repository.diagnostics.value.single()
        assertEquals(0, diagnostic.consecutiveFailures)
        assertNull(diagnostic.lastError)
        assertEquals(1L, diagnostic.successCount)
    }

    @Test
    fun `error buffer keeps newest entries and caps size`() {
        repeat(25) { index ->
            clock.now = 1_000L + index
            repository.record(
                CollectorResult(CollectorId.STORAGE, value = null, error = IllegalStateException("e$index")),
            )
        }
        val errors = repository.recentErrors.value
        assertEquals(20, errors.size)
        assertEquals("e24", errors.first().message)
        assertEquals("e5", errors.last().message)
    }

    @Test
    fun `reset clears all statistics`() {
        repository.record(CollectorResult(CollectorId.BATTERY, value = 88))
        repository.recordError("export failed")
        repository.reset()

        assertTrue(repository.diagnostics.value.isEmpty())
        assertTrue(repository.recentErrors.value.isEmpty())
    }

    private class FakeClock(var now: Long = 1_000L) : MonitorClock {
        override fun elapsedRealtimeMillis(): Long = now

        override fun currentTimeMillis(): Long = now
    }
}
