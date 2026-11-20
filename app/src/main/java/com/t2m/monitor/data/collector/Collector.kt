package com.t2m.monitor.data.collector

/**
 * 采集器标识，与 [com.t2m.monitor.data.model.MonitorSnapshot] 中的字段一一对应。
 */
enum class CollectorId {
    BATTERY,
    CPU,
    MEMORY,
    STORAGE,
    THERMAL,
    USAGE,
    FPS,
    APP_STORAGE,
}

/**
 * 指标采集器。
 *
 * 约定：
 * 1. 只负责"读取 + 归一化"，不做业务判断；
 * 2. 设备不支持或本轮不可用时返回 null，不要抛异常；
 * 3. 由 [com.t2m.monitor.domain.SamplingScheduler] 按 [intervalMillis] 调度执行。
 */
interface Collector<T : Any> {

    val id: CollectorId

    /** 建议采样周期（毫秒）。 */
    val intervalMillis: Long

    /** 单次采集超时预算（毫秒）；慢采集器可覆盖，默认 [DEFAULT_COLLECT_TIMEOUT_MILLIS]。 */
    val timeoutMillis: Long
        get() = DEFAULT_COLLECT_TIMEOUT_MILLIS

    /**
     * 能力是否可用（如 Shizuku 授权、使用情况访问）。
     *
     * 不可用时调度器直接跳过，不产生结果、不记失败，界面按无数据降级。
     */
    fun isAvailable(): Boolean = true

    /** 执行一次采集；返回 null 表示本轮无有效数据。 */
    suspend fun collect(): T?

    /** 能力不可用时的原因；默认“不可用”，需要特权的采集器应覆盖。 */
    fun unavailableStatus(): MetricStatus = MetricStatus.SKIPPED
}

/** 指标最近一次读取结果的原因，用于界面解释“为什么没有值”。 */
enum class MetricStatus {
    OK,
    EMPTY,
    SKIPPED,
    PRIVILEGE_DENIED,
    UNSUPPORTED,
    TIMEOUT,
    PARSE_FAILED,
    ERROR,
}

/**
 * 一次调度的采集结果。
 *
 * @param value 采集值，失败或不可用时为 null。
 * @param error 采集过程中抛出的异常，供引擎记录日志。
 * @param durationMillis 本次采集耗时（毫秒）。
 * @param skipped 能力不可用（未授权/未安装）导致本轮跳过，不计为失败。
 * @param status 最近一次读取状态；未显式指定时由 skipped/error/value 推导。
 * @param metric 类型化的采集结果；由调度器根据 id 和 value 生成。
 */
data class CollectorResult(
    val id: CollectorId,
    val value: Any?,
    val error: Throwable? = null,
    val durationMillis: Long = 0L,
    val skipped: Boolean = false,
    val status: MetricStatus = when {
        skipped -> MetricStatus.SKIPPED
        error != null -> MetricStatus.ERROR
        value == null -> MetricStatus.EMPTY
        else -> MetricStatus.OK
    },
    val metric: MetricValue? = null,
)

/** 默认单次采集超时预算，适用于电池、CPU、内存等低开销采集器。 */
const val DEFAULT_COLLECT_TIMEOUT_MILLIS = 500L

/** 采集器集合，供引擎统一调度。 */
class CollectorRegistry(val collectors: List<Collector<*>>)
