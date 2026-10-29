package com.t2m.monitor.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 指标展示格式化工具。
 *
 * 所有入参允许为 null（采集失败/设备不支持），统一返回 "--"。
 */
object FormatUtils {

    private const val UNAVAILABLE = "--"
    private const val BYTES_PER_UNIT = 1024.0

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    /** 将字节数格式化为 1.5 GB 形式。 */
    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes < 0L) {
            return UNAVAILABLE
        }
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= BYTES_PER_UNIT && unitIndex < UNITS.lastIndex) {
            value /= BYTES_PER_UNIT
            unitIndex++
        }
        return if (unitIndex == 0) {
            String.format(Locale.US, "%.0f %s", value, UNITS[unitIndex])
        } else {
            String.format(Locale.US, "%.1f %s", value, UNITS[unitIndex])
        }
    }

    /** 将 0..100 的百分比格式化为 62% 形式。 */
    fun formatPercent(percent: Float?): String =
        if (percent == null) UNAVAILABLE else String.format(Locale.US, "%.0f%%", percent)

    /** 将摄氏度格式化为 38.2 ℃。 */
    fun formatTemperature(celsius: Double?): String =
        if (celsius == null) UNAVAILABLE else String.format(Locale.US, "%.1f ℃", celsius)

    /** 将微安电流格式化为 mA，保留符号。 */
    fun formatCurrent(microAmps: Int?): String {
        if (microAmps == null) {
            return UNAVAILABLE
        }
        val milliAmps = microAmps / 1000.0
        return String.format(Locale.US, "%.0f mA", milliAmps)
    }

    /** 将毫伏电压格式化为 4.05 V。 */
    fun formatVoltage(milliVolts: Int?): String {
        if (milliVolts == null) {
            return UNAVAILABLE
        }
        return String.format(Locale.US, "%.2f V", milliVolts / 1000.0)
    }

    /** 将 kHz 频率格式化为 GHz；低于 1 GHz 时使用 MHz。 */
    fun formatFrequency(kiloHertz: Int?): String {
        if (kiloHertz == null || kiloHertz <= 0) {
            return UNAVAILABLE
        }
        return if (kiloHertz >= 1_000_000) {
            String.format(Locale.US, "%.2f GHz", kiloHertz / 1_000_000.0)
        } else {
            String.format(Locale.US, "%.0f MHz", kiloHertz / 1000.0)
        }
    }
    /** 悬浮球使用的紧凑温度格式（36°），无单位以适配 64dp 尺寸。 */
    fun formatTemperatureCompact(celsius: Double?): String =
        if (celsius == null) UNAVAILABLE else String.format(Locale.US, "%.0f°", celsius)

    /** 将毫秒时间戳格式化为 HH:mm:ss；0 视为无数据。 */
    fun formatTimeOfDay(timestampMillis: Long): String {
        if (timestampMillis <= 0L) {
            return UNAVAILABLE
        }
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
    }

    /** 将帧率格式化为 60 fps；null 显示占位符。 */
    fun formatFps(fps: Float?): String =
        if (fps == null) UNAVAILABLE else String.format(Locale.US, "%.0f fps", fps)
}
