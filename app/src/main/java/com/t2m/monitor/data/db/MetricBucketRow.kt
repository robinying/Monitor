package com.t2m.monitor.data.db

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
