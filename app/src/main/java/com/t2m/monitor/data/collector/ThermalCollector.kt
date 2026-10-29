package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.ThermalSnapshot
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
            ThermalSnapshot(
                maxTemperatureC = hottest.temperatureC,
                hottestZone = hottest.name,
                zones = zones,
            )
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5_000L
    }
}
