package com.robin.monitor.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.robin.monitor.R
import com.robin.monitor.core.MonitorLogger
import com.robin.monitor.core.MonitorRuntime
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 快捷设置磁贴：一键显示/隐藏悬浮窗；服务未运行时先启动监控服务。
 */
@AndroidEntryPoint
class MonitorTileService : TileService() {

    @Inject
    lateinit var runtime: MonitorRuntime

    @Inject
    lateinit var logger: MonitorLogger

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val started = runCatching {
            if (runtime.serviceRunning.value) {
                // 以服务内的窗口状态为准；避免运行时状态短暂不同步导致磁贴点了没反应。
                startAction(MonitorService.ACTION_TOGGLE_OVERLAY)
            } else {
                startForegroundService(
                    Intent(this, MonitorService::class.java)
                        .setAction(MonitorService.ACTION_START),
                )
                startAction(MonitorService.ACTION_SHOW_OVERLAY)
            }
        }
        started.onFailure { error -> logger.warn("quick settings toggle failed", error) }
        updateTile()
    }

    private fun startAction(action: String) {
        startService(Intent(this, MonitorService::class.java).setAction(action))
    }

    private fun updateTile() {
        qsTile?.apply {
            label = getString(R.string.tile_label)
            state = if (runtime.overlayVisible.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
