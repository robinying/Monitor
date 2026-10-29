package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.StorageSnapshot
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 存储采集器。
 *
 * 容量变化缓慢，按 60 秒采样以降低开销。
 */
class StorageCollector @Inject constructor(
    private val source: StorageSource,
) : Collector<StorageSnapshot> {

    override val id: CollectorId = CollectorId.STORAGE

    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    override suspend fun collect(): StorageSnapshot? = withContext(Dispatchers.IO) {
        source.read()
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 60_000L
    }
}
