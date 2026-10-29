package com.t2m.monitor.domain

import com.t2m.monitor.data.model.MetricAggregate
import com.t2m.monitor.data.model.MetricKeys
import com.t2m.monitor.data.model.MonitorSnapshot

/**
 * 10 秒写入窗口聚合器。
 *
 * 内存中按指标缓存采样值，窗口结束取平均写入数据库，降低 IO 频率。
 * 时间来源通过 lambda 注入，便于用虚拟时间做单元测试。
 */
class MetricAggregator(
    private val elapsedMillis: () -> Long,
) {

    private val buffers = mutableMapOf<String, MutableList<Float>>()

    private var windowStartedAtMillis = elapsedMillis()

    /** 清空缓存并以当前时刻作为新窗口起点。 */
    @Synchronized
    fun reset() {
        buffers.clear()
        windowStartedAtMillis = elapsedMillis()
    }

    @Synchronized
    fun add(snapshot: MonitorSnapshot) {
        snapshot.battery?.percent?.let { append(MetricKeys.BATTERY_LEVEL, it.toFloat()) }
        snapshot.battery?.temperatureC?.let { append(MetricKeys.BATTERY_TEMPERATURE, it.toFloat()) }
        snapshot.cpu?.totalUsagePercent?.let { append(MetricKeys.CPU_USAGE, it) }
        snapshot.memory?.usedPercent?.let { append(MetricKeys.MEMORY_USED_PERCENT, it) }
        snapshot.storage?.usedPercent?.let { append(MetricKeys.STORAGE_USED_PERCENT, it) }
        snapshot.thermal?.maxTemperatureC?.let { append(MetricKeys.THERMAL_MAX, it) }
        snapshot.fps?.let { append(MetricKeys.FPS, it) }
    }

    @Synchronized
    fun shouldFlush(): Boolean = elapsedMillis() - windowStartedAtMillis >= WINDOW_MILLIS

    /**
     * 结束当前窗口，返回各指标平均值。
     *
     * @param timestampMillis 写入数据库的时间戳（窗口结束时刻）
     */
    @Synchronized
    fun flush(timestampMillis: Long): List<MetricAggregate> {
        val aggregates = buffers.mapNotNull { (metric, values) ->
            if (values.isEmpty()) {
                null
            } else {
                MetricAggregate(
                    metric = metric,
                    timestampMillis = timestampMillis,
                    value = values.average().toFloat(),
                )
            }
        }
        reset()
        return aggregates
    }

    private fun append(metric: String, value: Float) {
        buffers.getOrPut(metric) { mutableListOf() }.add(value)
    }

    companion object {
        const val WINDOW_MILLIS = 10_000L
    }
}
