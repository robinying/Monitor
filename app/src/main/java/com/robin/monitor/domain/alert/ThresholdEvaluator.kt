package com.robin.monitor.domain.alert

import com.robin.monitor.data.collector.CollectorId
import com.robin.monitor.data.model.AlertEvent
import com.robin.monitor.data.model.AlertType
import com.robin.monitor.data.model.MonitorSnapshot

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

    /** 去抖使用单调时钟，系统时间校准不会拉长或缩短冷却窗口。 */
    private val lastAlertElapsedMillis = mutableMapOf<AlertType, Long>()

    private var lastEvaluateElapsedMillis: Long? = null

    /**
     * 判定一次快照。
     *
     * @param timestampMillis 快照的墙上时间，用于告警事件落库
     * @param monotonicMillis 单调时钟，仅用于去抖窗口计算
     */
    fun evaluate(
        snapshot: MonitorSnapshot,
        timestampMillis: Long,
        monotonicMillis: Long = timestampMillis,
    ): List<AlertEvent> {
        // 暂停/停止造成长时间没有新快照时，暂停前后的样本不应连成“连续三次”。
        val lastElapsed = lastEvaluateElapsedMillis
        if (lastElapsed != null &&
            (monotonicMillis - lastElapsed > CONSECUTIVE_RESET_GAP_MILLIS || monotonicMillis < lastElapsed)
        ) {
            consecutiveCounts.clear()
        }
        lastEvaluateElapsedMillis = monotonicMillis

        val events = mutableListOf<AlertEvent>()

        evaluateConsecutive(
            type = AlertType.BATTERY_TEMPERATURE_HIGH,
            value = snapshot.battery?.temperatureC?.toFloat(),
            threshold = BATTERY_TEMPERATURE_THRESHOLD_C,
            trigger = { value -> value >= BATTERY_TEMPERATURE_THRESHOLD_C },
            timestampMillis = timestampMillis,
            monotonicMillis = monotonicMillis,
            fresh = isFresh(snapshot, CollectorId.BATTERY, timestampMillis),
            debounceMillis = defaultDebounceMillis,
            events = events,
        )
        evaluateConsecutive(
            type = AlertType.MEMORY_LOW,
            value = snapshot.memory?.usedPercent,
            threshold = MEMORY_USED_PERCENT_THRESHOLD,
            trigger = { value -> value >= MEMORY_USED_PERCENT_THRESHOLD },
            timestampMillis = timestampMillis,
            monotonicMillis = monotonicMillis,
            fresh = isFresh(snapshot, CollectorId.MEMORY, timestampMillis),
            debounceMillis = defaultDebounceMillis,
            events = events,
        )
        evaluateSingle(
            type = AlertType.BATTERY_LOW,
            value = snapshot.battery?.percent?.toFloat(),
            threshold = BATTERY_LOW_PERCENT_THRESHOLD,
            trigger = { value -> value <= BATTERY_LOW_PERCENT_THRESHOLD },
            timestampMillis = timestampMillis,
            monotonicMillis = monotonicMillis,
            fresh = isFresh(snapshot, CollectorId.BATTERY, timestampMillis),
            debounceMillis = defaultDebounceMillis,
            events = events,
        )
        evaluateSingle(
            type = AlertType.STORAGE_LOW,
            value = snapshot.storage?.availableBytes?.let { bytes -> bytes / GIGABYTE },
            threshold = STORAGE_LOW_GB_THRESHOLD,
            trigger = { value -> value < STORAGE_LOW_GB_THRESHOLD },
            timestampMillis = timestampMillis,
            monotonicMillis = monotonicMillis,
            fresh = isFresh(snapshot, CollectorId.STORAGE, timestampMillis),
            debounceMillis = storageDebounceMillis,
            events = events,
        )
        return events
    }

    /** 清空连续计数（服务重新启动后必须重新积累新样本），去抖记录保留。 */
    fun resetConsecutiveCounts() {
        consecutiveCounts.clear()
    }

    /**
     * 只有本轮新采集到的指标才参与判定；引擎保留的旧值不能充当新的连续样本。
     * 空的时间戳映射（旧数据/测试构造）按“新鲜”处理。
     */
    private fun isFresh(
        snapshot: MonitorSnapshot,
        collectorId: CollectorId,
        timestampMillis: Long,
    ): Boolean {
        if (snapshot.sampleTimestampsMillis.isEmpty()) {
            return true
        }
        return snapshot.sampleTimestampsMillis[collectorId] == timestampMillis
    }

    private fun evaluateConsecutive(
        type: AlertType,
        value: Float?,
        threshold: Float,
        trigger: (Float) -> Boolean,
        timestampMillis: Long,
        monotonicMillis: Long,
        fresh: Boolean,
        debounceMillis: Long,
        events: MutableList<AlertEvent>,
    ) {
        if (!fresh) {
            return
        }
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
            emitIfAllowed(type, timestampMillis, monotonicMillis, threshold, value, debounceMillis, events)
        }
    }

    private fun evaluateSingle(
        type: AlertType,
        value: Float?,
        threshold: Float,
        trigger: (Float) -> Boolean,
        timestampMillis: Long,
        monotonicMillis: Long,
        fresh: Boolean,
        debounceMillis: Long,
        events: MutableList<AlertEvent>,
    ) {
        if (!fresh || value == null || !trigger(value)) {
            return
        }
        emitIfAllowed(type, timestampMillis, monotonicMillis, threshold, value, debounceMillis, events)
    }

    private fun emitIfAllowed(
        type: AlertType,
        timestampMillis: Long,
        monotonicMillis: Long,
        threshold: Float,
        actualValue: Float,
        debounceMillis: Long,
        events: MutableList<AlertEvent>,
    ) {
        val lastAlertElapsed = lastAlertElapsedMillis[type]
        if (lastAlertElapsed != null && monotonicMillis - lastAlertElapsed < debounceMillis) {
            return
        }
        lastAlertElapsedMillis[type] = monotonicMillis
        events += AlertEvent(
            type = type,
            timestampMillis = timestampMillis,
            threshold = threshold,
            actualValue = actualValue,
        )
    }

    private companion object {
        const val DEFAULT_CONSECUTIVE_SAMPLES = 3
        const val CONSECUTIVE_RESET_GAP_MILLIS = 10_000L
        const val DEFAULT_DEBOUNCE_MILLIS = 30L * 60 * 1000
        const val STORAGE_DEBOUNCE_MILLIS = 60L * 60 * 1000

        const val BATTERY_TEMPERATURE_THRESHOLD_C = 45f
        const val MEMORY_USED_PERCENT_THRESHOLD = 90f
        const val BATTERY_LOW_PERCENT_THRESHOLD = 20f
        const val STORAGE_LOW_GB_THRESHOLD = 2f
        const val GIGABYTE = 1024f * 1024f * 1024f
    }
}
