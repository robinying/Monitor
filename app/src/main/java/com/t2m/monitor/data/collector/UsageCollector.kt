package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.UsageSnapshot
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 使用情况采集器：前台应用与今日流量。
 *
 * 查询开销高于电池/CPU，按 5 秒采样。
 */
class UsageCollector @Inject constructor(
    private val source: UsageSource,
) : Collector<UsageSnapshot> {

    override val id: CollectorId = CollectorId.USAGE

    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    override fun isAvailable(): Boolean = source.isAvailable()

    override suspend fun collect(): UsageSnapshot? = withContext(Dispatchers.IO) {
        source.read()
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5_000L
    }
}
