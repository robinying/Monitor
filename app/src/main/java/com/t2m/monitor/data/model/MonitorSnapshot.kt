package com.t2m.monitor.data.model

/**
 * 一次采样的聚合结果。
 *
 * 各字段为 null 表示对应采集器本轮失败或设备不支持，展示层统一显示 "--"。
 */
data class MonitorSnapshot(
    val timestampMillis: Long = 0L,
    val battery: BatterySnapshot? = null,
    val cpu: CpuSnapshot? = null,
    val memory: MemorySnapshot? = null,
    val storage: StorageSnapshot? = null,
    val thermal: ThermalSnapshot? = null,
    val usage: UsageSnapshot? = null,
    val fps: Float? = null,
    val appStorage: List<AppStorageUsage>? = null,
) {
    companion object {
        /** 空快照：采样尚未产生任何数据时的初始值。 */
        val EMPTY = MonitorSnapshot()
    }
}

/** 电池状态。 */
enum class BatteryStatus {
    UNKNOWN,
    CHARGING,
    DISCHARGING,
    NOT_CHARGING,
    FULL,
}

/** 电源接入方式。 */
enum class PlugState {
    NONE,
    AC,
    USB,
    WIRELESS,
    DOCK,
}

/**
 * 电池指标。
 *
 * @param percent 电量百分比 0..100，无法读取时为 null。
 * @param temperatureC 电池温度（摄氏度）。
 * @param currentMicroAmps 瞬时电流（微安），放电为负；设备不支持时为 null。
 * @param voltageMv 电池电压（毫伏）。
 */
data class BatterySnapshot(
    val percent: Int?,
    val temperatureC: Double?,
    val currentMicroAmps: Int?,
    val voltageMv: Int?,
    val status: BatteryStatus,
    val plugged: PlugState,
    val health: Int?,
)

/**
 * CPU 指标。
 *
 * @param totalUsagePercent 相邻两次采样的整机 CPU 使用率；首次采样无基线时为 null。
 * @param perCoreUsagePercent 每核使用率，顺序与 [CpuSnapshot.perCoreFrequencyKHz] 一致。
 * @param perCoreFrequencyKHz 每核当前频率；读取不到的位置为 null。
 */
data class CpuSnapshot(
    val totalUsagePercent: Float?,
    val perCoreUsagePercent: List<Float?>,
    val perCoreFrequencyKHz: List<Int?>,
    val maxFrequencyKHz: Int?,
) {
    val coreCount: Int
        get() = perCoreFrequencyKHz.size.coerceAtLeast(perCoreUsagePercent.size)
}

/**
 * 内存指标。
 *
 * @param totalBytes 物理内存总量。
 * @param availBytes 当前可用内存。
 * @param lowMemory 系统是否处于低内存状态。
 */
data class MemorySnapshot(
    val totalBytes: Long,
    val availBytes: Long,
    val lowMemory: Boolean,
) {
    val usedBytes: Long
        get() = (totalBytes - availBytes).coerceAtLeast(0L)

    val usedPercent: Float
        get() = if (totalBytes <= 0L) {
            0f
        } else {
            (usedBytes.toDouble() / totalBytes * 100.0).toFloat().coerceIn(0f, 100f)
        }
}

/**
 * 温区读数。
 *
 * @param name `thermal_zone` 的 type 字段，如 `cpu-0-0`、`battery`
 * @param temperatureC 已归一化为摄氏度
 */
data class ThermalZone(
    val name: String,
    val temperatureC: Float,
)

/**
 * 设备温度指标。
 *
 * @param maxTemperatureC 所有有效温区中的最高温度
 * @param hottestZone 最高温度对应的温区名
 * @param zones 全部有效温区，按温度降序
 */
data class ThermalSnapshot(
    val maxTemperatureC: Float,
    val hottestZone: String,
    val zones: List<ThermalZone>,
)

/**
 * 使用情况访问相关指标（需要 PACKAGE_USAGE_STATS 授权）。
 *
 * @param foregroundPackage 最近进入前台的包名，未知时为 null
 * @param foregroundAppLabel 应用名；受包可见性限制取不到时回退为包名
 * @param todayMobileRxBytes 今日移动网络下行字节数
 * @param todayMobileTxBytes 今日移动网络上行字节数
 * @param todayWifiRxBytes 今日 Wi-Fi 下行字节数
 * @param todayWifiTxBytes 今日 Wi-Fi 上行字节数
 */
data class UsageSnapshot(
    val foregroundPackage: String?,
    val foregroundAppLabel: String?,
    val todayMobileRxBytes: Long?,
    val todayMobileTxBytes: Long?,
    val todayWifiRxBytes: Long?,
    val todayWifiTxBytes: Long?,
) {
    /** 今日下行总量；两个来源都不可用时为 null。 */
    val todayRxBytes: Long?
        get() = listOfNotNull(todayMobileRxBytes, todayWifiRxBytes)
            .takeIf { values -> values.isNotEmpty() }
            ?.sum()

    /** 今日上行总量；两个来源都不可用时为 null。 */
    val todayTxBytes: Long?
        get() = listOfNotNull(todayMobileTxBytes, todayWifiTxBytes)
            .takeIf { values -> values.isNotEmpty() }
            ?.sum()
}

/**
 * 单个应用的存储占用。
 *
 * @param label 应用名；受包可见性限制取不到时为 null
 */
data class AppStorageUsage(
    val packageName: String,
    val label: String?,
    val totalBytes: Long,
)

/**
 * 存储指标（应用数据分区）。
 *
 * @param totalBytes 分区总量。
 * @param availableBytes 剩余可用空间。
 */
data class StorageSnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long
        get() = (totalBytes - availableBytes).coerceAtLeast(0L)

    val usedPercent: Float
        get() = if (totalBytes <= 0L) {
            0f
        } else {
            (usedBytes.toDouble() / totalBytes * 100.0).toFloat().coerceIn(0f, 100f)
        }
}
