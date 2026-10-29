package com.t2m.monitor.data.collector

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CPU 采集器：验证基线建立、差值计算、频率读取与降级路径。
 */
class CpuCollectorTest {

    @Test
    fun `first sample establishes baseline and second computes usage`() = runTest {
        val source = FakeCpuStatSource(
            snapshots = listOf(
                listOf(
                    "cpu  100 0 100 800 0 0 0 0",
                    "cpu0 50 0 50 400 0 0 0 0",
                    "cpu1 50 0 50 400 0 0 0 0",
                ),
                listOf(
                    "cpu  200 0 200 1600 0 0 0 0",
                    "cpu0 100 0 100 800 0 0 0 0",
                    "cpu1 100 0 100 800 0 0 0 0",
                ),
            ),
            frequencies = listOf(1_800_000, 2_400_000),
            maxFrequencies = listOf(2_800_000, 2_800_000),
        )
        val collector = CpuCollector(source)

        val first = collector.collect()
        assertEquals(2, first!!.coreCount)
        assertNull(first.totalUsagePercent)

        val second = collector.collect()!!
        assertEquals(20f, second.totalUsagePercent!!, 0.01f)
        assertEquals(20f, second.perCoreUsagePercent[0]!!, 0.01f)
        assertEquals(1_800_000, second.perCoreFrequencyKHz[0])
        assertEquals(2_800_000, second.maxFrequencyKHz)
    }

    @Test
    fun `marks offline core usage as null`() = runTest {
        val source = FakeCpuStatSource(
            snapshots = listOf(
                listOf("cpu 100 0 100 800", "cpu0 50 0 50 400", "cpu1 50 0 50 400"),
                listOf("cpu 200 0 200 1600", "cpu0 100 0 100 800"),
            ),
        )
        val collector = CpuCollector(source)
        collector.collect()
        val snapshot = collector.collect()!!
        assertEquals(2, snapshot.perCoreUsagePercent.size)
        assertNull(snapshot.perCoreUsagePercent[1])
    }

    @Test
    fun `falls back to core count and frequencies when proc stat unavailable`() = runTest {
        val collector = CpuCollector(
            FakeCpuStatSource(
                snapshots = emptyList(),
                frequencies = listOf(1_200_000, 1_800_000),
                maxFrequencies = listOf(2_800_000, 2_800_000),
            ),
        )
        val snapshot = collector.collect()!!
        assertNull(snapshot.totalUsagePercent)
        assertTrue(snapshot.coreCount >= 2)
        assertNull(snapshot.perCoreUsagePercent[0])
        assertEquals(1_200_000, snapshot.perCoreFrequencyKHz[0])
        assertEquals(2_800_000, snapshot.maxFrequencyKHz)
    }

    private class FakeCpuStatSource(
        private val snapshots: List<List<String>>,
        private val frequencies: List<Int?> = emptyList(),
        private val maxFrequencies: List<Int?> = emptyList(),
    ) : CpuStatSource {

        private var readIndex = 0

        override fun readStatLines(): List<String>? = snapshots.getOrNull(readIndex).also { readIndex++ }

        override fun readFrequencyKHz(coreIndex: Int): Int? = frequencies.getOrNull(coreIndex)

        override fun readMaxFrequencyKHz(coreIndex: Int): Int? = maxFrequencies.getOrNull(coreIndex)
    }
}
