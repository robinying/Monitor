package com.t2m.monitor.data.collector

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.t2m.monitor.core.MonitorLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * 电池原始读数。
 *
 * 保持与系统字段一一对应（温度单位为 0.1 ℃），由 [BatteryReadingParser] 做归一化，
 * 使解析逻辑可在纯 JVM 单元测试中覆盖。
 */
data class BatteryReading(
    val level: Int,
    val scale: Int,
    val temperatureTenthsCelsius: Int?,
    val voltageMv: Int?,
    val currentMicroAmps: Int?,
    val status: Int,
    val plugged: Int,
    val health: Int?,
    val technology: String? = null,
    val cycleCount: Int? = null,
    val designCapacityMah: Int? = null,
)

/** 电池数据来源，便于测试替换。 */
interface BatterySource {

    fun read(): BatteryReading?
}

/** 生产实现：读取 `ACTION_BATTERY_CHANGED` 粘性广播与 [BatteryManager] 属性。 */
class AndroidBatterySource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: MonitorLogger,
) : BatterySource {

    @Volatile
    private var sysfsFailureLogged = false

    override fun read(): BatteryReading? {
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val batteryManager = context.getSystemService(BatteryManager::class.java)

        return BatteryReading(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, INVALID_VALUE),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, INVALID_VALUE),
            temperatureTenthsCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, INVALID_VALUE)
                .takeIf { it != INVALID_VALUE },
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, INVALID_VALUE)
                .takeIf { it != INVALID_VALUE },
            currentMicroAmps = batteryManager
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                ?.takeIf { it != Int.MIN_VALUE },
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, INVALID_VALUE),
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, INVALID_VALUE)
                .takeIf { it != INVALID_VALUE },
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
                ?.takeIf { value -> value.isNotBlank() },
            cycleCount = readCycleCount(intent),
            designCapacityMah = readDesignCapacityMah(),
        )
    }

    private fun readCycleCount(intent: Intent): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // BatteryManager.EXTRA_CYCLE_COUNT（API 34），使用常量值避免低版本类校验问题。
            intent.getIntExtra("android.os.extra.CYCLE_COUNT", INVALID_VALUE)
                .takeIf { value -> value != INVALID_VALUE }
                ?.let { return it }
        }
        return readIntFile("/sys/class/power_supply/battery/cycle_count")
    }

    private fun readDesignCapacityMah(): Int? {
        val microAmpHours = readLongFile("/sys/class/power_supply/battery/charge_full_design")
            ?: readLongFile("/sys/class/power_supply/battery/charge_full")
            ?: return null
        return (microAmpHours / 1000L).toInt().takeIf { value -> value > 0 }
    }

    private fun readIntFile(path: String): Int? = readLongFile(path)?.toInt()?.takeIf { it > 0 }

    private fun readLongFile(path: String): Long? = try {
        File(path).readText().trim().toLongOrNull()
    } catch (error: Exception) {
        if (!sysfsFailureLogged) {
            logger.warn("read battery sysfs failed: $path", error)
            sysfsFailureLogged = true
        }
        null
    }

    private companion object {
        const val INVALID_VALUE = -1
    }
}
