package com.t2m.monitor.data.collector

import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.model.ThermalZone
import java.io.File
import javax.inject.Inject

/** 温区数据来源，便于测试替换。 */
interface ThermalSource {

    /** 读取全部可解析的温区；目录不可读时返回 null。 */
    fun readZones(): List<ThermalZone>?
}

/** 生产实现：读取 `/sys/class/thermal/thermal_zone*`。 */
class AndroidThermalSource @Inject constructor(
    private val logger: MonitorLogger,
) : ThermalSource {

    @Volatile
    private var readFailureLogged = false

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

    private fun readZone(zoneDirectory: File): ThermalZone? {
        val name = runCatching { File(zoneDirectory, TYPE_FILE).readText().trim() }.getOrNull()
            ?: return null
        val rawTemperature = runCatching {
            File(zoneDirectory, TEMPERATURE_FILE).readText().trim().toFloat()
        }.getOrNull() ?: return null
        val celsius = ThermalParser.normalize(rawTemperature) ?: return null
        return ThermalZone(name = name, temperatureC = celsius)
    }

    private companion object {
        const val THERMAL_DIRECTORY = "/sys/class/thermal"
        const val ZONE_PREFIX = "thermal_zone"
        const val TYPE_FILE = "type"
        const val TEMPERATURE_FILE = "temp"
    }
}
