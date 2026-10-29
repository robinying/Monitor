package com.t2m.monitor.data.collector

/**
 * 根据前后两次累计时间片计算 CPU 使用率。
 */
object CpuUsageCalculator {

    /**
     * @param previous 上一次采样；为 null 时无法计算（首次采样）
     * @param current 本次采样
     * @return 0..100 的使用率；计数器回绕或数据无效时返回 null
     */
    fun usageBetween(previous: CpuTimes?, current: CpuTimes): Float? {
        if (previous == null) {
            return null
        }
        val totalDelta = current.totalJiffies - previous.totalJiffies
        if (totalDelta <= 0L) {
            return null
        }
        val idleDelta = (current.idleJiffies - previous.idleJiffies).coerceIn(0L, totalDelta)
        val busyDelta = totalDelta - idleDelta
        return (busyDelta.toDouble() / totalDelta * 100.0).toFloat().coerceIn(0f, 100f)
    }
}
