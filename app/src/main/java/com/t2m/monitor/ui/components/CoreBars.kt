package com.t2m.monitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * 每核使用率柱状图，下标顺序与核心编号一致。
 *
 * 数据缺失（核心离线）的核心显示为空柱。
 */
@Composable
fun CoreBars(
    usages: List<Float?>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        if (usages.isEmpty()) {
            return@Canvas
        }
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (usages.size - 1)) / usages.size).coerceAtLeast(1f)
        val cornerRadius = CornerRadius(2.dp.toPx())
        usages.forEachIndexed { index, usage ->
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = cornerRadius,
            )
            val fraction = (usage ?: 0f).coerceIn(0f, 100f) / 100f
            if (fraction > 0f) {
                val barHeight = size.height * fraction
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius,
                )
            }
        }
    }
}
