package com.t2m.monitor.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t2m.monitor.core.MonitorRuntime
import com.t2m.monitor.data.permission.PermissionChecker
import com.t2m.monitor.data.permission.PermissionStatus
import com.t2m.monitor.domain.MonitorEngine
import com.t2m.monitor.service.MonitorController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 仪表盘 ViewModel。
 *
 * M2 起采样生命周期由前台服务持有，本类只订阅运行态并转发用户操作。
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val engine: MonitorEngine,
    private val runtime: MonitorRuntime,
    private val controller: MonitorController,
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    private val cpuHistory = ArrayDeque<Float>()
    private val memoryHistory = ArrayDeque<Float>()

    private val permissions = MutableStateFlow(permissionChecker.readStatus())

    private var lastTimestampMillis = Long.MIN_VALUE

    private val _uiState = MutableStateFlow(DashboardUiState())

    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                engine.snapshot,
                runtime.serviceRunning,
                runtime.overlayVisible,
                runtime.samplingPaused,
                permissions,
            ) { snapshot, serviceRunning, overlayVisible, samplingPaused, permissionStatus ->
                DashboardUiState(
                    snapshot = snapshot,
                    cpuHistoryPercent = cpuHistory.toList(),
                    memoryHistoryPercent = memoryHistory.toList(),
                    serviceRunning = serviceRunning,
                    overlayVisible = overlayVisible,
                    samplingPaused = samplingPaused,
                    permissions = permissionStatus,
                )
            }.collect { state ->
                if (state.snapshot.timestampMillis != lastTimestampMillis) {
                    lastTimestampMillis = state.snapshot.timestampMillis
                    appendHistory(cpuHistory, state.snapshot.cpu?.totalUsagePercent)
                    appendHistory(memoryHistory, state.snapshot.memory?.usedPercent)
                }
                _uiState.value = state.copy(
                    cpuHistoryPercent = cpuHistory.toList(),
                    memoryHistoryPercent = memoryHistory.toList(),
                )
            }
        }
    }

    /** 回到前台或从系统设置返回时刷新权限状态。 */
    fun refreshPermissions() {
        permissions.value = permissionChecker.readStatus()
    }

    fun startMonitoring() {
        controller.start()
    }

    fun stopMonitoring() {
        controller.stop()
    }

    fun toggleOverlay() {
        if (_uiState.value.overlayVisible) {
            controller.hideOverlay()
        } else {
            controller.showOverlay()
        }
    }

    private fun appendHistory(history: ArrayDeque<Float>, value: Float?) {
        if (value == null) {
            return
        }
        history.addLast(value)
        while (history.size > HISTORY_CAPACITY) {
            history.removeFirst()
        }
    }

    private companion object {
        const val HISTORY_CAPACITY = 30
    }
}
