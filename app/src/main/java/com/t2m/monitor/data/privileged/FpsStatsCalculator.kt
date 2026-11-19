package com.t2m.monitor.data.privileged

import com.t2m.monitor.data.model.FpsSnapshot
import com.t2m.monitor.data.model.FpsSource

/**
 * 把 timestats 的累计值转换为一次采样。
 *
 * FPS 优先使用 totalFrames 增量 / 实际耗时，缺少基线时回退 averageFPS；
 * 卡顿率同样使用 missed/presented 的增量，避免把启用 timestats 以来的累计值当成当前窗口。
 */
object FpsStatsCalculator {

    private const val MAX_FPS = 240f

    fun fromTimeStats(
        stats: FpsParser.TimeStats,
        previousTotalFrames: Long?,
        previousMissedFrames: Long?,
        elapsedNanos: Long,
    ): FpsSnapshot? {
        val fps = calculateFps(stats, previousTotalFrames, elapsedNanos) ?: return null
        val jankPercent = calculateJankPercent(
            stats = stats,
            previousTotalFrames = previousTotalFrames,
            previousMissedFrames = previousMissedFrames,
        )
        return FpsSnapshot(
            fps = fps.coerceIn(0f, MAX_FPS),
            frameTimeP95Millis = stats.frameTimeP95Millis,
            frameTimeP99Millis = stats.frameTimeP99Millis,
            jankPercent = jankPercent?.coerceIn(0f, 100f),
            source = FpsSource.TIMESTATS,
        )
    }

    private fun calculateFps(
        stats: FpsParser.TimeStats,
        previousTotalFrames: Long?,
        elapsedNanos: Long,
    ): Float? {
        val totalFrames = stats.totalFrames
        if (totalFrames != null && previousTotalFrames != null && elapsedNanos > 0L) {
            val delta = totalFrames - previousTotalFrames
            if (delta >= 0L) {
                return (delta.toDouble() * NANOS_PER_SECOND / elapsedNanos).toFloat()
            }
        }
        return stats.averageFps
    }

    private fun calculateJankPercent(
        stats: FpsParser.TimeStats,
        previousTotalFrames: Long?,
        previousMissedFrames: Long?,
    ): Float? {
        val totalFrames = stats.totalFrames ?: return null
        val missedFrames = stats.missedFrames ?: return null
        val presented = if (previousTotalFrames != null) {
            totalFrames - previousTotalFrames
        } else {
            totalFrames
        }
        val missed = if (previousMissedFrames != null) {
            missedFrames - previousMissedFrames
        } else {
            missedFrames
        }
        if (presented < 0L || missed < 0L) {
            return null
        }
        val total = presented + missed
        return if (total > 0L) missed.toFloat() / total.toFloat() * 100f else null
    }

    private const val NANOS_PER_SECOND = 1_000_000_000.0
}
