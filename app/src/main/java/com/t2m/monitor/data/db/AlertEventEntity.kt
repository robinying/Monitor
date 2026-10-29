package com.t2m.monitor.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 告警事件表。
 */
@Entity(tableName = "alert_event")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "ts")
    val timestampMillis: Long,

    @ColumnInfo(name = "threshold")
    val threshold: Float,

    @ColumnInfo(name = "actual")
    val actualValue: Float,
)
