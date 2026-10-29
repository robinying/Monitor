package com.t2m.monitor.core

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 监控运行态。
 *
 * 由前台服务与悬浮窗控制器写入，界面只读，避免界面与服务直接耦合。
 */
@Singleton
class MonitorRuntime @Inject constructor() {

    private val _serviceRunning = MutableStateFlow(false)

    /** 前台监控服务是否运行中。 */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _overlayVisible = MutableStateFlow(false)

    /** 悬浮窗当前是否已显示。 */
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    private val _samplingPaused = MutableStateFlow(false)

    /** 用户是否暂停了采样（服务仍在，悬浮窗保留最后数据）。 */
    val samplingPaused: StateFlow<Boolean> = _samplingPaused.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun setOverlayVisible(visible: Boolean) {
        _overlayVisible.value = visible
    }

    fun setSamplingPaused(paused: Boolean) {
        _samplingPaused.value = paused
    }
}
