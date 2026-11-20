package com.robin.monitor.domain.alert

import com.robin.monitor.core.MonitorClock
import com.robin.monitor.core.MonitorLogger
import com.robin.monitor.data.model.MonitorSnapshot
import com.robin.monitor.data.repository.AlertRepository
import com.robin.monitor.di.ApplicationScope
import com.robin.monitor.domain.MonitorEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * 告警监控：订阅快照 → 阈值判定 → 落库 + 通知。
 *
 * 由前台服务启停，与采样生命周期一致。
 */
@Singleton
class AlertMonitor @Inject constructor(
    private val engine: MonitorEngine,
    private val evaluator: ThresholdEvaluator,
    private val repository: AlertRepository,
    private val notifier: AlertNotifier,
    private val clock: MonitorClock,
    private val logger: MonitorLogger,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private var alertJob: Job? = null

    /** 已处理的快照；服务重启后 StateFlow 会重放旧值，不能把重放当成新的采样。 */
    private var lastHandledSnapshot: MonitorSnapshot? = null

    fun start() {
        if (alertJob?.isActive == true) {
            return
        }
        // 新的运行周期必须重新积累连续样本；去抖记录跨重启保留。
        evaluator.resetConsecutiveCounts()
        alertJob = scope.launch {
            engine.snapshot.collect { snapshot ->
                if (snapshot.timestampMillis <= 0L) {
                    return@collect
                }
                if (snapshot == lastHandledSnapshot) {
                    return@collect
                }
                lastHandledSnapshot = snapshot
                val events = evaluator.evaluate(
                    snapshot = snapshot,
                    timestampMillis = snapshot.timestampMillis,
                    monotonicMillis = clock.elapsedRealtimeMillis(),
                )
                for (event in events) {
                    try {
                        repository.insert(event)
                    } catch (cancellation: CancellationException) {
                        // 停止服务时取消写入：不得继续发布通知。
                        throw cancellation
                    } catch (error: Exception) {
                        logger.warn("insert alert event failed: ${event.type}", error)
                    }
                    currentCoroutineContext().ensureActive()
                    notifier.notify(event)
                }
            }
        }
    }

    fun stop() {
        alertJob?.cancel()
        alertJob = null
    }
}
