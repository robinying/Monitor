package com.t2m.monitor.data.collector

import com.t2m.monitor.data.privileged.SystemFpsSource
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 系统帧率采集器（Shizuku 特权能力）。
 *
 * 每次采样会启动一个 shell 进程，开销明显高于其他采集器，因此按 5 秒周期执行；
 * 未授权时返回 null，界面不展示该卡片。
 */
class FpsCollector @Inject constructor(
    private val source: SystemFpsSource,
) : Collector<Float> {

    override val id: CollectorId = CollectorId.FPS

    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    override fun isAvailable(): Boolean = source.isAvailable()

    override suspend fun collect(): Float? = withContext(Dispatchers.IO) {
        source.readFps()
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5_000L
    }
}
