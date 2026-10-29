package com.t2m.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 语义颜色：根据数值危险程度返回主色 / 警告色 / 错误色。
 */
@Composable
fun batteryLevelColor(percent: Int?): Color = when {
    percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
    percent <= 20 -> MaterialTheme.colorScheme.error
    percent <= 40 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun temperatureColor(celsius: Double?): Color = when {
    celsius == null -> MaterialTheme.colorScheme.onSurfaceVariant
    celsius >= 45.0 -> MaterialTheme.colorScheme.error
    celsius >= 40.0 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun usageColor(percent: Float?): Color = when {
    percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
    percent >= 90f -> MaterialTheme.colorScheme.error
    percent >= 75f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun availableStorageColor(availableBytes: Long?): Color = when {
    availableBytes == null -> MaterialTheme.colorScheme.onSurfaceVariant
    availableBytes < 2L * 1024 * 1024 * 1024 -> MaterialTheme.colorScheme.error
    availableBytes < 8L * 1024 * 1024 * 1024 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}
