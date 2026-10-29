package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.MemorySnapshot
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内存采集器：物理内存总量与可用量。
 */
class MemoryCollector @Inject constructor(
    private val source: MemorySource,
) : Collector<MemorySnapshot> {

    override val id: CollectorId = CollectorId.MEMORY

    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    override suspend fun collect(): MemorySnapshot? = withContext(Dispatchers.IO) {
        source.read()
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
    }
}
