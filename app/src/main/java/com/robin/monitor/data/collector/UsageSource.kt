package com.robin.monitor.data.collector

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.ConnectivityManager
import com.robin.monitor.core.MonitorLogger
import com.robin.monitor.data.model.UsageSnapshot
import com.robin.monitor.data.permission.PermissionChecker
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
    private val logger: MonitorLogger,
) : UsageSource {

    /** 查询失败按来源只记录一次，避免 5 秒采样周期刷屏。 */
    private var failureLogged = false

    override fun isAvailable(): Boolean = permissionChecker.hasUsageStatsPermission()

    override fun read(): UsageSnapshot? {
        if (!isAvailable()) {
            return null
        }
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return null
        val now = System.currentTimeMillis()
        val foregroundPackage = try {
            readForegroundPackage(usageStatsManager, now)
        } catch (error: Exception) {
            warnOnce("read foreground app failed", error)
            null
        }
        val networkStats = readTodayNetworkBytes(now)

        // 全部字段为空时返回 null，让诊断层记为失败而不是“成功但无数据”。
        return usageSnapshotOrNull(
            foregroundPackage = foregroundPackage,
            foregroundAppLabel = foregroundPackage?.let(::resolveAppLabel),
            networkStats = networkStats,
        )
    }

    private fun warnOnce(message: String, error: Exception) {
        if (failureLogged) {
            return
        }
        failureLogged = true
        logger.warn(message, error)
    }

    private fun readForegroundPackage(usageStatsManager: UsageStatsManager, nowMillis: Long): String? {
        queryRecentForeground(usageStatsManager, nowMillis, FOREGROUND_LOOKBACK_MILLIS)?.let { return it }
        // 长时间停留在同一页面时近期没有切换事件，扩大窗口后再兜底到最近使用的应用。
        queryRecentForeground(usageStatsManager, nowMillis, FOREGROUND_FALLBACK_MILLIS)?.let { return it }
        return try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                nowMillis - FOREGROUND_FALLBACK_MILLIS,
                nowMillis,
            ).orEmpty().maxByOrNull { stats -> stats.lastTimeUsed }?.packageName
        } catch (error: Exception) {
            warnOnce("query usage stats failed", error)
            null
        }
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
    ): NetworkStats.Bucket? = try {
        @Suppress("DEPRECATION")
        networkStatsManager.querySummaryForDevice(networkType, null, startMillis, endMillis)
    } catch (error: Exception) {
        warnOnce("read today's network usage failed", error)
        null
    }

    private companion object {
        const val FOREGROUND_LOOKBACK_MILLIS = 60_000L
        const val FOREGROUND_FALLBACK_MILLIS = 10L * 60 * 1000
    }
}

/**
 * 组装使用情况快照。
 *
 * @return 全部字段为空时返回 null；部分来源不可用时保留有效字段并标记 [UsageSnapshot.partial]
 */
internal fun usageSnapshotOrNull(
    foregroundPackage: String?,
    foregroundAppLabel: String?,
    networkStats: List<Long?>?,
): UsageSnapshot? {
    val snapshot = UsageSnapshot(
        foregroundPackage = foregroundPackage,
        foregroundAppLabel = foregroundAppLabel,
        todayMobileRxBytes = networkStats?.getOrNull(0),
        todayMobileTxBytes = networkStats?.getOrNull(1),
        todayWifiRxBytes = networkStats?.getOrNull(2),
        todayWifiTxBytes = networkStats?.getOrNull(3),
    )
    if (!snapshot.hasData) {
        return null
    }
    val partial = foregroundPackage == null ||
        networkStats == null ||
        networkStats.any { bytes -> bytes == null }
    return snapshot.copy(partial = partial)
}
