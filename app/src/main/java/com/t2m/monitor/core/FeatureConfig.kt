package com.t2m.monitor.core

/**
 * 运行时功能配置。
 *
 * 生产实现默认读取 [FeatureFlags]；测试可构造关闭某项能力的实例，验证降级行为。
 */
data class FeatureConfig(
    val overlay: Boolean = FeatureFlags.OVERLAY,
    val alerts: Boolean = FeatureFlags.ALERTS,
    val usageStats: Boolean = FeatureFlags.USAGE_STATS,
    val privileged: Boolean = FeatureFlags.PRIVILEGED,
)
