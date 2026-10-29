package com.t2m.monitor.domain

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorRegistry
import com.t2m.monitor.data.collector.CollectorResult
import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.CpuSnapshot
import com.t2m.monitor.data.model.MemorySnapshot
import com.t2m.monitor.data.model.MonitorSnapshot
import com.t2m.monitor.data.model.AppStorageUsage
import com.t2m.monitor.data.model.StorageSnapshot
import com.t2m.monitor.data.model.ThermalSnapshot
import com.t2m.monitor.data.model.UsageSnapshot
import com.t2m.monitor.di.ApplicationScope
import com.t2m.monitor.domain.diagnostics.DiagnosticsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 采集中枢。
 *
 * 负责启动/停止采样循环、合并各采集器结果并对外暴露最新快照。
 * 采集器本轮失败时保留上一次有效值，避免界面数值闪烁或归零。
 */
@Singleton
class MonitorEngine @Inject constructor(
    private val registry: CollectorRegistry,
    private val clock: MonitorClock,
    private val scheduler: SamplingScheduler,
    private val logger: MonitorLogger,
    private val diagnostics: DiagnosticsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _snapshot = MutableStateFlow(MonitorSnapshot.EMPTY)

    /** 最新一次快照，界面只订阅该流。 */
    val snapshot: StateFlow<MonitorSnapshot> = _snapshot.asStateFlow()

    private val _isRunning = MutableStateFlow(false)

    /** 采样是否运行中。 */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val lastValues = mutableMapOf<CollectorId, Any>()

    private var samplingJob: Job? = null

    /** 串行化启动/停止：旧任务释放后新任务才进入采样循环，避免两轮采集并发。 */
    private val runMutex = Mutex()

    /** 启动采样；重复调用幂等。 */
    fun start() {
        if (samplingJob?.isActive == true) {
            return
        }
        _isRunning.value = true
        samplingJob = scope.launch {
            runMutex.withLock {
                if (!_isRunning.value) {
                    return@withLock
                }
                scheduler.run(
                    collectors = registry.collectors,
                    now = clock::elapsedRealtimeMillis,
                    onResults = { results -> applyResults(results) },
                )
            }
        }
    }

    /** 停止采样；已产生的快照保留，便于界面继续展示最后一次数据。 */
    fun stop() {
        _isRunning.value = false
        samplingJob?.cancel()
        samplingJob = null
    }

    private suspend fun applyResults(results: List<CollectorResult>) {
        for (result in results) {
            diagnostics.record(result)
            if (result.skipped) {
                // 能力被撤销（Shizuku/使用情况访问）时清空旧值，避免继续展示过期数据。
                lastValues.remove(result.id)
                continue
            }
            val error = result.error
            if (error != null) {
                logger.warn("collector ${result.id} failed", error)
                continue
            }
            val value = result.value ?: continue
            lastValues[result.id] = value
        }
        _snapshot.value = MonitorSnapshot(
            timestampMillis = clock.currentTimeMillis(),
            battery = lastValues[CollectorId.BATTERY] as? BatterySnapshot,
            cpu = lastValues[CollectorId.CPU] as? CpuSnapshot,
            memory = lastValues[CollectorId.MEMORY] as? MemorySnapshot,
            storage = lastValues[CollectorId.STORAGE] as? StorageSnapshot,
            thermal = lastValues[CollectorId.THERMAL] as? ThermalSnapshot,
            usage = lastValues[CollectorId.USAGE] as? UsageSnapshot,
            fps = lastValues[CollectorId.FPS] as? Float,
            appStorage = (lastValues[CollectorId.APP_STORAGE] as? List<*>)
                ?.filterIsInstance<AppStorageUsage>(),
        )
    }
}
