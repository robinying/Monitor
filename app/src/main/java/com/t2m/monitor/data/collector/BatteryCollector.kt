package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.BatterySnapshot
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 电池指标采集器。
 *
 * 通过粘性广播读取，开销极低，按 1 秒周期采样。
 */
class BatteryCollector @Inject constructor(
    private val source: BatterySource,
) : Collector<BatterySnapshot> {

    override val id: CollectorId = CollectorId.BATTERY

    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    override suspend fun collect(): BatterySnapshot? = withContext(Dispatchers.IO) {
        source.read()?.let(BatteryReadingParser::parse)
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
    }
}
