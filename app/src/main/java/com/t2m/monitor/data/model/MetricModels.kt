package com.t2m.monitor.data.model

/** 时序指标键，与 Room 表中 `metric` 列一致。 */
object MetricKeys {
    const val BATTERY_LEVEL = "battery.level"
    const val BATTERY_TEMPERATURE = "battery.temperature"
    const val CPU_USAGE = "cpu.usage"
    const val MEMORY_USED_PERCENT = "memory.used_percent"
    const val STORAGE_USED_PERCENT = "storage.used_percent"
    const val THERMAL_MAX = "thermal.max"
    const val FPS = "screen.fps"

    /** CSV 导出列顺序。 */
    val ALL: List<String> = listOf(
        BATTERY_LEVEL,
        BATTERY_TEMPERATURE,
        CPU_USAGE,
        MEMORY_USED_PERCENT,
        STORAGE_USED_PERCENT,
        THERMAL_MAX,
    )
}

/**
 * 历史曲线上的一个数据点。
 *
 * @param timestampMillis 时间桶起点（毫秒）
 * @param value 该桶内的平均值
 */
data class MetricPoint(
    val timestampMillis: Long,
    val value: Float,
)

/** 一次聚合写入的数据（10 秒窗口的平均值）。 */
data class MetricAggregate(
    val metric: String,
    val timestampMillis: Long,
    val value: Float,
)

/** 历史页时间范围与查询粒度。 */
enum class HistoryRange(
    val durationMillis: Long,
    val bucketMillis: Long,
) {
    ONE_HOUR(durationMillis = 60L * 60 * 1000, bucketMillis = 60_000L),
    SIX_HOURS(durationMillis = 6L * 60 * 60 * 1000, bucketMillis = 5L * 60 * 1000),
    ONE_DAY(durationMillis = 24L * 60 * 60 * 1000, bucketMillis = 15L * 60 * 1000),
    SEVEN_DAYS(durationMillis = 7L * 24 * 60 * 60 * 1000, bucketMillis = 60L * 60 * 1000),
}

/** 历史页可查看的指标。 */
enum class MetricType(val key: String) {
    CPU(MetricKeys.CPU_USAGE),
    MEMORY(MetricKeys.MEMORY_USED_PERCENT),
    BATTERY_LEVEL(MetricKeys.BATTERY_LEVEL),
    BATTERY_TEMPERATURE(MetricKeys.BATTERY_TEMPERATURE),
    STORAGE(MetricKeys.STORAGE_USED_PERCENT),
    DEVICE_TEMPERATURE(MetricKeys.THERMAL_MAX),
    FPS(MetricKeys.FPS),
}

/** 数据保留策略。 */
object RetentionPolicy {
    const val RAW_RETENTION_DAYS = 7L
    const val HOURLY_RETENTION_DAYS = 90L

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** 原始样本的清理截止时间（早于该时间的样本先聚合再删除）。 */
    fun rawCutoff(nowMillis: Long): Long = nowMillis - RAW_RETENTION_DAYS * DAY_MILLIS

    /** 小时聚合的清理截止时间。 */
    fun hourlyCutoff(nowMillis: Long): Long = nowMillis - HOURLY_RETENTION_DAYS * DAY_MILLIS
}
