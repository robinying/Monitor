package com.t2m.monitor.ui.dashboard

import com.t2m.monitor.data.model.MonitorSnapshot
import com.t2m.monitor.data.permission.PermissionStatus

/**
 * 仪表盘界面状态。
 *
 * @param cpuHistoryPercent 最近若干次 CPU 使用率，用于 sparkline
 * @param memoryHistoryPercent 最近若干次内存使用率，用于 sparkline
 * @param serviceRunning 前台监控服务是否运行
 * @param overlayVisible 悬浮窗是否显示
 * @param samplingPaused 用户是否暂停采样
 */
data class DashboardUiState(
    val snapshot: MonitorSnapshot = MonitorSnapshot.EMPTY,
    val cpuHistoryPercent: List<Float> = emptyList(),
    val memoryHistoryPercent: List<Float> = emptyList(),
    val serviceRunning: Boolean = false,
    val overlayVisible: Boolean = false,
    val samplingPaused: Boolean = false,
    val permissions: PermissionStatus = PermissionStatus(
        overlayGranted = false,
        notificationGranted = false,
        ignoringBatteryOptimizations = false,
        usageStatsGranted = false,
    ),
) {
    /** 采样是否在产生新数据。 */
    val samplingActive: Boolean
        get() = serviceRunning && !samplingPaused
}
