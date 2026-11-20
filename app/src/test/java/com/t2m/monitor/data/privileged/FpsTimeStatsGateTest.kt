package com.t2m.monitor.data.privileged

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * timestats 退避：连续失败后暂停一个冷却窗口，成功或 reset 后恢复。
 */
class FpsTimeStatsGateTest {

    private var nowNanos = 1_000_000_000L

    private val gate = FpsTimeStatsGate(
        elapsedNanos = { nowNanos },
        maxConsecutiveFailures = 3,
        cooldownNanos = 60_000_000_000L,
    )

    @Test
    fun `blocks after three consecutive failures and recovers after cooldown`() {
        assertTrue(gate.canAttempt())
        gate.onFailure()
        gate.onFailure()
        assertTrue(gate.canAttempt())

        gate.onFailure()
        assertFalse(gate.canAttempt())

        nowNanos += 59_000_000_000L
        assertFalse(gate.canAttempt())

        nowNanos += 1_000_000_000L
        assertTrue(gate.canAttempt())
    }

    @Test
    fun `success clears failure streak`() {
        gate.onFailure()
        gate.onFailure()
        gate.onSuccess()
        gate.onFailure()
        gate.onFailure()

        assertTrue(gate.canAttempt())

        gate.onFailure()
        assertFalse(gate.canAttempt())
    }

    @Test
    fun `reset clears cooldown`() {
        repeat(3) { gate.onFailure() }
        assertFalse(gate.canAttempt())

        gate.reset()

        assertTrue(gate.canAttempt())
    }
}
