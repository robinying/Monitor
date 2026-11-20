package com.t2m.monitor.overlay

import com.t2m.monitor.data.model.OverlayMetric
import com.t2m.monitor.data.prefs.OverlaySettings

/**
 * 悬浮窗一次渲染时的分组显隐。
 *
 * 单独抽出纯逻辑，避免依赖 `View.parent` 推断布局层级；布局调整时字段名变化会直接编译失败。
 */
internal data class OverlayVisibility(
    val showBattery: Boolean,
    val showTemperature: Boolean,
    val showCpu: Boolean,
    val showMemory: Boolean,
    val showStorage: Boolean,
    val showFps: Boolean,
) {
    val showBatteryTemperatureRow: Boolean
        get() = showBattery || showTemperature

    val showCpuMemoryRow: Boolean
        get() = showCpu || showMemory

    val showStorageVoltageRow: Boolean
        get() = showStorage || showBattery

    val showCurrentFrequencyRow: Boolean
        get() = showBattery || showCpu

    val showFpsRow: Boolean
        get() = showFps
}

internal fun overlayVisibility(settings: OverlaySettings, hasFps: Boolean): OverlayVisibility =
    OverlayVisibility(
        showBattery = settings.isVisible(OverlayMetric.BATTERY),
        showTemperature = settings.isVisible(OverlayMetric.TEMPERATURE),
        showCpu = settings.isVisible(OverlayMetric.CPU),
        showMemory = settings.isVisible(OverlayMetric.MEMORY),
        showStorage = settings.isVisible(OverlayMetric.STORAGE),
        showFps = settings.isVisible(OverlayMetric.FPS) && hasFps,
    )
