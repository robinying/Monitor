package com.t2m.monitor.data.collector

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
