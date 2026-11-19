package com.t2m.monitor.data.privileged

/**
 * `dumpsys SurfaceFlinger` 输出解析。
 *
 * 支持两种 FPS 数据源：
 * 1. `--timestats`：`totalFrames`、`missedFrames`、`averageFPS` 与 presentToPresent 直方图；
 * 2. `--latency`：单层帧时间戳中最近 1 秒的帧数（回退）。
 */
object FpsParser {

    private val WHITESPACE = Regex("\\s+")

    private const val INVALID_TIMESTAMP = Long.MAX_VALUE
    private const val WINDOW_NANOS = 1_000_000_000L

    private val TOTAL_FRAMES = Regex("""totalFrames\s*=\s*(\d+)""")
    private val MISSED_FRAMES = Regex("""(?:missedFrames|jankyFrames)\s*=\s*(\d+)""")
    private val AVERAGE_FPS = Regex("""averageFPS\s*=\s*([0-9.]+)""")
    private val STATS_START = Regex("""statsStart\s*=\s*(\d+)""")
    private val STATS_END = Regex("""statsEnd\s*=\s*(\d+)""")
    private val BUCKET = Regex("""(\d+)ms=(\d+)""")

    private const val LAYER_HISTOGRAM_MARKER = "present2present histogram is as below:"
    private const val GLOBAL_HISTOGRAM_MARKER = "presentToPresent histogram is as below:"

    /** `--timestats -dump` 中与帧率质量相关的字段。 */
    data class TimeStats(
        val totalFrames: Long?,
        val missedFrames: Long?,
        val averageFps: Float?,
        val frameTimeP95Millis: Int?,
        val frameTimeP99Millis: Int?,
    )

    /** 从 `--list` 输出中挑选最可能代表当前前台内容的图层。 */
    fun selectLayer(listOutput: String): String? {
        val lines = listOutput.lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) {
            return null
        }
        return lines.firstOrNull { line -> line.contains("SurfaceView[") }
            ?: lines.firstOrNull { line -> line.contains("BLAST") }
            ?: lines.last()
    }

    /**
     * @param output `--latency` 命令输出
     * @param nowNanos 当前单调时钟（与 SurfaceFlinger 帧时间戳同源）
     * @return 最近 1 秒的帧数（即 FPS）；无有效帧数据时返回 null
     */
    fun parseLatency(output: String, nowNanos: Long): Float? {
        val lines = output.lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .toList()
        if (lines.size < 2) {
            return null
        }
        val timestamps = lines.drop(1).mapNotNull { line ->
            val parts = line.split(WHITESPACE)
            if (parts.size < 3) {
                null
            } else {
                parts[1].toLongOrNull()
            }
        }.filter { timestamp -> timestamp != 0L && timestamp != INVALID_TIMESTAMP }
        if (timestamps.isEmpty()) {
            return null
        }
        val windowStart = nowNanos - WINDOW_NANOS
        val framesInWindow = timestamps.count { timestamp -> timestamp in windowStart..nowNanos }
        return framesInWindow.toFloat().coerceIn(0f, MAX_FPS)
    }

    /**
     * 解析 `--timestats -dump`。
     *
     * 直方图优先取 `averageFPS` 之后的第一层 `present2present`，没有时回退全局
     * `presentToPresent`；缺失字段保持 null，不把“没测到”当成 0。
     */
    fun parseTimeStats(output: String): TimeStats {
        val lines = output.lineSequence().map { line -> line.trim() }.toList()
        val totalFrames = TOTAL_FRAMES.find(output)?.groupValues?.get(1)?.toLongOrNull()
        val statsStart = STATS_START.find(output)?.groupValues?.get(1)?.toLongOrNull()
        val statsEnd = STATS_END.find(output)?.groupValues?.get(1)?.toLongOrNull()
        val derivedAverageFps = if (totalFrames != null && statsStart != null && statsEnd != null &&
            statsEnd > statsStart
        ) {
            (totalFrames.toDouble() / (statsEnd - statsStart).toDouble()).toFloat()
        } else {
            null
        }
        val averageFps = derivedAverageFps
            ?: AVERAGE_FPS.find(output)?.groupValues?.get(1)?.toFloatOrNull()
        val histogram = chooseHistogram(
            layer = layerHistogramPercentiles(lines),
            global = globalHistogramPercentiles(lines),
        )
        return TimeStats(
            totalFrames = totalFrames,
            missedFrames = MISSED_FRAMES.find(output)?.groupValues?.get(1)?.toLongOrNull(),
            averageFps = averageFps,
            frameTimeP95Millis = histogram?.p95Millis,
            frameTimeP99Millis = histogram?.p99Millis,
        )
    }

    private data class HistogramPercentiles(
        val p95Millis: Int?,
        val p99Millis: Int?,
        val samples: Long,
    )

    /**
     * 层直方图只在样本量不低于全局一半时使用；静态前台层可能只有 1 帧，
     * 直接采用会把 p95/p99 误报为 1000ms。
     */
    private fun chooseHistogram(
        layer: HistogramPercentiles?,
        global: HistogramPercentiles?,
    ): HistogramPercentiles? = when {
        layer == null -> global
        global == null -> layer
        layer.samples * 2 >= global.samples -> layer
        else -> global
    }

    private fun layerHistogramPercentiles(lines: List<String>): HistogramPercentiles? {
        val averageIndex = lines.indexOfFirst { line -> AVERAGE_FPS.containsMatchIn(line) }
        if (averageIndex < 0) {
            return null
        }
        val markerIndex = (averageIndex + 1 until lines.size).firstOrNull { index ->
            lines[index].contains(LAYER_HISTOGRAM_MARKER)
        } ?: return null
        return histogramPercentiles(lines, markerIndex)
    }

    private fun globalHistogramPercentiles(lines: List<String>): HistogramPercentiles? {
        val markerIndex = lines.indexOfFirst { line -> line.contains(GLOBAL_HISTOGRAM_MARKER) }
        return if (markerIndex < 0) null else histogramPercentiles(lines, markerIndex)
    }

    private fun histogramPercentiles(
        lines: List<String>,
        markerIndex: Int,
    ): HistogramPercentiles? {
        val sameLine = lines[markerIndex].substringAfter("as below:", "")
        val nextLine = lines.drop(markerIndex + 1).firstOrNull { line -> line.isNotBlank() }.orEmpty()
        val bucketLine = listOf(sameLine, nextLine).firstOrNull { line -> BUCKET.containsMatchIn(line) }
            ?: return null
        val buckets = BUCKET.findAll(bucketLine).mapNotNull { match ->
            val milliseconds = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val count = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (count > 0L) milliseconds to count else null
        }.sortedBy { bucket -> bucket.first }.toList()
        val total = buckets.sumOf { bucket -> bucket.second }
        if (total <= 0L) {
            return null
        }
        return HistogramPercentiles(
            p95Millis = percentile(buckets, total, 95),
            p99Millis = percentile(buckets, total, 99),
            samples = total,
        )
    }

    private fun percentile(buckets: List<Pair<Int, Long>>, total: Long, percentile: Int): Int {
        val rank = (total * percentile + 99L) / 100L
        var cumulative = 0L
        for ((milliseconds, count) in buckets) {
            cumulative += count
            if (cumulative >= rank) {
                return milliseconds
            }
        }
        return buckets.last().first
    }

    private const val MAX_FPS = 240f
}
