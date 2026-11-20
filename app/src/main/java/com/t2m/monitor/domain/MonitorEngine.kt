package com.t2m.monitor.domain

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorRegistry
import com.t2m.monitor.data.collector.CollectorResult
import com.t2m.monitor.data.collector.MetricStatus
import com.t2m.monitor.data.collector.MetricValue
import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.FpsSnapshot
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

    private var battery: BatterySnapshot? = null

    private var cpu: CpuSnapshot? = null

    private var memory: MemorySnapshot? = null

    private var storage: StorageSnapshot? = null

    private var thermal: ThermalSnapshot? = null

    private var usage: UsageSnapshot? = null

    private var fps: FpsSnapshot? = null

    private var appStorage: List<AppStorageUsage>? = null

    /** 各指标最近一次成功采样的墙上时间，随快照发布，供告警判定区分新样本。 */
    private val lastUpdatedAtMillis = mutableMapOf<CollectorId, Long>()

    /** 各指标最近一次读取状态，随快照发布给界面。 */
    private val metricStatuses = mutableMapOf<CollectorId, MetricStatus>()

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
        val nowMillis = clock.currentTimeMillis()
        for (result in results) {
            diagnostics.record(result)
            metricStatuses[result.id] = result.status
            if (result.skipped) {
                // 能力被撤销（Shizuku/使用情况访问）时清空旧值，避免继续展示过期数据。
                clearMetric(result.id)
                lastUpdatedAtMillis.remove(result.id)
                continue
            }
            val error = result.error
            if (error != null) {
                logger.warn("collector ${result.id} failed", error)
                continue
            }
            val metric = result.metric
            if (metric == null) {
                if (result.value != null) {
                    logger.warn("collector ${result.id} returned unsupported value")
                }
                continue
            }
            applyMetric(metric)
            lastUpdatedAtMillis[result.id] = nowMillis
        }
        _snapshot.value = MonitorSnapshot(
            timestampMillis = nowMillis,
            battery = battery,
            cpu = cpu,
            memory = memory,
            storage = storage,
            thermal = thermal,
            usage = usage,
            fps = fps,
            appStorage = appStorage,
            sampleTimestampsMillis = lastUpdatedAtMillis.toMap(),
            metricStatuses = metricStatuses.toMap(),
        )
    }

    private fun applyMetric(metric: MetricValue) {
        when (metric) {
            is MetricValue.Battery -> battery = metric.value
            is MetricValue.Cpu -> cpu = metric.value
            is MetricValue.Memory -> memory = metric.value
            is MetricValue.Storage -> storage = metric.value
            is MetricValue.Thermal -> thermal = metric.value
            is MetricValue.Usage -> usage = metric.value
            is MetricValue.Fps -> fps = metric.value
            is MetricValue.AppStorage -> appStorage = metric.value
        }
    }

    private fun clearMetric(id: CollectorId) {
        when (id) {
            CollectorId.BATTERY -> battery = null
            CollectorId.CPU -> cpu = null
            CollectorId.MEMORY -> memory = null
            CollectorId.STORAGE -> storage = null
            CollectorId.THERMAL -> thermal = null
            CollectorId.USAGE -> usage = null
            CollectorId.FPS -> fps = null
            CollectorId.APP_STORAGE -> appStorage = null
        }
    }
}
