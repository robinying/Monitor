package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.ThermalStatusLevel
import com.t2m.monitor.data.model.ThermalZoneKind

/**
 * 温区原始值归一化。
 *
 * 不同厂商的单位不一致：常见为毫摄氏度（45000），少数为摄氏度（45）或十分之一度（450），
 * 这里按量级推断并过滤明显无效的读数。
 */
object ThermalParser {

    private const val MIN_VALID_CELSIUS = 0.1f
    private const val MAX_VALID_CELSIUS = 150f

    private val EXCLUDED_ZONE_TOKENS = listOf(
        "vbat",
        "ibat",
        "current",
        "voltage",
        "bcl",
        "fan",
        "power",
    )

    /**
     * 判断温区 type 是否为温度传感器。
     *
     * 部分平台把电压/电流/充电等级也注册成 thermal_zone（如 `vbat`、`ibat`、
     * `pmxr2230-bcl-lvl0`），其 temp 字段并非摄氏度，需要排除，否则会出现 99℃ 之类的误报。
     */
    fun isTemperatureZone(zoneType: String): Boolean {
        val normalized = zoneType.lowercase()
        return EXCLUDED_ZONE_TOKENS.none { token -> normalized.contains(token) }
    }

    /** 按温区 type 推断用途，用于分区展示与诊断。 */
    fun classifyZone(zoneType: String): ThermalZoneKind {
        val normalized = zoneType.lowercase()
        return when {
            containsAny(normalized, "gpu", "kgsl", "mali") -> ThermalZoneKind.GPU
            containsAny(normalized, "battery", "batt") -> ThermalZoneKind.BATTERY
            containsAny(normalized, "skin", "case", "back", "shell", "frame", "board") ->
                ThermalZoneKind.SKIN

            containsAny(normalized, "cpu", "cluster", "core", "soc") -> ThermalZoneKind.CPU
            else -> ThermalZoneKind.OTHER
        }
    }

    /** 映射 PowerManager.THERMAL_STATUS_*；未知值返回 null。 */
    fun parseStatus(rawStatus: Int): ThermalStatusLevel? = when (rawStatus) {
        0 -> ThermalStatusLevel.NONE
        1 -> ThermalStatusLevel.LIGHT
        2 -> ThermalStatusLevel.MODERATE
        3 -> ThermalStatusLevel.SEVERE
        4 -> ThermalStatusLevel.CRITICAL
        5 -> ThermalStatusLevel.EMERGENCY
        6 -> ThermalStatusLevel.SHUTDOWN
        else -> null
    }

    /** NaN/负数/无穷表示 HAL 未提供数据，返回 null 而不是 0。 */
    fun classifyHeadroom(rawHeadroom: Float): Float? =
        rawHeadroom.takeIf { value -> value.isFinite() && value >= 0f }

    private fun containsAny(value: String, vararg tokens: String): Boolean =
        tokens.any { token -> value.contains(token) }

    fun normalize(rawValue: Float): Float? {
        if (rawValue <= 0f) {
            return null
        }
        val celsius = when {
            rawValue > 1_000f -> rawValue / 1_000f
            rawValue > 100f -> rawValue / 10f
            else -> rawValue
        }
        return celsius.takeIf { it in MIN_VALID_CELSIUS..MAX_VALID_CELSIUS }
    }
}
