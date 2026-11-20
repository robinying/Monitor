package com.robin.monitor.data.repository

import com.robin.monitor.data.db.MetricStatsRow
import com.robin.monitor.data.db.TestMetricDao
import com.robin.monitor.data.db.TransactionRunner
import com.robin.monitor.data.model.MetricKeys
import com.robin.monitor.data.model.MetricStats
import com.robin.monitor.data.model.RetentionPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 时序仓库：保留策略事务与整点对齐、原始样本统计查询。
 */
class MetricRepositoryTest {

    private val dao = TestMetricDao()

    private val transactionRunner = RecordingTransactionRunner()

    private val repository = MetricRepository(dao, transactionRunner)

    @Test
    fun `prune rolls up and deletes inside one transaction with aligned cutoff`() = runTest {
        val nowMillis = 100L * DAY_MILLIS + 30L * MINUTE_MILLIS

        repository.prune(nowMillis)

        val expectedRawCutoff = RetentionPolicy.rawCutoff(nowMillis)
        assertEquals((expectedRawCutoff / HOUR_MILLIS) * HOUR_MILLIS, expectedRawCutoff)
        assertEquals(listOf(expectedRawCutoff), dao.rollupCutoffs)
        assertEquals(listOf(expectedRawCutoff), dao.deletedSampleCutoffs)
        assertEquals(listOf(RetentionPolicy.hourlyCutoff(nowMillis)), dao.deletedHourlyCutoffs)
        assertEquals(1, transactionRunner.calls)
    }

    @Test
    fun `statistics returns raw sample aggregates`() = runTest {
        dao.statisticsRow = MetricStatsRow(minValue = 10f, maxValue = 90f, avgValue = 40f)

        val stats = repository.statistics(MetricKeys.CPU_USAGE, 0L, 1_000L)

        assertEquals(MetricStats(minValue = 10f, maxValue = 90f, avgValue = 40f), stats)
    }

    @Test
    fun `statistics returns empty values when the range has no samples`() = runTest {
        dao.statisticsRow = MetricStatsRow(null, null, null)

        assertEquals(MetricStats(null, null, null), repository.statistics(MetricKeys.CPU_USAGE, 0L, 1L))
    }

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0

        override suspend fun <T> run(block: suspend () -> T): T {
            calls++
            return block()
        }
    }

    private companion object {
        const val MINUTE_MILLIS = 60L * 1000
        const val HOUR_MILLIS = 60L * MINUTE_MILLIS
        const val DAY_MILLIS = 24L * HOUR_MILLIS
    }
}
