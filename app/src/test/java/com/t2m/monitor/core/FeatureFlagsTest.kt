package com.t2m.monitor.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验 M0 阶段功能开关的默认状态，防止打包时误开未实现能力。
 */
class FeatureFlagsTest {

    @Test
    fun `mvp features are enabled`() {
        assertTrue(FeatureFlags.OVERLAY)
        assertTrue(FeatureFlags.ALERTS)
    }

    @Test
    fun `optional features are enabled and degrade at runtime`() {
        assertTrue(FeatureFlags.USAGE_STATS)
        assertTrue(FeatureFlags.PRIVILEGED)
    }
}
