package com.t2m.monitor.domain

import com.t2m.monitor.data.collector.Collector
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 采样调度器。
 *
 * 单个循环按固定 tick 驱动，各采集器按自身 [Collector.intervalMillis] 决定本轮是否执行，
 * 避免为每个采集器维护独立计时器。
 *
 * @param tickIntervalMillis 调度 tick 间隔
 * @param tickTimeoutMillis 单轮采集超时；超时则丢弃本轮结果并在下一 tick 重试
 */
class SamplingScheduler(
    private val tickIntervalMillis: Long = DEFAULT_TICK_INTERVAL_MILLIS,
    private val tickTimeoutMillis: Long = DEFAULT_TICK_TIMEOUT_MILLIS,
) {

    private val lastRunAtMillis = mutableMapOf<CollectorId, Long>()

    /**
     * 持续运行采样循环，直到协程被取消。
     *
     * @param collectors 采集器集合
     * @param now 单调时钟（毫秒），测试可注入虚拟时间
     * @param onResults 本轮采集结果回调；仅在有采集器执行时触发
     */
    suspend fun run(
        collectors: List<Collector<*>>,
        now: () -> Long,
        onResults: suspend (List<CollectorResult>) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            val tickStartedAt = now()
            val dueCollectors = collectors.filter { collector ->
                if (!collector.isAvailable()) {
                    return@filter false
                }
                val lastRunAt = lastRunAtMillis[collector.id]
                lastRunAt == null || tickStartedAt - lastRunAt >= collector.intervalMillis
            }
            if (dueCollectors.isNotEmpty()) {
                val results = withTimeoutOrNull(tickTimeoutMillis) { collectAll(dueCollectors) }
                if (results != null) {
                    dueCollectors.forEach { collector -> lastRunAtMillis[collector.id] = tickStartedAt }
                    onResults(results)
                }
            }
            delay(tickIntervalMillis)
        }
    }

    private suspend fun collectAll(collectors: List<Collector<*>>): List<CollectorResult> =
        collectors.map { collector ->
            val startedAtNanos = System.nanoTime()
            try {
                val value = collector.collect()
                CollectorResult(
                    id = collector.id,
                    value = value,
                    durationMillis = elapsedMillisSince(startedAtNanos),
                )
            } catch (cancellation: CancellationException) {
                // 超时或上层取消：交给 withTimeoutOrNull / 调用方处理，不吞掉取消信号。
                throw cancellation
            } catch (error: Exception) {
                // 兜底：单个采集器异常不得影响其他指标，异常交由引擎记录日志。
                CollectorResult(
                    id = collector.id,
                    value = null,
                    error = error,
                    durationMillis = elapsedMillisSince(startedAtNanos),
                )
            }
        }

    private fun elapsedMillisSince(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI

    private companion object {
        const val DEFAULT_TICK_INTERVAL_MILLIS = 1_000L
        const val DEFAULT_TICK_TIMEOUT_MILLIS = 500L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
