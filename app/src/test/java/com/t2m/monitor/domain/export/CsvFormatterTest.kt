package com.t2m.monitor.domain.export

import com.t2m.monitor.data.model.MetricKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV 行格式：列顺序、数值精度、缺失值留空、时间列非空。
 */
class CsvFormatterTest {

    @Test
    fun `bom is the utf8 byte order mark`() {
        assertEquals("\uFEFF", CsvFormatter.BOM)
    }

    @Test
    fun `formats row in metric key order`() {
        val row = CsvFormatter.formatRow(
            timestampMillis = 1_700_000_000_000L,
            values = mapOf(
                MetricKeys.BATTERY_LEVEL to 78f,
                MetricKeys.BATTERY_TEMPERATURE to 36.44f,
                MetricKeys.CPU_USAGE to 23.4f,
                MetricKeys.MEMORY_USED_PERCENT to 62f,
            ),
        )
        val cells = row.split(",")
        assertEquals(7, cells.size)
        assertTrue(cells[0].isNotEmpty())
        assertEquals("78", cells[1])
        assertEquals("36.4", cells[2])
        assertEquals("23", cells[3])
        assertEquals("62", cells[4])
        assertEquals("", cells[5])
        assertEquals("", cells[6])
    }

    @Test
    fun `timestamp formatting includes date and time`() {
        val formatted = CsvFormatter.formatTimestamp(1_700_000_000_000L)
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}").matches(formatted))
    }
}
