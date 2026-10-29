package com.t2m.monitor.ui.permission

import androidx.lifecycle.ViewModel
import com.t2m.monitor.data.permission.PermissionChecker
import com.t2m.monitor.data.privileged.ShizukuController
import com.t2m.monitor.data.permission.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 权限引导页 ViewModel。
 *
 * 只读检测 + 跳系统设置，不主动申请危险权限（通知权限由界面发起运行时申请）。
 */
@HiltViewModel
class PermissionGuideViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
    private val shizukuController: ShizukuController,
) : ViewModel() {

    private val _status = MutableStateFlow(permissionChecker.readStatus())

    val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    /** Shizuku 服务是否可连接。 */
    val shizukuAvailable: StateFlow<Boolean> = shizukuController.available

    /** 是否已授予 Shizuku 权限。 */
    val shizukuGranted: StateFlow<Boolean> = shizukuController.granted

    /** 从系统设置返回或申请结果回调后刷新。 */
    fun refresh() {
        _status.value = permissionChecker.readStatus()
        shizukuController.refresh()
    }

    fun openOverlaySettings() {
        permissionChecker.openOverlaySettings()
    }

    fun openNotificationSettings() {
        permissionChecker.openNotificationSettings()
    }

    fun requestBatteryOptimization() {
        permissionChecker.requestIgnoreBatteryOptimizations()
    }

    fun openUsageAccessSettings() {
        permissionChecker.openUsageAccessSettings()
    }

    fun requestShizukuPermission() {
        shizukuController.requestPermission()
    }
}
