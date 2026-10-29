package com.t2m.monitor.data.collector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CPU 使用率差值算法：覆盖无基线、零增量、回绕与越界。
 */
class CpuUsageCalculatorTest {

    @Test
    fun `returns null without baseline`() {
        assertNull(CpuUsageCalculator.usageBetween(null, CpuTimes(1000, 800)))
    }

    @Test
    fun `computes busy ratio from delta`() {
        val previous = CpuTimes(totalJiffies = 1000, idleJiffies = 800)
        val current = CpuTimes(totalJiffies = 2000, idleJiffies = 1600)
        assertEquals(20f, CpuUsageCalculator.usageBetween(previous, current)!!, 0.01f)
    }

    @Test
    fun `returns null when counter does not advance`() {
        val times = CpuTimes(totalJiffies = 1000, idleJiffies = 800)
        assertNull(CpuUsageCalculator.usageBetween(times, times))
    }

    @Test
    fun `clamps when idle delta exceeds total delta`() {
        val previous = CpuTimes(totalJiffies = 1000, idleJiffies = 500)
        val current = CpuTimes(totalJiffies = 1100, idleJiffies = 900)
        assertEquals(0f, CpuUsageCalculator.usageBetween(previous, current)!!, 0.01f)
    }

    @Test
    fun `returns null when counters decrease`() {
        val previous = CpuTimes(totalJiffies = 2000, idleJiffies = 1000)
        val current = CpuTimes(totalJiffies = 1000, idleJiffies = 500)
        assertNull(CpuUsageCalculator.usageBetween(previous, current))
    }
}
