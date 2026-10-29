package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.AppStorageUsage
import com.t2m.monitor.data.privileged.AppStorageSource
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用存储占用采集器。
 *
 * 需要逐个包查询 StorageStats，开销较大，按 60 秒采样，只保留前 5 个应用。
 */
class AppStorageCollector @Inject constructor(
    private val source: AppStorageSource,
) : Collector<List<AppStorageUsage>> {

    override val id: CollectorId = CollectorId.APP_STORAGE

    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    override fun isAvailable(): Boolean = source.isAvailable()

    override suspend fun collect(): List<AppStorageUsage>? = withContext(Dispatchers.IO) {
        source.readTopApps(TOP_APP_LIMIT)?.takeIf { apps -> apps.isNotEmpty() }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 60_000L
        const val TOP_APP_LIMIT = 5
    }
}
