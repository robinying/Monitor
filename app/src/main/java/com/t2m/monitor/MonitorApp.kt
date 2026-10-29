package com.t2m.monitor

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.t2m.monitor.work.RetentionWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

/**
 * 应用入口。
 *
 * 通过 Hilt 初始化全局依赖图；采集内核、前台服务与悬浮窗的依赖均由此注入。
 */
@HiltAndroidApp
class MonitorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleRetentionWork()
    }

    /** 每日执行一次数据保留策略，低电量时延迟。 */
    private fun scheduleRetentionWork() {
        val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RETENTION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val RETENTION_WORK_NAME = "monitor_retention"
    }
}
