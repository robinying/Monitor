package com.robin.monitor.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.robin.monitor.data.model.MetricPoint
import kotlin.math.max

/**
 * 动态区间折线图。
 *
 * 横轴按样本时间戳相对首尾的比例定位，而非按点序号均分；相邻样本间隔明显大于
 * 常规采样间隔时断开曲线，避免把暂停/权限变化造成的空档画成连续趋势。
 * 纵轴按当前数据的最小/最大值自适应，并绘制 4 条水平网格线。
 */
@Composable
fun HistoryChart(
    points: List<MetricPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = lineColor.copy(alpha = 0.12f)
    Canvas(modifier = modifier) {
        val topPadding = 4.dp.toPx()
        val bottomPadding = 4.dp.toPx()
        val chartHeight = size.height - topPadding - bottomPadding
        if (chartHeight <= 0f) {
            return@Canvas
        }
        repeat(GRID_LINE_COUNT) { index ->
            val y = topPadding + chartHeight * index / (GRID_LINE_COUNT - 1).toFloat()
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        if (points.isEmpty()) {
            return@Canvas
        }
        if (points.size == 1) {
            val y = topPadding + chartHeight / 2f
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(size.width / 2f, y))
            return@Canvas
        }

        val minValue = points.minOf { point -> point.value }
        val maxValue = points.maxOf { point -> point.value }
        val range = max(maxValue - minValue, MIN_RANGE)
        val timestamps = points.map { point -> point.timestampMillis }
        val fractions = historyChartFractions(timestamps)
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun yOf(index: Int): Float {
            val fraction = ((points[index].value - minValue) / range).coerceIn(0f, 1f)
            return topPadding + chartHeight * (1f - fraction)
        }

        historyChartSegments(timestamps).forEach { segment ->
            if (segment.first == segment.last) {
                val index = segment.first
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = Offset(size.width * fractions[index], yOf(index)),
                )
                return@forEach
            }
            val linePath = Path()
            val fillPath = Path()
            var firstX = 0f
            segment.forEach { index ->
                val x = size.width * fractions[index]
                val y = yOf(index)
                if (index == segment.first) {
                    firstX = x
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(size.width * fractions[segment.last], size.height)
            fillPath.lineTo(firstX, size.height)
            fillPath.close()

            drawPath(path = fillPath, color = fillColor)
            drawPath(path = linePath, color = lineColor, style = stroke)
        }
    }
}

/** 按时间戳相对首尾的比例计算横轴位置；时间戳相同（或单点）时返回 0。 */
internal fun historyChartFractions(timestamps: List<Long>): List<Float> {
    if (timestamps.size <= 1) {
        return List(timestamps.size) { 0f }
    }
    val first = timestamps.first()
    val span = (timestamps.last() - first).coerceAtLeast(1L)
    return timestamps.map { timestamp ->
        (((timestamp - first).toDouble() / span).toFloat()).coerceIn(0f, 1f)
    }
}

/**
 * 按采样间隔把点分成连续段：间隔超过常规间隔 3 倍或时间倒退时断线。
 *
 * @return 每段对应的下标区间
 */
internal fun historyChartSegments(timestamps: List<Long>): List<IntRange> {
    if (timestamps.isEmpty()) {
        return emptyList()
    }
    val intervals = timestamps.zipWithNext { previous, next -> next - previous }
        .filter { interval -> interval > 0L }
        .sorted()
    val typicalInterval = intervals.getOrNull(intervals.size / 2)
    val threshold = if (typicalInterval == null) {
        Long.MAX_VALUE
    } else {
        (typicalInterval * SEGMENT_GAP_MULTIPLIER).coerceAtLeast(1L)
    }
    val segments = mutableListOf<IntRange>()
    var segmentStart = 0
    for (index in 1 until timestamps.size) {
        val interval = timestamps[index] - timestamps[index - 1]
        if (interval <= 0L || interval > threshold) {
            segments += segmentStart until index
            segmentStart = index
        }
    }
    segments += segmentStart until timestamps.size
    return segments
}

private const val GRID_LINE_COUNT = 4
private const val MIN_RANGE = 0.5f
private const val SEGMENT_GAP_MULTIPLIER = 3L
