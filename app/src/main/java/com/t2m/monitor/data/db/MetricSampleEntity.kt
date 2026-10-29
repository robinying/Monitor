package com.t2m.monitor.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 原始采样（10 秒窗口聚合后的平均值）。
 */
@Entity(
    tableName = "metric_sample",
    indices = [Index(value = ["metric", "ts"])],
)
data class MetricSampleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "metric")
    val metric: String,

    @ColumnInfo(name = "ts")
    val timestampMillis: Long,

    @ColumnInfo(name = "value")
    val value: Float,
)
