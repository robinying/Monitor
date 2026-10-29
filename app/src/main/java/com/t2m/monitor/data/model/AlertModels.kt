package com.t2m.monitor.data.model

/** 告警类型。 */
enum class AlertType {
    BATTERY_TEMPERATURE_HIGH,
    BATTERY_LOW,
    MEMORY_LOW,
    STORAGE_LOW,
}

/**
 * 一条告警事件（领域模型）。
 *
 * @param threshold 触发阈值（温度 ℃ / 电量 % / 内存占用 % / 可用存储 GB）
 * @param actualValue 触发时的实际值，单位与阈值一致
 */
data class AlertEvent(
    val type: AlertType,
    val timestampMillis: Long,
    val threshold: Float,
    val actualValue: Float,
)

/** 从数据库读出的告警记录。 */
data class AlertRecord(
    val id: Long,
    val type: AlertType,
    val timestampMillis: Long,
    val threshold: Float,
    val actualValue: Float,
)
