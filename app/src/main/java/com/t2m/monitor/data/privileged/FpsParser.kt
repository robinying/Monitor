package com.t2m.monitor.data.privileged

/**
 * `dumpsys SurfaceFlinger` 输出解析。
 *
 * FPS 计算方式：从 `dumpsys SurfaceFlinger --latency <layer>` 的帧时间戳中统计最近 1 秒的帧数。
 * 输出格式为：首行刷新周期（ns），其后每行 "desired present / actual present / ready"。
 */
object FpsParser {

    private val WHITESPACE = Regex("\\s+")

    private const val INVALID_TIMESTAMP = Long.MAX_VALUE
    private const val WINDOW_NANOS = 1_000_000_000L

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

    private const val MAX_FPS = 240f
}
