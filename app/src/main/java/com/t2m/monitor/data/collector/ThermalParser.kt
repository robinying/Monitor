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
