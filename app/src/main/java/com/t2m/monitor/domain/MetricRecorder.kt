package com.t2m.monitor.domain

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.model.MetricAggregate
import com.t2m.monitor.data.repository.MetricRepository
import com.t2m.monitor.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * 采样记录器：订阅引擎快照，按 10 秒窗口聚合后写入 Room。
 *
 * 由前台服务启停；停止时把未满窗口的数据落库，避免丢点。
 *
 * 生命周期约定：
 * 1. start/stop/flush 通过 [serialMutex] 串行化，新 start 会等待上一次 stop 的落库完成，
 *    避免 reset 清掉尚未提交的窗口；
 * 2. 已取出的窗口先保存在 [pendingAggregates]，落库失败时保留并在下次 flush 重试；
 * 3. 数据库写入放在 NonCancellable 中，stop 取消失败时不会中断已在提交的窗口。
 */
@Singleton
class MetricRecorder @Inject constructor(
    private val engine: MonitorEngine,
    private val repository: MetricRepository,
    private val clock: MonitorClock,
    private val logger: MonitorLogger,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val aggregator = MetricAggregator { clock.elapsedRealtimeMillis() }

    private var recordJob: Job? = null

    private var stopJob: Job? = null

    private val serialMutex = Mutex()

    /** 已从聚合器取出但尚未成功写入数据库的窗口；仅由 [serialMutex] 保护。 */
    private val pendingAggregates = mutableListOf<MetricAggregate>()

    fun start() {
        if (recordJob?.isActive == true) {
            return
        }
        val waitForStop = stopJob
        recordJob = scope.launch {
            waitForStop?.join()
            serialMutex.withLock { aggregator.reset() }
            engine.snapshot.collect { snapshot ->
                serialMutex.withLock {
                    aggregator.add(snapshot)
                    if (aggregator.shouldFlush()) {
                        flushWindow()
                    }
                }
            }
        }
    }

    fun stop() {
        val running = recordJob
        recordJob = null
        // 先等待订阅任务结束（含在途 flush），再落库剩余窗口；新 start 会等待该任务。
        stopJob = scope.launch {
            running?.cancelAndJoin()
            serialMutex.withLock { flushWindow() }
        }
    }

    /** 立即把当前窗口写入数据库（暂停采样或服务停止时调用）。 */
    fun flushNow() {
        scope.launch {
            serialMutex.withLock {
                flushWindow()
            }
        }
    }

    /** 等待当前窗口及未提交数据写入数据库；供清空历史前调用。 */
    suspend fun flushNowAndWait() {
        serialMutex.withLock { flushWindow() }
    }

    private suspend fun flushWindow() {
        val aggregates = mutableListOf<MetricAggregate>()
        aggregates += pendingAggregates
        pendingAggregates.clear()
        aggregates += aggregator.flush(clock.currentTimeMillis())
        if (aggregates.isEmpty()) {
            return
        }
        var committed = false
        try {
            // 停止/取消不应丢弃已取出的窗口；Room 写入耗时有限，提交完成后才响应取消。
            withContext(NonCancellable) {
                repository.insert(aggregates)
            }
            committed = true
        } catch (cancellation: CancellationException) {
            if (!committed) {
                pendingAggregates.addAll(aggregates)
            }
            throw cancellation
        } catch (error: Exception) {
            pendingAggregates.addAll(aggregates)
            logger.warn("insert metric samples failed", error)
        }
    }
}
