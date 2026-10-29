package com.t2m.monitor.ui.history

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
import com.t2m.monitor.data.model.MetricPoint
import kotlin.math.max

/**
 * 动态区间折线图。
 *
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

        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { index, point ->
            val x = size.width * index / points.lastIndex
            val fraction = ((point.value - minValue) / range).coerceIn(0f, 1f)
            val y = topPadding + chartHeight * (1f - fraction)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(path = fillPath, color = fillColor)
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private const val GRID_LINE_COUNT = 4
private const val MIN_RANGE = 0.5f
