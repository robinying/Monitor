package com.robin.monitor.data.collector

/**
 * 读取电池 sysfs 中变化很慢的字段。
 *
 * 设计容量在进程内只解析一次；循环次数按 TTL 缓存，避免电池采集器每秒都打开
 * `charge_full_design` / `cycle_count` 并在节点缺失时反复抛异常。
 */
internal class BatterySysfsReader(
    private val readLong: (String) -> Long?,
    private val elapsedMillis: () -> Long,
    private val cycleCountTtlMillis: Long = CYCLE_COUNT_TTL_MILLIS,
) {

    private val designCapacityMah: Int? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val microAmpHours = readLong(DESIGN_CAPACITY_PATH)
            ?: readLong(FULL_CAPACITY_PATH)
            ?: return@lazy null
        (microAmpHours / MICRO_AMP_HOURS_PER_MAH).toInt().takeIf { it > 0 }
    }

    private var cycleCount: Int? = null

    private var cycleCountReadAtMillis: Long? = null

    fun designCapacityMah(): Int? = designCapacityMah

    @Synchronized
    fun cycleCount(): Int? {
        val now = elapsedMillis()
        val lastReadAt = cycleCountReadAtMillis
        val expired = lastReadAt == null ||
            now - lastReadAt >= cycleCountTtlMillis ||
            now < lastReadAt
        if (expired) {
            cycleCount = readLong(CYCLE_COUNT_PATH)?.toInt()?.takeIf { it > 0 }
            cycleCountReadAtMillis = now
        }
        return cycleCount
    }

    internal companion object {
        const val DESIGN_CAPACITY_PATH = "/sys/class/power_supply/battery/charge_full_design"
        const val FULL_CAPACITY_PATH = "/sys/class/power_supply/battery/charge_full"
        const val CYCLE_COUNT_PATH = "/sys/class/power_supply/battery/cycle_count"
        const val CYCLE_COUNT_TTL_MILLIS = 60_000L
        private const val MICRO_AMP_HOURS_PER_MAH = 1_000L
    }
}
