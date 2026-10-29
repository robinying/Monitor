package com.t2m.monitor.domain.alert

import com.t2m.monitor.data.model.AlertEvent
import com.t2m.monitor.data.model.AlertType
import com.t2m.monitor.data.model.MonitorSnapshot

/**
 * 阈值判定。
 *
 * 规则：
 * 1. 电池温度 ≥ 45 ℃、内存占用 ≥ 90%：连续 3 次采样满足才告警，避免瞬时毛刺；
 * 2. 电量 ≤ 20%、可用存储 < 2 GB：单次满足即告警；
 * 3. 同一类型告警在去抖窗口内只提醒一次（默认 30 分钟，存储 60 分钟）。
 *
 * 纯逻辑实现，便于用虚拟时间做单元测试。
 */
class ThresholdEvaluator(
    private val requiredConsecutiveSamples: Int = DEFAULT_CONSECUTIVE_SAMPLES,
    private val defaultDebounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val storageDebounceMillis: Long = STORAGE_DEBOUNCE_MILLIS,
) {

    private val consecutiveCounts = mutableMapOf<AlertType, Int>()

    private val lastAlertAtMillis = mutableMapOf<AlertType, Long>()

    fun evaluate(snapshot: MonitorSnapshot, timestampMillis: Long): List<AlertEvent> {
        val events = mutableListOf<AlertEvent>()

        evaluateConsecutive(
            type = AlertType.BATTERY_TEMPERATURE_HIGH,
            value = snapshot.battery?.temperatureC?.toFloat(),
            threshold = BATTERY_TEMPERATURE_THRESHOLD_C,
            trigger = { value -> value >= BATTERY_TEMPERATURE_THRESHOLD_C },
            timestampMillis = timestampMillis,
            debounceMillis = defaultDebounceMillis,
            events = events,
        )
        evaluateConsecutive(
            type = AlertType.MEMORY_LOW,
            value = snapshot.memory?.usedPercent,
            threshold = MEMORY_USED_PERCENT_THRESHOLD,
            trigger = { value -> value >= MEMORY_USED_PERCENT_THRESHOLD },
            timestampMillis = timestampMillis,
            debounceMillis = defaultDebounceMillis,
            events = events,
        )
        evaluateSingle(
            type = AlertType.BATTERY_LOW,
            value = snapshot.battery?.percent?.toFloat(),
            threshold = BATTERY_LOW_PERCENT_THRESHOLD,
            trigger = { value -> value <= BATTERY_LOW_PERCENT_THRESHOLD },
            timestampMillis = timestampMillis,
            debounceMillis = defaultDebounceMillis,
            events = events,
        )
        evaluateSingle(
            type = AlertType.STORAGE_LOW,
            value = snapshot.storage?.availableBytes?.let { bytes -> bytes / GIGABYTE },
            threshold = STORAGE_LOW_GB_THRESHOLD,
            trigger = { value -> value < STORAGE_LOW_GB_THRESHOLD },
            timestampMillis = timestampMillis,
            debounceMillis = storageDebounceMillis,
            events = events,
        )
        return events
    }

    private fun evaluateConsecutive(
        type: AlertType,
        value: Float?,
        threshold: Float,
        trigger: (Float) -> Boolean,
        timestampMillis: Long,
        debounceMillis: Long,
        events: MutableList<AlertEvent>,
    ) {
        if (value == null) {
            consecutiveCounts.remove(type)
            return
        }
        if (!trigger(value)) {
            consecutiveCounts[type] = 0
            return
        }
        val count = (consecutiveCounts[type] ?: 0) + 1
        consecutiveCounts[type] = count
        if (count >= requiredConsecutiveSamples) {
            emitIfAllowed(type, timestampMillis, threshold, value, debounceMillis, events)
        }
    }

    private fun evaluateSingle(
        type: AlertType,
        value: Float?,
        threshold: Float,
        trigger: (Float) -> Boolean,
        timestampMillis: Long,
        debounceMillis: Long,
        events: MutableList<AlertEvent>,
    ) {
        if (value == null || !trigger(value)) {
            return
        }
        emitIfAllowed(type, timestampMillis, threshold, value, debounceMillis, events)
    }

    private fun emitIfAllowed(
        type: AlertType,
        timestampMillis: Long,
        threshold: Float,
        actualValue: Float,
        debounceMillis: Long,
        events: MutableList<AlertEvent>,
    ) {
        val lastAlertAt = lastAlertAtMillis[type]
        if (lastAlertAt != null && timestampMillis - lastAlertAt < debounceMillis) {
            return
        }
        lastAlertAtMillis[type] = timestampMillis
        events += AlertEvent(
            type = type,
            timestampMillis = timestampMillis,
            threshold = threshold,
            actualValue = actualValue,
        )
    }

    private companion object {
        const val DEFAULT_CONSECUTIVE_SAMPLES = 3
        const val DEFAULT_DEBOUNCE_MILLIS = 30L * 60 * 1000
        const val STORAGE_DEBOUNCE_MILLIS = 60L * 60 * 1000

        const val BATTERY_TEMPERATURE_THRESHOLD_C = 45f
        const val MEMORY_USED_PERCENT_THRESHOLD = 90f
        const val BATTERY_LOW_PERCENT_THRESHOLD = 20f
        const val STORAGE_LOW_GB_THRESHOLD = 2f
        const val GIGABYTE = 1024f * 1024f * 1024f
    }
}
