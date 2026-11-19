package com.t2m.monitor.data.collector

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.model.ThermalStatusLevel
import com.t2m.monitor.data.model.ThermalZone
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** 系统热状态读数。 */
data class ThermalStatusReading(
    val status: ThermalStatusLevel?,
    val headroom: Float?,
)

/** 温区数据来源，便于测试替换。 */
interface ThermalSource {

    /** 读取全部可解析的温区；目录不可读时返回 null。 */
    fun readZones(): List<ThermalZone>?

    /** 读取系统热状态与余量；API 不支持或不可读时返回 null。 */
    fun readStatus(): ThermalStatusReading? = null
}

/** 生产实现：sysfs 温区 + PowerManager 热状态。 */
class AndroidThermalSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: MonitorLogger,
) : ThermalSource {

    @Volatile
    private var readFailureLogged = false

    @Volatile
    private var statusFailureLogged = false

    override fun readZones(): List<ThermalZone>? = try {
        val thermalDirectory = File(THERMAL_DIRECTORY)
        if (!thermalDirectory.isDirectory) {
            emptyList()
        } else {
            val zones = thermalDirectory.listFiles { file -> file.name.startsWith(ZONE_PREFIX) }
                .orEmpty()
                .sortedBy { file -> file.name }
                .mapNotNull { zoneDirectory -> readZone(zoneDirectory) }
            readFailureLogged = false
            zones
        }
    } catch (error: Exception) {
        if (!readFailureLogged) {
            logger.warn("read thermal zones failed", error)
            readFailureLogged = true
        }
        null
    }

    override fun readStatus(): ThermalStatusReading? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return null
        return try {
            val status = ThermalParser.parseStatus(powerManager.currentThermalStatus)
            val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ThermalParser.classifyHeadroom(powerManager.getThermalHeadroom(0))
            } else {
                null
            }
            if (status == null && headroom == null) {
                null
            } else {
                ThermalStatusReading(status = status, headroom = headroom)
            }
        } catch (error: Exception) {
            if (!statusFailureLogged) {
                logger.warn("read thermal status failed", error)
                statusFailureLogged = true
            }
            null
        }
    }

    private fun readZone(zoneDirectory: File): ThermalZone? {
        val name = runCatching { File(zoneDirectory, TYPE_FILE).readText().trim() }.getOrNull()
            ?: return null
        if (!ThermalParser.isTemperatureZone(name)) {
            return null
        }
        val rawTemperature = runCatching {
            File(zoneDirectory, TEMPERATURE_FILE).readText().trim().toFloat()
        }.getOrNull() ?: return null
        val celsius = ThermalParser.normalize(rawTemperature) ?: return null
        return ThermalZone(
            name = name,
            temperatureC = celsius,
            kind = ThermalParser.classifyZone(name),
        )
    }

    private companion object {
        const val THERMAL_DIRECTORY = "/sys/class/thermal"
        const val ZONE_PREFIX = "thermal_zone"
        const val TYPE_FILE = "type"
        const val TEMPERATURE_FILE = "temp"
    }
}
