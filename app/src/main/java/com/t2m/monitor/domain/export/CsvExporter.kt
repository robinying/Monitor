package com.t2m.monitor.domain.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.t2m.monitor.R
import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.db.MetricDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导出结果。
 *
 * @param uri 可分享/打开的 Content Uri
 * @param displayName 文件名
 */
data class ExportFile(
    val uri: Uri,
    val displayName: String,
)

/**
 * CSV 导出器。
 *
 * Android 10+ 写入系统下载目录（MediaStore，无需存储权限）；
 * Android 8/9 写入应用专属外部目录并通过 FileProvider 分享。
 * 数据按 6 小时分段查询，避免一次性把数天样本载入内存。
 */
@Singleton
class CsvExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val metricDao: MetricDao,
    private val clock: MonitorClock,
    private val logger: MonitorLogger,
) {

    suspend fun export(fromMillis: Long, toMillis: Long): Result<ExportFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(toMillis > fromMillis) { "invalid export range" }
                val fileName = buildFileName()
                val target = createTarget(fileName)
                try {
                    writeCsv(target.uri, fromMillis, toMillis)
                    target.pendingUri?.let { pendingUri ->
                        context.contentResolver.update(
                            pendingUri,
                            ContentValues().apply {
                                put(MediaStore.Downloads.IS_PENDING, 0)
                            },
                            null,
                            null,
                        )
                    }
                    ExportFile(uri = target.uri, displayName = fileName)
                } catch (ioError: IOException) {
                    // 写入失败时删除半成品，避免下载目录出现空文件。
                    target.pendingUri?.let { pendingUri ->
                        runCatching { context.contentResolver.delete(pendingUri, null, null) }
                    }
                    throw ioError
                }
            }.onFailure { error -> logger.warn("export csv failed", error) }
        }

    private suspend fun writeCsv(uri: Uri, fromMillis: Long, toMillis: Long) {
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("open export stream failed")
        outputStream.bufferedWriter().use { writer ->
            writer.write(CsvFormatter.BOM)
            writer.write(context.getString(R.string.csv_header))
            writer.newLine()
            var chunkStart = fromMillis
            while (chunkStart < toMillis) {
                val chunkEnd = minOf(chunkStart + CHUNK_MILLIS, toMillis)
                writeChunk(writer, chunkStart, chunkEnd)
                chunkStart = chunkEnd
            }
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

    private fun createTarget(fileName: String): ExportTarget {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE_CSV)
                put(MediaStore.Downloads.RELATIVE_PATH, "$EXPORT_DIRECTORY/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            ) ?: throw IOException("insert download failed")
            return ExportTarget(uri = uri, pendingUri = uri)
        }

        val baseDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        val directory = File(baseDirectory, LEGACY_SUBDIRECTORY).apply { mkdirs() }
        val file = File(directory, fileName)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return ExportTarget(uri = uri, pendingUri = null)
    }

    private fun buildFileName(): String {
        val timestamp = FILE_NAME_FORMATTER.format(Instant.ofEpochMilli(clock.currentTimeMillis()))
        return "monitor_$timestamp.csv"
    }

    private data class ExportTarget(
        val uri: Uri,
        val pendingUri: Uri?,
    )

    private companion object {
        const val MIME_TYPE_CSV = "text/csv"
        const val EXPORT_DIRECTORY = "Download/T2MMonitor"
        const val LEGACY_SUBDIRECTORY = "Download/T2MMonitor"
        const val CHUNK_MILLIS = 6L * 60 * 60 * 1000
        val FILE_NAME_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
    }
}
