package com.t2m.monitor.overlay

import com.t2m.monitor.data.model.OverlayMetric
import com.t2m.monitor.data.model.OverlayMode
import com.t2m.monitor.data.prefs.OverlaySettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮窗分组显隐：指标开关与行显隐不依赖布局层级。
 */
class OverlayVisibilityTest {

    @Test
    fun `all visible shows all rows`() {
        val visibility = overlayVisibility(settings(OverlayMetric.entries.toSet()), hasFps = true)

        assertTrue(visibility.showBatteryTemperatureRow)
        assertTrue(visibility.showCpuMemoryRow)
        assertTrue(visibility.showStorageVoltageRow)
        assertTrue(visibility.showCurrentFrequencyRow)
        assertTrue(visibility.showFpsRow)
    }

    @Test
    fun `battery only keeps battery related groups and rows visible`() {
        val visibility = overlayVisibility(settings(setOf(OverlayMetric.BATTERY)), hasFps = true)

        assertTrue(visibility.showBatteryTemperatureRow)
        assertFalse(visibility.showCpuMemoryRow)
        assertTrue(visibility.showStorageVoltageRow)
        assertTrue(visibility.showCurrentFrequencyRow)
        assertFalse(visibility.showFpsRow)
    }

    @Test
    fun `cpu and temperature can keep rows without battery`() {
        val visibility = overlayVisibility(
            settings(setOf(OverlayMetric.CPU, OverlayMetric.TEMPERATURE)),
            hasFps = true,
        )

        assertTrue(visibility.showBatteryTemperatureRow)
        assertTrue(visibility.showCpuMemoryRow)
        assertFalse(visibility.showStorageVoltageRow)
        assertTrue(visibility.showCurrentFrequencyRow)
    }

    @Test
    fun `fps row requires both the toggle and a sample`() {
        assertFalse(overlayVisibility(settings(OverlayMetric.entries.toSet()), hasFps = false).showFps)
        assertTrue(overlayVisibility(settings(setOf(OverlayMetric.FPS)), hasFps = true).showFps)
    }

    private fun settings(metrics: Set<OverlayMetric>): OverlaySettings = OverlaySettings(
        mode = OverlayMode.PANEL,
        x = OverlaySettings.POSITION_UNSET,
        y = OverlaySettings.POSITION_UNSET,
        visibleMetrics = metrics,
    )
}
