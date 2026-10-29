package com.t2m.monitor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * 告警事件访问。
 */
@Dao
interface AlertDao {

    @Insert
    suspend fun insert(event: AlertEventEntity): Long

    @Query("SELECT * FROM alert_event ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AlertEventEntity>

    @Query("DELETE FROM alert_event")
    suspend fun clear(): Int
}
