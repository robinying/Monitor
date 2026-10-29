package com.t2m.monitor.core

/**
 * 功能开关。
 *
 * 新增能力必须经开关保护；关闭后应用须回退为仅仪表盘的基础形态且不崩溃。
 */
object FeatureFlags {

    /** 悬浮窗（M2 落地后置为 true）。 */
    const val OVERLAY: Boolean = true

    /** 阈值告警（M4 落地）。 */
    const val ALERTS: Boolean = true

    /** 使用情况访问指标：前台应用、今日流量、应用存储占用（需 PACKAGE_USAGE_STATS）。 */
    const val USAGE_STATS: Boolean = true

    /** Shizuku 特权指标：系统 FPS。未安装/未授权时相关卡片自动隐藏。 */
    const val PRIVILEGED: Boolean = true
}
