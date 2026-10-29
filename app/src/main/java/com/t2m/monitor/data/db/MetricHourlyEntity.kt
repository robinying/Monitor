package com.t2m.monitor.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 小时级聚合，原始样本清理后仍保留 90 天。
 *
 * 预留给后续 >7 天历史范围；当前历史页最大范围为 7 天，不直接查询本表。
 */
@Entity(
    tableName = "metric_hourly",
    primaryKeys = ["metric", "hour_ts"],
)
data class MetricHourlyEntity(
    @ColumnInfo(name = "metric")
    val metric: String,

    @ColumnInfo(name = "hour_ts")
    val hourTimestampMillis: Long,

    @ColumnInfo(name = "min_value")
    val minValue: Float,

    @ColumnInfo(name = "max_value")
    val maxValue: Float,

    @ColumnInfo(name = "avg_value")
    val avgValue: Float,

    @ColumnInfo(name = "sample_count")
    val sampleCount: Int,
)
