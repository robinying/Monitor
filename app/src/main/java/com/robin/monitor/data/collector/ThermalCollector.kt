package com.robin.monitor.data.collector

import com.robin.monitor.data.model.ThermalSnapshot
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 温区采集器：读取全部 thermal_zone，输出最高温度与温区列表。
 */
class ThermalCollector @Inject constructor(
    private val source: ThermalSource,
) : Collector<ThermalSnapshot> {

    override val id: CollectorId = CollectorId.THERMAL

    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    override suspend fun collect(): ThermalSnapshot? = withContext(Dispatchers.IO) {
        val zones = source.readZones()?.sortedByDescending { zone -> zone.temperatureC }
        if (zones.isNullOrEmpty()) {
            null
        } else {
            val hottest = zones.first()
            val status = source.readStatus()
            ThermalSnapshot(
                maxTemperatureC = hottest.temperatureC,
                hottestZone = hottest.name,
                zones = zones,
                status = status?.status,
                headroom = status?.headroom,
            )
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5_000L
    }
}
