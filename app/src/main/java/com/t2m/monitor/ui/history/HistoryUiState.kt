package com.t2m.monitor.ui.history

import com.t2m.monitor.data.model.AlertRecord
import com.t2m.monitor.data.model.HistoryRange
import com.t2m.monitor.data.model.MetricPoint
import com.t2m.monitor.data.model.MetricType
import com.t2m.monitor.domain.export.ExportFile

/**
 * 历史页界面状态。
 *
 * @param sampleCount 库中原始样本总数
 * @param exportEvent 导出成功后待处理的一次性事件（分享文件）
 * @param messageRes 待展示的提示文案资源
 */
data class HistoryUiState(
    val range: HistoryRange = HistoryRange.ONE_HOUR,
    val metric: MetricType = MetricType.BATTERY_LEVEL,
    val points: List<MetricPoint> = emptyList(),
    val minValue: Float? = null,
    val maxValue: Float? = null,
    val avgValue: Float? = null,
    val sampleCount: Long = 0L,
    val oldestTimestampMillis: Long? = null,
    val latestTimestampMillis: Long? = null,
    val alerts: List<AlertRecord> = emptyList(),
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val exportEvent: ExportFile? = null,
    val messageRes: Int? = null,
)
