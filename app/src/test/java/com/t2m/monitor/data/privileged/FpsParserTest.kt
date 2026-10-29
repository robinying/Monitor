package com.t2m.monitor.data.privileged

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SurfaceFlinger 输出解析：图层选择、1 秒窗口帧数统计与异常输入。
 */
class FpsParserTest {

    @Test
    fun `prefers surface view layer then blast then last line`() {
        val output = """
            LayerNameA#0
            SurfaceView[com.example/com.example.Main](BLAST)#1
            com.example/com.example.Main#0
        """.trimIndent()
        assertEquals(
            "SurfaceView[com.example/com.example.Main](BLAST)#1",
            FpsParser.selectLayer(output),
        )

        assertEquals("layer#0", FpsParser.selectLayer("other\nlayer#0"))
        assertNull(FpsParser.selectLayer(""))
    }

    @Test
    fun `counts frames within the last second`() {
        val now = 10_000_000_000L
        val latency = """
            16666666
            9000000000	9000000000	9000000000
            9500000000	9500000000	9500000000
            9900000000	9900000000	9900000000
            8000000000	8000000000	8000000000
            9223372036854775807	9223372036854775807	9223372036854775807
            0	0	0
        """.trimIndent()
        assertEquals(3f, FpsParser.parseLatency(latency, now)!!, 0.01f)
    }

    @Test
    fun `returns null without usable frame rows`() {
        assertNull(FpsParser.parseLatency("16666666", 10_000_000_000L))
        assertNull(FpsParser.parseLatency("", 10_000_000_000L))
        assertNull(
            FpsParser.parseLatency(
                "16666666\n9223372036854775807\t9223372036854775807\t1",
                10_000_000_000L,
            ),
        )
    }

    @Test
    fun `clamps unrealistic frame counts`() {
        val now = 10_000_000_000L
        val rows = (0 until 300).joinToString("\n") { index ->
            val timestamp = now - index * 1_000_000L
            "$timestamp\t$timestamp\t$timestamp"
        }
        assertEquals(240f, FpsParser.parseLatency("16666666\n$rows", now)!!, 0.01f)
    }
}
