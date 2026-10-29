package com.t2m.monitor.data.repository

import com.t2m.monitor.data.db.AlertDao
import com.t2m.monitor.data.db.AlertEventEntity
import com.t2m.monitor.data.model.AlertEvent
import com.t2m.monitor.data.model.AlertRecord
import com.t2m.monitor.data.model.AlertType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 告警事件仓库。
 */
@Singleton
class AlertRepository @Inject constructor(
    private val alertDao: AlertDao,
) {

    suspend fun insert(event: AlertEvent): Long = alertDao.insert(
        AlertEventEntity(
            type = event.type.name,
            timestampMillis = event.timestampMillis,
            threshold = event.threshold,
            actualValue = event.actualValue,
        ),
    )

    /** 最近的告警，按时间倒序；未知类型的历史记录直接跳过。 */
    suspend fun recent(limit: Int = RECENT_LIMIT): List<AlertRecord> = alertDao
        .recent(limit)
        .mapNotNull { entity ->
            val type = AlertType.entries.firstOrNull { it.name == entity.type } ?: return@mapNotNull null
            AlertRecord(
                id = entity.id,
                type = type,
                timestampMillis = entity.timestampMillis,
                threshold = entity.threshold,
                actualValue = entity.actualValue,
            )
        }

    suspend fun clearAll() {
        alertDao.clear()
    }

    private companion object {
        const val RECENT_LIMIT = 20
    }
}
