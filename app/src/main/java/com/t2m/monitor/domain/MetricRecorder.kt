package com.t2m.monitor.domain

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.repository.MetricRepository
import com.t2m.monitor.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 采样记录器：订阅引擎快照，按 10 秒窗口聚合后写入 Room。
 *
 * 由前台服务启停；停止时把未满窗口的数据落库，避免丢点。
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

    private val recordMutex = Mutex()

    fun start() {
        if (recordJob?.isActive == true) {
            return
        }
        aggregator.reset()
        recordJob = scope.launch {
            engine.snapshot.collect { snapshot ->
                recordMutex.withLock {
                    aggregator.add(snapshot)
                    if (aggregator.shouldFlush()) {
                        flushWindow()
                    }
                }
            }
        }
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
        flushNow()
    }

    /** 立即把当前窗口写入数据库（暂停采样或服务停止时调用）。 */
    fun flushNow() {
        scope.launch {
            recordMutex.withLock {
                flushWindow()
            }
        }
    }

    private suspend fun flushWindow() {
        val aggregates = aggregator.flush(clock.currentTimeMillis())
        if (aggregates.isEmpty()) {
            return
        }
        val result = runCatching { repository.insert(aggregates) }
        if (result.isFailure) {
            logger.warn("insert metric samples failed", result.exceptionOrNull())
        }
    }
}
