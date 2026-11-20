package com.robin.monitor.data.collector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 电池 sysfs 缓存：设计容量只读一次，循环次数按 TTL 重读。
 */
class BatterySysfsReaderTest {

    private var nowMillis = 1_000L

    private val reads = mutableMapOf<String, Int>()

    @Test
    fun `design capacity is resolved once and converted from micro amp hours`() {
        val reader = BatterySysfsReader(
            readLong = { path ->
                reads[path] = (reads[path] ?: 0) + 1
                if (path == BatterySysfsReader.DESIGN_CAPACITY_PATH) 4_500_000L else null
            },
            elapsedMillis = { nowMillis },
        )

        repeat(20) { assertEquals(4500, reader.designCapacityMah()) }

        assertEquals(1, reads[BatterySysfsReader.DESIGN_CAPACITY_PATH])
        assertEquals(0, reads[BatterySysfsReader.FULL_CAPACITY_PATH] ?: 0)
    }

    @Test
    fun `design capacity falls back to charge full`() {
        val reader = BatterySysfsReader(
            readLong = { path ->
                if (path == BatterySysfsReader.FULL_CAPACITY_PATH) 3_800_000L else null
            },
            elapsedMillis = { nowMillis },
        )

        assertEquals(3800, reader.designCapacityMah())
    }

    @Test
    fun `cycle count is cached until the ttl expires`() {
        var value = 12L
        var readCount = 0
        val reader = BatterySysfsReader(
            readLong = { path ->
                if (path == BatterySysfsReader.CYCLE_COUNT_PATH) {
                    readCount++
                    value
                } else {
                    null
                }
            },
            elapsedMillis = { nowMillis },
            cycleCountTtlMillis = 60_000L,
        )

        assertEquals(12, reader.cycleCount())
        nowMillis += 30_000L
        assertEquals(12, reader.cycleCount())
        assertEquals(1, readCount)

        value = 13L
        nowMillis += 30_000L
        assertEquals(13, reader.cycleCount())
        assertEquals(2, readCount)
    }

    @Test
    fun `unreadable cycle count stays null and retries after ttl`() {
        var readCount = 0
        val reader = BatterySysfsReader(
            readLong = { path ->
                if (path == BatterySysfsReader.CYCLE_COUNT_PATH) {
                    readCount++
                }
                null
            },
            elapsedMillis = { nowMillis },
            cycleCountTtlMillis = 60_000L,
        )

        assertNull(reader.cycleCount())
        assertNull(reader.cycleCount())
        assertEquals(1, readCount)

        nowMillis += 60_000L
        assertNull(reader.cycleCount())
        assertEquals(2, readCount)
    }
}
