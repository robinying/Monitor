package com.t2m.monitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 简易折线图。
 *
 * 数值固定按 0..100 百分比映射，少于 2 个点时只画基线。
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val baselineColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val verticalPadding = 2.dp.toPx()
        val chartHeight = size.height - verticalPadding * 2
        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        if (values.size < 2) {
            return@Canvas
        }
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / values.lastIndex
            val fraction = value.coerceIn(0f, 100f) / 100f
            val y = verticalPadding + chartHeight * (1f - fraction)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
