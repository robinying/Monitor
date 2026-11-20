package com.robin.monitor.data.collector

import com.robin.monitor.data.model.FpsSnapshot
import com.robin.monitor.data.privileged.SystemFpsSource
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 系统帧率采集器（Shizuku 特权能力）。
 *
 * 优先读取 SurfaceFlinger timestats（帧率、p95/p99 帧时间、卡顿率），失败时回退
 * `--latency` 单层统计；每次采样会启动 shell 进程，因此按 5 秒周期执行。
 * 未授权时返回 null，界面按“需要授权”展示。
 */
class FpsCollector @Inject constructor(
    private val source: SystemFpsSource,
) : Collector<FpsSnapshot> {

    override val id: CollectorId = CollectorId.FPS

    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    override val timeoutMillis: Long = COLLECT_TIMEOUT_MILLIS

    override fun isAvailable(): Boolean = source.isAvailable()

    override fun unavailableStatus(): MetricStatus = MetricStatus.PRIVILEGE_DENIED

    override suspend fun collect(): FpsSnapshot? = withContext(Dispatchers.IO) {
        source.readFps()
    }

    /** 停止 SurfaceFlinger timestats 统计，避免应用退出后仍保留系统统计开销。 */
    suspend fun release() = withContext(Dispatchers.IO) {
        source.release()
    }

    private companion object {
        const val COLLECT_TIMEOUT_MILLIS = 2_000L
        const val DEFAULT_INTERVAL_MILLIS = 5_000L
    }
}
