package com.t2m.monitor.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 本地时序数据库。
 */
@Database(
    entities = [MetricSampleEntity::class, MetricHourlyEntity::class, AlertEventEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MonitorDatabase : RoomDatabase() {

    abstract fun metricDao(): MetricDao

    abstract fun alertDao(): AlertDao

    companion object {
        /** v1 → v2：新增告警事件表。 */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alert_event` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`ts` INTEGER NOT NULL, " +
                        "`threshold` REAL NOT NULL, " +
                        "`actual` REAL NOT NULL)",
                )
            }
        }
    }
}
