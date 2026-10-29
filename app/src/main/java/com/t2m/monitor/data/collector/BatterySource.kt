package com.t2m.monitor.data.collector

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
)

/** 电池数据来源，便于测试替换。 */
interface BatterySource {

    fun read(): BatteryReading?
}

/** 生产实现：读取 `ACTION_BATTERY_CHANGED` 粘性广播与 [BatteryManager] 属性。 */
class AndroidBatterySource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BatterySource {

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
        )
    }

    private companion object {
        const val INVALID_VALUE = -1
    }
}
