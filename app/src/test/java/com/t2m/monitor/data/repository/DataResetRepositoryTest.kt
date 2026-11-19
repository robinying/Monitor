package com.t2m.monitor.data.repository

import com.t2m.monitor.data.db.AlertDao
import com.t2m.monitor.data.db.AlertEventEntity
import com.t2m.monitor.data.db.TestMetricDao
import com.t2m.monitor.data.db.TransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 一键清空：三张表在同一事务内删除。
 */
class DataResetRepositoryTest {

    private val dao = TestMetricDao()

    private val alertDao = RecordingAlertDao()

    private val transactionRunner = RecordingTransactionRunner()

    private val repository = DataResetRepository(dao, alertDao, transactionRunner)

    @Test
    fun `clears samples hourly aggregates and alerts in one transaction`() = runTest {
        repository.clearAll()

        assertEquals(1, transactionRunner.calls)
        assertEquals(1, dao.clearSamplesCount)
        assertEquals(1, dao.clearHourlyCount)
        assertEquals(1, alertDao.clears)
    }

    private class RecordingAlertDao : AlertDao {
        var clears = 0

        override suspend fun insert(event: AlertEventEntity): Long = 0L

        override suspend fun recent(limit: Int): List<AlertEventEntity> = emptyList()

        override suspend fun clear(): Int {
            clears++
            return 0
        }
    }

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0

        override suspend fun <T> run(block: suspend () -> T): T {
            calls++
            return block()
        }
    }
}
