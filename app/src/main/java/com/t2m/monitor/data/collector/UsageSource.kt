package com.t2m.monitor.data.collector

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.ConnectivityManager
import com.t2m.monitor.data.model.UsageSnapshot
import com.t2m.monitor.data.permission.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** 使用情况数据来源，便于测试替换。 */
interface UsageSource {

    fun isAvailable(): Boolean

    fun read(): UsageSnapshot?
}

/**
 * 生产实现：基于 `PACKAGE_USAGE_STATS` 读取前台应用与今日流量。
 *
 * 未授权、系统不支持或查询失败时返回 null，界面显示 "--"。
 */
class AndroidUsageSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
) : UsageSource {

    override fun isAvailable(): Boolean = permissionChecker.hasUsageStatsPermission()

    override fun read(): UsageSnapshot? {
        if (!isAvailable()) {
            return null
        }
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return null
        val now = System.currentTimeMillis()
        val foregroundPackage = readForegroundPackage(usageStatsManager, now)
        val networkStats = readTodayNetworkBytes(now)

        return UsageSnapshot(
            foregroundPackage = foregroundPackage,
            foregroundAppLabel = foregroundPackage?.let(::resolveAppLabel),
            todayMobileRxBytes = networkStats?.getOrNull(0),
            todayMobileTxBytes = networkStats?.getOrNull(1),
            todayWifiRxBytes = networkStats?.getOrNull(2),
            todayWifiTxBytes = networkStats?.getOrNull(3),
        )
    }

    private fun readForegroundPackage(usageStatsManager: UsageStatsManager, nowMillis: Long): String? {
        queryRecentForeground(usageStatsManager, nowMillis, FOREGROUND_LOOKBACK_MILLIS)?.let { return it }
        // 长时间停留在同一页面时近期没有切换事件，扩大窗口后再兜底到最近使用的应用。
        queryRecentForeground(usageStatsManager, nowMillis, FOREGROUND_FALLBACK_MILLIS)?.let { return it }
        return runCatching {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                nowMillis - FOREGROUND_FALLBACK_MILLIS,
                nowMillis,
            ).orEmpty().maxByOrNull { stats -> stats.lastTimeUsed }?.packageName
        }.getOrNull()
    }

    private fun queryRecentForeground(
        usageStatsManager: UsageStatsManager,
        nowMillis: Long,
        lookbackMillis: Long,
    ): String? {
        val events = usageStatsManager.queryEvents(nowMillis - lookbackMillis, nowMillis)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == foregroundEventType) {
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }

    /** API 29 起用 ACTIVITY_RESUMED，旧版本用等价的 MOVE_TO_FOREGROUND。 */
    @Suppress("DEPRECATION")
    private val foregroundEventType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

    private fun resolveAppLabel(packageName: String): String? = runCatching {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    }.getOrNull()

    /** @return 移动下行、移动上行、Wi-Fi 下行、Wi-Fi 上行；查询失败时返回 null。 */
    @Suppress("DEPRECATION")
    private fun readTodayNetworkBytes(nowMillis: Long): List<Long?>? {
        val networkStatsManager = context.getSystemService(NetworkStatsManager::class.java)
            ?: return null
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val mobile = queryBucket(networkStatsManager, ConnectivityManager.TYPE_MOBILE, startOfDay, nowMillis)
        val wifi = queryBucket(networkStatsManager, ConnectivityManager.TYPE_WIFI, startOfDay, nowMillis)
        if (mobile == null && wifi == null) {
            return null
        }
        return listOf(
            mobile?.rxBytes,
            mobile?.txBytes,
            wifi?.rxBytes,
            wifi?.txBytes,
        )
    }

    private fun queryBucket(
        networkStatsManager: NetworkStatsManager,
        networkType: Int,
        startMillis: Long,
        endMillis: Long,
    ): NetworkStats.Bucket? = runCatching {
        @Suppress("DEPRECATION")
        networkStatsManager.querySummaryForDevice(networkType, null, startMillis, endMillis)
    }.getOrNull()

    private companion object {
        const val FOREGROUND_LOOKBACK_MILLIS = 60_000L
        const val FOREGROUND_FALLBACK_MILLIS = 10L * 60 * 1000
    }
}
