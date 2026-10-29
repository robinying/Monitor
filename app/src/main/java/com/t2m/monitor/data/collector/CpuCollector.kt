package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.CpuSnapshot
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CPU 采集器：整机与每核使用率、每核当前频率。
 *
 * 使用率依赖相邻两次采样的时间片差值，首次采样只建立基线，使用率为 null。
 */
class CpuCollector @Inject constructor(
    private val source: CpuStatSource,
) : Collector<CpuSnapshot> {

    override val id: CollectorId = CollectorId.CPU

    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    private var previousTotal: CpuTimes? = null

    private var previousCores: List<CpuTimes?> = emptyList()

    private var cachedMaxFrequencyKHz: Int? = null

    override suspend fun collect(): CpuSnapshot? = withContext(Dispatchers.IO) {
        val parsed = source.readStatLines()?.let(ProcStatParser::parse)

        // 核心可能临时下线：按历史最大核数与系统可见核数对齐，缺失位置保持 null。
        // Android 17 起部分机型限制 /proc/stat，此时仍可读取 /sys 频率并展示核数。
        // /proc/stat 可读时以解析到的核数为准；完全不可读时才回退到系统可见核数。
        val fallbackCoreCount = if (parsed == null || parsed.perCore.isEmpty()) {
            Runtime.getRuntime().availableProcessors()
        } else {
            0
        }
        val coreCount = maxOf(
            parsed?.perCore?.size ?: 0,
            previousCores.size,
            fallbackCoreCount,
        )
        if (coreCount <= 0) {
            return@withContext null
        }

        val perCoreUsage = (0 until coreCount).map { index ->
            val times = parsed?.perCore?.getOrNull(index)
            if (times == null) {
                null
            } else {
                CpuUsageCalculator.usageBetween(previousCores.getOrNull(index), times)
            }
        }
        val totalUsage = parsed?.total?.let { total ->
            CpuUsageCalculator.usageBetween(previousTotal, total).also { previousTotal = total }
        }
        previousCores = parsed?.perCore.orEmpty()

        val frequencies = (0 until coreCount).map { coreIndex -> source.readFrequencyKHz(coreIndex) }
        if (cachedMaxFrequencyKHz == null) {
            cachedMaxFrequencyKHz = (0 until coreCount)
                .mapNotNull { coreIndex -> source.readMaxFrequencyKHz(coreIndex) }
                .maxOrNull()
        }

        CpuSnapshot(
            totalUsagePercent = totalUsage,
            perCoreUsagePercent = perCoreUsage,
            perCoreFrequencyKHz = frequencies,
            maxFrequencyKHz = cachedMaxFrequencyKHz,
        )
    }

    /** CPU 采集器始终可用：读取失败时退化为频率/核数展示。 */
    override fun isAvailable(): Boolean = true

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
    }
}
