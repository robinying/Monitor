package com.t2m.monitor.domain.alert

import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.repository.AlertRepository
import com.t2m.monitor.di.ApplicationScope
import com.t2m.monitor.domain.MonitorEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val logger: MonitorLogger,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private var alertJob: Job? = null

    fun start() {
        if (alertJob?.isActive == true) {
            return
        }
        alertJob = scope.launch {
            engine.snapshot.collect { snapshot ->
                if (snapshot.timestampMillis <= 0L) {
                    return@collect
                }
                val events = evaluator.evaluate(snapshot, snapshot.timestampMillis)
                for (event in events) {
                    val saved = runCatching { repository.insert(event) }
                    if (saved.isFailure) {
                        logger.warn("insert alert event failed: ${event.type}", saved.exceptionOrNull())
                    }
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
