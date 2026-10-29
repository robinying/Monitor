package com.t2m.monitor.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 界面侧的服务控制入口。
 *
 * 统一用 startForegroundService 保证服务进入前台，避免后台启动限制导致崩溃。
 */
@Singleton
class MonitorController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun start() {
        ContextCompat.startForegroundService(context, serviceIntent(MonitorService.ACTION_START))
    }

    fun stop() {
        context.stopService(Intent(context, MonitorService::class.java))
    }

    fun showOverlay() {
        ContextCompat.startForegroundService(context, serviceIntent(MonitorService.ACTION_SHOW_OVERLAY))
    }

    fun hideOverlay() {
        ContextCompat.startForegroundService(context, serviceIntent(MonitorService.ACTION_HIDE_OVERLAY))
    }

    fun toggleSampling() {
        ContextCompat.startForegroundService(
            context,
            serviceIntent(MonitorService.ACTION_TOGGLE_SAMPLING),
        )
    }

    private fun serviceIntent(action: String): Intent =
        Intent(context, MonitorService::class.java).setAction(action)
}
