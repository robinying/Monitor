package com.t2m.monitor.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.t2m.monitor.data.repository.MetricRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 每日数据保留任务。
 *
 * 通过 Hilt EntryPoint 获取仓库，无需注入 Worker 工厂。
 */
class RetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                RetentionEntryPoint::class.java,
            )
            entryPoint.metricRepository().prune(System.currentTimeMillis())
            Result.success()
        } catch (exception: Exception) {
            // 保留策略失败不影响用户数据可读性，按重试次数退避后放弃。
            Log.w(TAG, "retention work failed", exception)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RetentionEntryPoint {
        fun metricRepository(): MetricRepository
    }

    private companion object {
        const val TAG = "T2mMonitor"
        const val MAX_RETRIES = 3
    }
}
