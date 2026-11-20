package com.t2m.monitor.data.collector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CPU 频率路径缓存：不可读核心在 TTL 内不重复读，恢复后自动重试。
 */
class CpuFrequencyFileReaderTest {

    private var nowMillis = 1_000L

    @Test
    fun `missing core is not retried until ttl`() {
        var reads = 0
        val reader = CpuFrequencyFileReader(
            readInt = {
                reads++
                null
            },
            elapsedMillis = { nowMillis },
            retryAfterMillis = 60_000L,
        )

        repeat(10) { assertNull(reader.read("/cpu0/cur")) }
        assertEquals(1, reads)

        nowMillis += 60_000L
        assertNull(reader.read("/cpu0/cur"))
        assertEquals(2, reads)
    }

    @Test
    fun `successful read clears the failure cache`() {
        var value: Int? = null
        var reads = 0
        val reader = CpuFrequencyFileReader(
            readInt = {
                reads++
                value
            },
            elapsedMillis = { nowMillis },
            retryAfterMillis = 60_000L,
        )

        assertNull(reader.read("/cpu0/cur"))
        assertEquals(1, reads)

        value = 1_800_000
        nowMillis += 60_000L
        assertEquals(1_800_000, reader.read("/cpu0/cur"))
        assertEquals(2, reads)

        assertEquals(1_800_000, reader.read("/cpu0/cur"))
        assertEquals(3, reads)
    }

    @Test
    fun `exception is treated as a missing value`() {
        val reader = CpuFrequencyFileReader(
            readInt = { throw IllegalStateException("offline") },
            elapsedMillis = { nowMillis },
        )

        assertNull(reader.read("/cpu4/cur"))
    }
}
