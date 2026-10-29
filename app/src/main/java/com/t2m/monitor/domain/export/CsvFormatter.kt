package com.t2m.monitor.domain.export

import com.t2m.monitor.data.model.MetricKeys
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV 行格式化。
 *
 * 固定列顺序（时间 + MetricKeys.ALL），数值列不含逗号，无需转义。
 */
object CsvFormatter {

    /** UTF-8 BOM，保证 Excel 正确识别中文表头。 */
    const val BOM = "\uFEFF"

    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun formatTimestamp(timestampMillis: Long): String =
        TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMillis))

    /**
     * @param values 指标键到数值的映射；缺失指标输出空单元格
     */
    fun formatRow(timestampMillis: Long, values: Map<String, Float>): String {
        val cells = MetricKeys.ALL.map { key ->
            values[key]?.let { value -> formatValue(key, value) } ?: ""
        }
        return (listOf(formatTimestamp(timestampMillis)) + cells).joinToString(",")
    }

    private fun formatValue(metric: String, value: Float): String = when (metric) {
        MetricKeys.BATTERY_TEMPERATURE, MetricKeys.THERMAL_MAX -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%.0f", value)
    }
}
