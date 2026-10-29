package com.t2m.monitor.data.collector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/proc/stat` 解析：覆盖多核、字段缺失与非法输入。
 */
class ProcStatParserTest {

    private val sample = listOf(
        "cpu  1200 30 800 5000 100 20 5 10 0 0",
        "cpu0 600 15 400 2500 50 10 2 5 0 0",
        "cpu1 600 15 400 2500 50 10 3 5 0 0",
        "intr 123456 0 0 0",
        "ctxt 7890",
    )

    @Test
    fun `parses total and per core times`() {
        val parsed = ProcStatParser.parse(sample)!!
        val total = parsed.total!!
        // total = 1200+30+800+5000+100+20+5+10
        assertEquals(7165L, total.totalJiffies)
        // idle = idle + iowait = 5000 + 100
        assertEquals(5100L, total.idleJiffies)

        assertEquals(2, parsed.perCore.size)
        assertEquals(3582L, parsed.perCore[0]!!.totalJiffies)
        assertEquals(2550L, parsed.perCore[0]!!.idleJiffies)
    }

    @Test
    fun `keeps null placeholder for offline core index`() {
        val parsed = ProcStatParser.parse(
            listOf(
                "cpu 10 0 10 80 0 0 0 0",
                "cpu0 5 0 5 40 0 0 0 0",
                "cpu2 5 0 5 40 0 0 0 0",
            ),
        )!!
        assertNull(parsed.perCore[1])
        assertEquals(3, parsed.perCore.size)
    }

    @Test
    fun `ignores malformed and non cpu lines`() {
        val parsed = ProcStatParser.parse(
            listOf(
                "cpu abc 0 0 0",
                "cpu0 10 0 10",
                "intr 1 2 3",
                "cpu 10 0 10 80",
            ),
        )!!
        assertNull(parsed.perCore.firstOrNull())
        assertTrue(parsed.perCore.isEmpty())
        assertEquals(100L, parsed.total!!.totalJiffies)
    }

    @Test
    fun `returns null when nothing usable`() {
        assertNull(ProcStatParser.parse(emptyList()))
        assertNull(ProcStatParser.parse(listOf("intr 1 2 3")))
    }
}
