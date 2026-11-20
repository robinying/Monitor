package com.robin.monitor.data.privileged

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
    fun `parses timestats totals jank and layer frame-time percentiles`() {
        val output = """
            SurfaceFlinger TimeStats:
            layerName = SurfaceView[com.example/com.example.Main](BLAST)#1
            averageFPS = 59.80
            present2present histogram is as below:
            16ms=90 17ms=8 33ms=2
            totalFrames = 1234
            missedFrames = 7
        """.trimIndent()

        val stats = FpsParser.parseTimeStats(output)

        assertEquals(1234L, stats.totalFrames)
        assertEquals(7L, stats.missedFrames)
        assertEquals(59.80f, stats.averageFps!!, 0.01f)
        assertEquals(17, stats.frameTimeP95Millis)
        assertEquals(33, stats.frameTimeP99Millis)
    }

    @Test
    fun `falls back to the global histogram when no layer histogram follows averageFPS`() {
        val output = """
            totalFrames = 100
            presentToPresent histogram is as below:
            8ms=50 16ms=50
        """.trimIndent()

        val stats = FpsParser.parseTimeStats(output)

        assertEquals(100L, stats.totalFrames)
        assertNull(stats.averageFps)
        assertEquals(16, stats.frameTimeP95Millis)
        assertEquals(16, stats.frameTimeP99Millis)
    }

    @Test
    fun `prefers the global histogram when the layer has too few samples`() {
        val output = """
            statsStart = 100
            statsEnd = 103
            totalFrames = 8
            presentToPresent histogram is as below:
            11ms=2 33ms=1 48ms=1 1000ms=2
            layerName = static-layer
            totalFrames = 1
            averageFPS = 1.000
            present2present histogram is as below:
            1000ms=1
        """.trimIndent()

        val stats = FpsParser.parseTimeStats(output)

        assertEquals(8L, stats.totalFrames)
        assertEquals(2.67f, stats.averageFps!!, 0.01f)
        assertEquals(1000, stats.frameTimeP95Millis)
        assertEquals(1000, stats.frameTimeP99Millis)
    }

    @Test
    fun `layer histogram wins when it has comparable samples`() {
        val output = """
            statsStart = 100
            statsEnd = 101
            totalFrames = 60
            presentToPresent histogram is as below:
            16ms=20 17ms=10
            layerName = game-layer
            totalFrames = 58
            averageFPS = 58.0
            present2present histogram is as below:
            16ms=50 17ms=8
        """.trimIndent()

        val stats = FpsParser.parseTimeStats(output)

        assertEquals(17, stats.frameTimeP95Millis)
        assertEquals(17, stats.frameTimeP99Millis)
    }

    @Test
    fun `timestats leaves absent quality fields null`() {
        val stats = FpsParser.parseTimeStats("averageFPS = 60.00")

        assertNull(stats.totalFrames)
        assertNull(stats.missedFrames)
        assertNull(stats.frameTimeP95Millis)
        assertEquals(60f, stats.averageFps!!, 0.01f)
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
