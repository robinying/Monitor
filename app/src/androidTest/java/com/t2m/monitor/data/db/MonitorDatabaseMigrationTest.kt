package com.t2m.monitor.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据库 v1 → v2 迁移：验证 alert_event 表被创建并可读写。
 */
@RunWith(AndroidJUnit4::class)
class MonitorDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun migratesV1ToV2AndCreatesAlertTable() {
        val databaseName = "migration-test-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val helper = createV1Helper(databaseName)
        try {
            val database = helper.writableDatabase
            MonitorDatabase.MIGRATION_1_2.migrate(database)

            database.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='alert_event'",
            ).use { cursor -> assertTrue("alert_event table missing", cursor.moveToFirst()) }

            database.execSQL(
                "INSERT INTO alert_event(type, ts, threshold, actual) VALUES ('BATTERY_LOW', 1, 20.0, 15.0)",
            )
            database.query("SELECT actual FROM alert_event").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(15.0f, cursor.getFloat(0), 0.01f)
            }
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createV1Helper(databaseName: String): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `metric_sample` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`metric` TEXT NOT NULL, `ts` INTEGER NOT NULL, `value` REAL NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_metric_sample_metric_ts` " +
                                "ON `metric_sample` (`metric`, `ts`)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `metric_hourly` (" +
                                "`metric` TEXT NOT NULL, `hour_ts` INTEGER NOT NULL, " +
                                "`min_value` REAL NOT NULL, `max_value` REAL NOT NULL, " +
                                "`avg_value` REAL NOT NULL, `sample_count` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`metric`, `hour_ts`))",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }
}
