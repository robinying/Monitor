package com.t2m.monitor.data.privileged

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import com.t2m.monitor.data.model.AppStorageUsage
import com.t2m.monitor.data.permission.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** 应用存储占用数据来源，便于测试替换。 */
interface AppStorageSource {

    fun isAvailable(): Boolean

    fun readTopApps(limit: Int): List<AppStorageUsage>?
}

/**
 * 生产实现：UsageStats 取常用应用 → StorageStatsManager 逐个查询占用。
 *
 * 仅需 `PACKAGE_USAGE_STATS`（M4 已接入），不依赖 Shizuku。
 */
class AndroidAppStorageSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
) : AppStorageSource {

    override fun isAvailable(): Boolean = permissionChecker.hasUsageStatsPermission()

    override fun readTopApps(limit: Int): List<AppStorageUsage>? {
        if (!isAvailable()) {
            return null
        }
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return null
        val storageStatsManager = context.getSystemService(StorageStatsManager::class.java)
            ?: return null
        val now = System.currentTimeMillis()
        val candidates = runCatching {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - CANDIDATE_WINDOW_MILLIS,
                now,
            ).orEmpty()
                .sortedByDescending { stats -> stats.totalTimeInForeground }
                .distinctBy { stats -> stats.packageName }
                .take(MAX_CANDIDATES)
        }.getOrNull() ?: return null

        val userHandle = Process.myUserHandle()
        val apps = candidates.mapNotNull { usage ->
            val stats = runCatching {
                storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    usage.packageName,
                    userHandle,
                )
            }.getOrNull() ?: return@mapNotNull null
            AppStorageUsage(
                packageName = usage.packageName,
                label = resolveAppLabel(usage.packageName),
                totalBytes = stats.appBytes + stats.dataBytes + stats.cacheBytes,
            )
        }
        return apps.sortedByDescending { item -> item.totalBytes }.take(limit)
    }

    private fun resolveAppLabel(packageName: String): String? = runCatching {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    }.getOrNull()

    private companion object {
        const val CANDIDATE_WINDOW_MILLIS = 24L * 60 * 60 * 1000
        const val MAX_CANDIDATES = 15
    }
}
