package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.BatteryStatus
import com.t2m.monitor.data.model.PlugState
import kotlin.math.roundToInt

/**
 * 将系统原始电池读数转换为界面模型。
 *
 * 状态与接入方式的取值来自 Android 公开常量，这里以私有常量镜像，避免单元测试依赖 android.jar。
 */
object BatteryReadingParser {

    private const val STATUS_CHARGING = 2
    private const val STATUS_DISCHARGING = 3
    private const val STATUS_NOT_CHARGING = 4
    private const val STATUS_FULL = 5

    private const val PLUGGED_AC = 1
    private const val PLUGGED_USB = 2
    private const val PLUGGED_WIRELESS = 4
    private const val PLUGGED_DOCK = 8

    private const val TEMPERATURE_INVALID_MAX = 0

    /**
     * @param reading 原始读数
     * @return 归一化快照；电量无法计算时 [BatterySnapshot.percent] 为 null
     */
    fun parse(reading: BatteryReading): BatterySnapshot = BatterySnapshot(
        percent = calculatePercent(reading.level, reading.scale),
        temperatureC = reading.temperatureTenthsCelsius
            ?.takeIf { it > TEMPERATURE_INVALID_MAX }
            ?.div(10.0),
        currentMicroAmps = reading.currentMicroAmps,
        voltageMv = reading.voltageMv,
        status = mapStatus(reading.status),
        plugged = mapPlugged(reading.plugged),
        health = reading.health,
    )

    private fun calculatePercent(level: Int, scale: Int): Int? {
        if (level < 0 || scale <= 0) {
            return null
        }
        return (level * 100.0 / scale).roundToInt().coerceIn(0, 100)
    }

    private fun mapStatus(status: Int): BatteryStatus = when (status) {
        STATUS_CHARGING -> BatteryStatus.CHARGING
        STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
        STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
        STATUS_FULL -> BatteryStatus.FULL
        else -> BatteryStatus.UNKNOWN
    }

    private fun mapPlugged(plugged: Int): PlugState = when {
        plugged and PLUGGED_WIRELESS != 0 -> PlugState.WIRELESS
        plugged and PLUGGED_DOCK != 0 -> PlugState.DOCK
        plugged and PLUGGED_USB != 0 -> PlugState.USB
        plugged and PLUGGED_AC != 0 -> PlugState.AC
        else -> PlugState.NONE
    }
}
