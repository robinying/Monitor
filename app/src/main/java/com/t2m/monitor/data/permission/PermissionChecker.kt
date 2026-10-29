package com.t2m.monitor.data.permission

import android.Manifest
import android.app.AppOpsManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 权限状态快照。
 *
 * @param overlayGranted 是否允许显示在其他应用上层
 * @param notificationGranted 是否允许发通知（Android 13 以下恒为 true）
 * @param ignoringBatteryOptimizations 是否已加入电池优化白名单
 * @param usageStatsGranted 是否已授予使用情况访问（前台应用/流量）
 */
data class PermissionStatus(
    val overlayGranted: Boolean,
    val notificationGranted: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val usageStatsGranted: Boolean,
)

/**
 * 权限检测与系统设置跳转。
 *
 * 全部为只读检查或显式跳转，不静默申请危险权限。
 */
@Singleton
class PermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun readStatus(): PermissionStatus = PermissionStatus(
        overlayGranted = Settings.canDrawOverlays(context),
        notificationGranted = hasNotificationPermission(),
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
        usageStatsGranted = hasUsageStatsPermission(),
    )

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 使用情况访问属于特殊授权，通过 AppOps 判断。 */
    @Suppress("DEPRECATION")
    fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 跳转悬浮窗授权页。 */
    fun openOverlaySettings() {
        startSettings(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri(),
            ),
        )
    }

    /** 跳转使用情况访问设置页（系统列表页，无包级入口）。 */
    fun openUsageAccessSettings() {
        startSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    /** 跳转本应用通知设置页。 */
    fun openNotificationSettings() {
        startSettings(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    /**
     * 申请加入电池优化白名单；设备不支持时回退到电池优化列表页。
     *
     * 本应用为企业内部/侧载分发，且由用户主动点击触发，不涉及商店政策限制。
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations() {
        val requestIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri(),
        )
        if (!startSettings(requestIntent)) {
            startSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** 跳转系统设置，需要 NEW_TASK（从非 Activity 上下文调用也安全）。 */
    private fun startSettings(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (exception: Exception) {
        // 个别 ROM 缺少对应设置页，界面会提示用户手动前往系统设置。
        android.util.Log.w("T2mMonitor", "open settings failed: ${intent.action}", exception)
        false
    }
}
