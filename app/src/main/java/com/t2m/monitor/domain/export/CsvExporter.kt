package com.t2m.monitor.domain.export

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.db.MetricDao
import java.io.BufferedWriter
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CSV 导出器。
 *
 * 数据按 6 小时分段查询，避免一次性把数天样本载入内存；任何失败路径都会调用
 * [ExportTarget.abort] 清理半成品。
 */
@Singleton
class CsvExporter @Inject constructor(
    private val metricDao: MetricDao,
    private val clock: MonitorClock,
    private val logger: MonitorLogger,
    private val targetFactory: ExportTargetFactory,
    private val headerProvider: CsvHeaderProvider,
) {

    suspend fun export(fromMillis: Long, toMillis: Long): Result<ExportFile> =
        withContext(Dispatchers.IO) {
            var target: ExportTarget? = null
            try {
                require(toMillis > fromMillis) { "invalid export range" }
                val fileName = buildFileName()
                val createdTarget = targetFactory.create(fileName)
                target = createdTarget
                createdTarget.openStream().bufferedWriter().use { writer ->
                    writeCsv(writer, headerProvider.header(), fromMillis, toMillis)
                }
                createdTarget.markComplete()
                Result.success(ExportFile(uri = createdTarget.uri, displayName = fileName))
            } catch (cancellation: CancellationException) {
                target?.abort()
                throw cancellation
            } catch (error: Throwable) {
                target?.abort()
                logger.warn("export csv failed", error)
                Result.failure(error)
            }
        }

    private suspend fun writeCsv(
        writer: BufferedWriter,
        header: String,
        fromMillis: Long,
        toMillis: Long,
    ) {
        writer.write(CsvFormatter.BOM)
        writer.write(header)
        writer.newLine()
        var chunkStart = fromMillis
        while (chunkStart < toMillis) {
            val chunkEnd = minOf(chunkStart + CHUNK_MILLIS, toMillis)
            writeChunk(writer, chunkStart, chunkEnd)
            chunkStart = chunkEnd
        }
    }

    private suspend fun writeChunk(writer: BufferedWriter, fromMillis: Long, toMillis: Long) {
        val rows = metricDao.queryRange(fromMillis, toMillis)
        if (rows.isEmpty()) {
            return
        }
        var currentTimestamp = Long.MIN_VALUE
        val values = HashMap<String, Float>()
        for (row in rows) {
            if (row.timestampMillis != currentTimestamp) {
                if (currentTimestamp != Long.MIN_VALUE) {
                    writeRow(writer, currentTimestamp, values)
                }
                currentTimestamp = row.timestampMillis
                values.clear()
            }
            values[row.metric] = row.value
        }
        if (currentTimestamp != Long.MIN_VALUE) {
            writeRow(writer, currentTimestamp, values)
        }
    }

    private fun writeRow(writer: BufferedWriter, timestampMillis: Long, values: Map<String, Float>) {
        writer.write(CsvFormatter.formatRow(timestampMillis, values))
        writer.newLine()
    }

    private fun buildFileName(): String {
        val timestamp = FILE_NAME_FORMATTER.format(Instant.ofEpochMilli(clock.currentTimeMillis()))
        return "monitor_$timestamp.csv"
    }

    private companion object {
        const val CHUNK_MILLIS = 6L * 60 * 60 * 1000
        val FILE_NAME_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
    }
}
