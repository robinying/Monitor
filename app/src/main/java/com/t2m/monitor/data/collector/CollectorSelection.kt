package com.t2m.monitor.data.collector

import com.t2m.monitor.core.FeatureConfig

/**
 * 按功能开关筛选采集器。
 *
 * 关闭使用情况访问时不注册 USAGE/APP_STORAGE 采集器；关闭特权层时不注册 FPS 采集器。
 */
object CollectorSelection {

    fun select(
        collectors: List<Collector<*>>,
        features: FeatureConfig,
    ): List<Collector<*>> = collectors.filter { collector ->
        when (collector.id) {
            CollectorId.USAGE, CollectorId.APP_STORAGE -> features.usageStats
            CollectorId.FPS -> features.privileged
            else -> true
        }
    }
}
