package com.robin.monitor.data.privileged

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import com.robin.monitor.core.MonitorLogger
import com.robin.monitor.data.model.AppStorageUsage
import com.robin.monitor.data.permission.PermissionChecker
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
    private val logger: MonitorLogger,
) : AppStorageSource {

    @Volatile
    private var failureLogged = false

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
        val candidates = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - CANDIDATE_WINDOW_MILLIS,
                now,
            ).orEmpty()
                .sortedByDescending { stats -> stats.totalTimeInForeground }
                .distinctBy { stats -> stats.packageName }
                .take(MAX_CANDIDATES)
        } catch (error: Exception) {
            logger.warn("query usage stats for storage failed", error)
            return null
        }

        val userHandle = Process.myUserHandle()
        return buildAppStorageList(
            packageNames = candidates.map { stats -> stats.packageName },
            limit = limit,
            queryBytes = { packageName ->
                val stats = storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    packageName,
                    userHandle,
                )
                // dataBytes 已包含 cacheBytes（见 StorageStats#getDataBytes 契约），不可重复相加。
                stats.appBytes to stats.dataBytes
            },
            label = ::resolveAppLabel,
            onFailure = { packageName, error ->
                if (!failureLogged) {
                    logger.warn("query app storage failed: $packageName", error)
                    failureLogged = true
                }
            },
        )
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

/**
 * 聚合应用存储查询结果。
 *
 * 单个包失败不丢弃整个列表，但全部失败时返回 null，让采集器记为失败而不是“成功但无数据”。
 */
internal fun buildAppStorageList(
    packageNames: List<String>,
    limit: Int,
    queryBytes: (String) -> Pair<Long, Long>?,
    label: (String) -> String?,
    onFailure: (String, Exception) -> Unit,
): List<AppStorageUsage>? {
    val apps = packageNames.mapNotNull { packageName ->
        try {
            val bytes = queryBytes(packageName) ?: return@mapNotNull null
            AppStorageUsage(
                packageName = packageName,
                label = label(packageName),
                totalBytes = bytes.first + bytes.second,
            )
        } catch (error: Exception) {
            onFailure(packageName, error)
            null
        }
    }
    if (apps.isEmpty() && packageNames.isNotEmpty()) {
        return null
    }
    return apps.sortedByDescending { item -> item.totalBytes }.take(limit)
}
