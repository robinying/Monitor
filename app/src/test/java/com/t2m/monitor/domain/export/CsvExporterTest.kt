package com.t2m.monitor.domain.export

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.db.MetricBucketRow
import com.t2m.monitor.data.db.MetricDao
import com.t2m.monitor.data.db.MetricSampleEntity
import com.t2m.monitor.data.model.MetricKeys
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV 导出：成功写入、任意失败路径清理半成品、异常返回。
 */
class CsvExporterTest {

    private val logger = RecordingLogger()

    @Test
    fun `writes bom header and rows and marks target complete`() = runTest {
        val target = FakeTarget()
        val exporter = createExporter(rows = listOf(
            MetricSampleEntity(metric = MetricKeys.BATTERY_LEVEL, timestampMillis = 1_000L, value = 80f),
            MetricSampleEntity(metric = MetricKeys.CPU_USAGE, timestampMillis = 1_000L, value = 20f),
            MetricSampleEntity(metric = MetricKeys.BATTERY_LEVEL, timestampMillis = 2_000L, value = 70f),
        ), target = target)

        val result = exporter.export(fromMillis = 0L, toMillis = 10_000L)

        assertTrue(result.isSuccess)
        assertTrue(target.completed)
        assertFalse(target.aborted)
        val expected = buildString {
            append(CsvFormatter.BOM).append("测试表头\n")
            append(CsvFormatter.formatRow(1_000L, mapOf(MetricKeys.BATTERY_LEVEL to 80f, MetricKeys.CPU_USAGE to 20f))).append('\n')
            append(CsvFormatter.formatRow(2_000L, mapOf(MetricKeys.BATTERY_LEVEL to 70f))).append('\n')
        }
        assertEquals(expected, target.output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `aborts target when output stream cannot be opened`() = runTest {
        val target = FakeTarget(openError = IOException("storage full"))
        val exporter = createExporter(rows = listOf(sample()), target = target)

        val result = exporter.export(fromMillis = 0L, toMillis = 10_000L)

        assertTrue(result.isFailure)
        assertTrue(target.aborted)
        assertFalse(target.completed)
        assertTrue(logger.messages.isNotEmpty())
    }

    @Test
    fun `aborts target when completing media store row fails`() = runTest {
        val target = FakeTarget(completeError = SecurityException("permission revoked"))
        val exporter = createExporter(rows = listOf(sample()), target = target)

        val result = exporter.export(fromMillis = 0L, toMillis = 10_000L)

        assertTrue(result.isFailure)
        assertTrue(target.aborted)
        assertFalse(target.completed)
    }

    @Test
    fun `rejects invalid range without touching target`() = runTest {
        val target = FakeTarget()
        val exporter = createExporter(rows = emptyList(), target = target)

        val result = exporter.export(fromMillis = 10L, toMillis = 10L)

        assertTrue(result.isFailure)
        assertFalse(target.aborted)
        assertFalse(target.completed)
    }

    private fun createExporter(
        rows: List<MetricSampleEntity>,
        target: FakeTarget,
    ): CsvExporter = CsvExporter(
        metricDao = FakeMetricDao(rows),
        clock = FixedClock,
        logger = logger,
        targetFactory = ExportTargetFactory { target },
        headerProvider = CsvHeaderProvider { "测试表头" },
    )

    private fun sample(): MetricSampleEntity =
        MetricSampleEntity(metric = MetricKeys.BATTERY_LEVEL, timestampMillis = 1_000L, value = 80f)

    private class FakeTarget(
        private val openError: IOException? = null,
        private val completeError: Exception? = null,
    ) : ExportTarget {
        val output = ByteArrayOutputStream()
        var completed = false
        var aborted = false

        override val uri: String = "content://test/export.csv"

        override fun openStream(): OutputStream {
            openError?.let { throw it }
            return output
        }

        override fun markComplete() {
            completeError?.let { throw it }
            completed = true
        }

        override fun abort() {
            aborted = true
        }
    }

    private class FakeMetricDao(
        private val rows: List<MetricSampleEntity>,
    ) : MetricDao {
        override suspend fun insertAll(samples: List<MetricSampleEntity>) = Unit
        override suspend fun queryBuckets(
            metric: String,
            fromMillis: Long,
            toMillis: Long,
            bucketMillis: Long,
        ): List<MetricBucketRow> = error("not used")

        override suspend fun queryRange(fromMillis: Long, toMillis: Long): List<MetricSampleEntity> =
            rows.filter { row -> row.timestampMillis >= fromMillis && row.timestampMillis < toMillis }

        override suspend fun countSamples(): Long = rows.size.toLong()
        override suspend fun oldestTimestampMillis(): Long? = rows.minOfOrNull { it.timestampMillis }
        override suspend fun latestTimestampMillis(): Long? = rows.maxOfOrNull { it.timestampMillis }
        override suspend fun deleteSamplesBefore(cutoffMillis: Long): Int = 0
        override suspend fun clearSamples(): Int = 0
        override suspend fun clearHourly(): Int = 0
        override suspend fun deleteHourlyBefore(cutoffMillis: Long): Int = 0
        override suspend fun rollupHourly(cutoffMillis: Long): Long = 0L
    }

    private object FixedClock : MonitorClock {
        override fun elapsedRealtimeMillis(): Long = 1_700_000_000_000L
        override fun currentTimeMillis(): Long = 1_700_000_000_000L
    }

    private class RecordingLogger : MonitorLogger {
        val messages = mutableListOf<String>()
        override fun warn(message: String, throwable: Throwable?) {
            messages += message
        }
    }
}
