package com.robin.monitor.data.db

import androidx.room.ColumnInfo

/**
 * 按时间桶聚合的查询结果。
 */
data class MetricBucketRow(
    @ColumnInfo(name = "bucket_ts")
    val bucketTimestampMillis: Long,

    @ColumnInfo(name = "avg_value")
    val avgValue: Float,
)

/** 原始样本区间统计查询结果。 */
data class MetricStatsRow(
    @ColumnInfo(name = "min_value")
    val minValue: Float?,

    @ColumnInfo(name = "max_value")
    val maxValue: Float?,

    @ColumnInfo(name = "avg_value")
    val avgValue: Float?,
)
