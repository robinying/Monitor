package com.robin.monitor.data.privileged

/**
 * timestats 失败退避。
 *
 * 连续失败达到阈值后暂停尝试一个冷却窗口，避免不支持的 ROM 上每 5 秒都执行
 * `-enable` + `-dump` + 两条 latency 回退命令。
 */
internal class FpsTimeStatsGate(
    private val elapsedNanos: () -> Long,
    private val maxConsecutiveFailures: Int = MAX_CONSECUTIVE_FAILURES,
    private val cooldownNanos: Long = COOLDOWN_NANOS,
) {

    private var consecutiveFailures = 0

    private var blockedUntilNanos = Long.MIN_VALUE

    fun canAttempt(): Boolean = elapsedNanos() >= blockedUntilNanos

    fun onSuccess() {
        consecutiveFailures = 0
        blockedUntilNanos = Long.MIN_VALUE
    }

    fun onFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= maxConsecutiveFailures) {
            blockedUntilNanos = elapsedNanos() + cooldownNanos
            consecutiveFailures = 0
        }
    }

    fun reset() {
        consecutiveFailures = 0
        blockedUntilNanos = Long.MIN_VALUE
    }

    internal companion object {
        const val MAX_CONSECUTIVE_FAILURES = 3
        const val COOLDOWN_NANOS = 60_000_000_000L
    }
}
