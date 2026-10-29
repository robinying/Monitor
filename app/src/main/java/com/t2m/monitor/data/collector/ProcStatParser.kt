package com.t2m.monitor.data.collector

/**
 * 一次 `/proc/stat` 采样中的 CPU 时间片统计（单位：jiffies）。
 *
 * @param totalJiffies 累计总时间片（不含 guest/guest_nice，避免重复计数）。
 * @param idleJiffies 空闲时间片，含 iowait。
 */
data class CpuTimes(
    val totalJiffies: Long,
    val idleJiffies: Long,
)

/**
 * `/proc/stat` 解析结果。
 *
 * @param total 整机汇总行（`cpu`）数据，缺失时为 null。
 * @param perCore 每核数据，下标即核心编号；离线核心对应位置为 null。
 */
data class ParsedProcStat(
    val total: CpuTimes?,
    val perCore: List<CpuTimes?>,
)

/**
 * `/proc/stat` 纯文本解析器。
 *
 * 行格式：`cpu  user nice system idle iowait irq softirq steal guest guest_nice`。
 * 只统计前 8 个字段；字段缺失或非法时不抛异常，按无效行跳过。
 */
object ProcStatParser {

    private val WHITESPACE = Regex("\\s+")
    private const val TOTAL_LABEL = "cpu"
    private const val CORE_LABEL_PREFIX = "cpu"
    private const val MIN_FIELD_COUNT = 4
    private const val MAX_COUNTED_FIELDS = 8

    fun parse(lines: List<String>): ParsedProcStat? {
        var total: CpuTimes? = null
        val perCore = mutableListOf<CpuTimes?>()

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith(CORE_LABEL_PREFIX)) {
                continue
            }
            val parts = trimmed.split(WHITESPACE)
            if (parts.size < MIN_FIELD_COUNT + 1) {
                continue
            }
            val times = parseTimes(parts) ?: continue
            if (parts[0] == TOTAL_LABEL) {
                total = times
                continue
            }
            val coreIndex = parts[0].removePrefix(CORE_LABEL_PREFIX).toIntOrNull() ?: continue
            if (coreIndex < 0) {
                continue
            }
            while (perCore.size <= coreIndex) {
                perCore.add(null)
            }
            perCore[coreIndex] = times
        }

        if (total == null && perCore.isEmpty()) {
            return null
        }
        return ParsedProcStat(total = total, perCore = perCore)
    }

    private fun parseTimes(parts: List<String>): CpuTimes? {
        val countedFields = parts.drop(1).take(MAX_COUNTED_FIELDS)
        val values = countedFields.map { it.toLongOrNull() ?: return null }
        if (values.any { it < 0L }) {
            return null
        }
        val idle = values.getOrNull(IDLE_INDEX) ?: return null
        val iowait = if (values.size > IOWAIT_INDEX) values[IOWAIT_INDEX] else 0L
        return CpuTimes(totalJiffies = values.sum(), idleJiffies = idle + iowait)
    }

    private const val IDLE_INDEX = 3
    private const val IOWAIT_INDEX = 4
}
