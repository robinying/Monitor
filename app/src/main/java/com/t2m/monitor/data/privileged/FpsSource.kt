package com.t2m.monitor.data.privileged

import com.t2m.monitor.data.model.FpsSnapshot
import com.t2m.monitor.data.model.FpsSource
import javax.inject.Inject

/** 系统帧率数据来源，便于测试替换。 */
interface SystemFpsSource {

    fun isAvailable(): Boolean

    fun readFps(): FpsSnapshot?

    /** 停止 timestats 统计；服务结束或权限撤销时调用。 */
    fun release() = Unit
}

/** 生产实现：Shizuku + `dumpsys SurfaceFlinger`，timestats 优先、latency 回退。 */
class ShizukuFpsSource @Inject constructor(
    private val shell: ShizukuShell,
) : SystemFpsSource {

    private var timeStatsEnabled = false

    private var lastTotalFrames: Long? = null

    private var lastMissedFrames: Long? = null

    private var lastSampleNanos = 0L

    override fun isAvailable(): Boolean = shell.isGranted()

    override fun readFps(): FpsSnapshot? {
        enableTimeStatsIfNeeded()
        if (timeStatsEnabled) {
            readTimeStats()?.let { return it }
        }
        return readLatency()
    }

    override fun release() {
        if (!timeStatsEnabled) {
            return
        }
        shell.exec("dumpsys", "SurfaceFlinger", "--timestats", "-disable")
        timeStatsEnabled = false
        lastTotalFrames = null
        lastMissedFrames = null
        lastSampleNanos = 0L
    }

    private fun enableTimeStatsIfNeeded() {
        if (timeStatsEnabled || !shell.isGranted()) {
            return
        }
        val enabled = shell.exec("dumpsys", "SurfaceFlinger", "--timestats", "-clear", "-enable")
        if (enabled == null) {
            return
        }
        timeStatsEnabled = true
        lastTotalFrames = null
        lastMissedFrames = null
        lastSampleNanos = System.nanoTime()
    }

    private fun readTimeStats(): FpsSnapshot? {
        val output = shell.exec("dumpsys", "SurfaceFlinger", "--timestats", "-dump") ?: return null
        val stats = FpsParser.parseTimeStats(output)
        val nowNanos = System.nanoTime()
        val snapshot = FpsStatsCalculator.fromTimeStats(
            stats = stats,
            previousTotalFrames = lastTotalFrames,
            previousMissedFrames = lastMissedFrames,
            elapsedNanos = (nowNanos - lastSampleNanos).coerceAtLeast(1L),
        ) ?: return null
        lastTotalFrames = stats.totalFrames ?: lastTotalFrames
        lastMissedFrames = stats.missedFrames ?: lastMissedFrames
        lastSampleNanos = nowNanos
        return snapshot
    }

    private fun readLatency(): FpsSnapshot? {
        val layers = shell.exec("dumpsys", "SurfaceFlinger", "--list") ?: return null
        val layer = FpsParser.selectLayer(layers) ?: return null
        val latency = shell.exec("dumpsys", "SurfaceFlinger", "--latency", layer) ?: return null
        val fps = FpsParser.parseLatency(latency, System.nanoTime()) ?: return null
        return FpsSnapshot(fps = fps, source = FpsSource.LATENCY)
    }
}
