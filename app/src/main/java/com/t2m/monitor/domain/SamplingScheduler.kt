package com.t2m.monitor.domain

import com.t2m.monitor.data.collector.Collector
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorResult
import com.t2m.monitor.data.collector.MetricStatus
import com.t2m.monitor.data.collector.MetricValue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 采样调度器。
 *
 * 单个循环按固定 tick 驱动，各采集器按自身 [Collector.intervalMillis] 决定本轮是否执行，
 * 避免为每个采集器维护独立计时器。每个采集器有独立的超时预算
 * （[Collector.timeoutMillis]）：单个慢采集器超时只产生一条失败结果，不会丢弃本轮
 * 其他已成功的数据；能力不可用的 skipped 结果独立发布，不受采集超时影响。
 *
 * @param tickIntervalMillis 调度 tick 间隔
 */
class SamplingScheduler(
    private val tickIntervalMillis: Long = DEFAULT_TICK_INTERVAL_MILLIS,
) {

    private val lastRunAtMillis = mutableMapOf<CollectorId, Long>()

    /**
     * 持续运行采样循环，直到协程被取消。
     *
     * @param collectors 采集器集合
     * @param now 单调时钟（毫秒），测试可注入虚拟时间
     * @param onResults 本轮采集结果回调；存在到期/不可用采集器时触发
     */
    suspend fun run(
        collectors: List<Collector<*>>,
        now: () -> Long,
        onResults: suspend (List<CollectorResult>) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            val tickStartedAt = now()
            val unavailableCollectors = collectors.filterNot { collector -> collector.isAvailable() }
            val dueCollectors = collectors.filter { collector ->
                collector.isAvailable() && isDue(collector, tickStartedAt)
            }

            val results = ArrayList<CollectorResult>(
                unavailableCollectors.size + dueCollectors.size,
            )
            // 能力不可用的结果独立发布：即使同轮采集超时，撤权清空仍能到达引擎。
            unavailableCollectors.forEach { collector ->
                results += CollectorResult(
                    id = collector.id,
                    value = null,
                    skipped = true,
                    status = collector.unavailableStatus(),
                )
            }
            dueCollectors.forEach { collector ->
                results += collectWithTimeout(collector)
                lastRunAtMillis[collector.id] = tickStartedAt
            }
            if (results.isNotEmpty()) {
                onResults(results)
            }
            delay(tickIntervalMillis)
        }
    }

    private fun isDue(collector: Collector<*>, tickStartedAt: Long): Boolean {
        val lastRunAt = lastRunAtMillis[collector.id]
        return lastRunAt == null || tickStartedAt - lastRunAt >= collector.intervalMillis
    }

    private suspend fun collectWithTimeout(collector: Collector<*>): CollectorResult {
        val startedAtNanos = System.nanoTime()
        val outcome = try {
            // 包装非空对象，区分“采集器返回 null”和“超时返回 null”。
            withTimeoutOrNull(collector.timeoutMillis) { Collected(collector.collect()) }
        } catch (cancellation: CancellationException) {
            // 上层取消：不吞掉取消信号。
            throw cancellation
        } catch (error: Exception) {
            // 单个采集器异常不影响本轮其他指标，异常交由引擎记录日志。
            return CollectorResult(
                id = collector.id,
                value = null,
                error = error,
                durationMillis = elapsedMillisSince(startedAtNanos),
                status = classify(error),
            )
        }
        val durationMillis = elapsedMillisSince(startedAtNanos)
        return if (outcome == null) {
            CollectorResult(
                id = collector.id,
                value = null,
                error = CollectorTimeoutException(collector.id, collector.timeoutMillis),
                durationMillis = durationMillis,
                status = MetricStatus.TIMEOUT,
            )
        } else {
            val metric = outcome.value?.let { value -> MetricValue.from(collector.id, value) }
            CollectorResult(
                id = collector.id,
                value = outcome.value,
                durationMillis = durationMillis,
                status = when {
                    outcome.value == null -> MetricStatus.EMPTY
                    metric == null -> MetricStatus.ERROR
                    else -> MetricStatus.OK
                },
                metric = metric,
            )
        }
    }

    private fun classify(error: Exception): MetricStatus = when (error) {
        is SecurityException -> MetricStatus.PRIVILEGE_DENIED
        is UnsupportedOperationException -> MetricStatus.UNSUPPORTED
        is NumberFormatException -> MetricStatus.PARSE_FAILED
        else -> MetricStatus.ERROR
    }

    private fun elapsedMillisSince(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI

    private class Collected(val value: Any?)

    private companion object {
        const val DEFAULT_TICK_INTERVAL_MILLIS = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** 采集器超过自身超时预算时产生的失败结果。 */
class CollectorTimeoutException(
    collectorId: CollectorId,
    timeoutMillis: Long,
) : IllegalStateException("collector $collectorId timed out after $timeoutMillis ms")
