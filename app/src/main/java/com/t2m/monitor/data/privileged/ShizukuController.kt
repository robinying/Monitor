package com.t2m.monitor.data.privileged

import android.content.pm.PackageManager
import com.t2m.monitor.core.MonitorLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku 状态与授权封装。
 *
 * Shizuku 是可选能力：未安装、服务未启动或未授权时，所有特权采集器返回 null，
 * 界面相应卡片自动隐藏，不影响基线功能。
 */
@Singleton
class ShizukuController @Inject constructor(
    private val logger: MonitorLogger,
) : ShizukuAccess {

    private val _available = MutableStateFlow(false)

    /** Shizuku 服务是否可连接。 */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _granted = MutableStateFlow(false)

    /** 是否已授予本应用 Shizuku 权限。 */
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == REQUEST_CODE) {
            refresh()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }

    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }

    init {
        val registered = runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        }
        if (registered.isFailure) {
            logger.warn("register shizuku listeners failed", registered.exceptionOrNull())
        }
        refresh()
    }

    /** 重新读取服务与授权状态（从设置返回、Binder 变化时调用）。 */
    fun refresh() {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        _available.value = binderAlive
        _granted.value = binderAlive && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    override fun isGranted(): Boolean = _granted.value

    /** 发起授权请求；结果通过 [granted] 流回传。 */
    fun requestPermission() {
        if (!_available.value) {
            return
        }
        val requested = runCatching { Shizuku.requestPermission(REQUEST_CODE) }
        if (requested.isFailure) {
            logger.warn("request shizuku permission failed", requested.exceptionOrNull())
        }
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
