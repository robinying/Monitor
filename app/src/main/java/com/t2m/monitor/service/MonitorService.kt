package com.t2m.monitor.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.t2m.monitor.R
import com.t2m.monitor.core.MonitorRuntime
import com.t2m.monitor.data.prefs.MonitorPreferences
import com.t2m.monitor.domain.MetricRecorder
import com.t2m.monitor.domain.alert.AlertMonitor
import com.t2m.monitor.domain.alert.AlertNotifier
import com.t2m.monitor.domain.MonitorEngine
import com.t2m.monitor.overlay.OverlayController
import com.t2m.monitor.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 常驻监控服务。
 *
 * 持有采样与悬浮窗的生命周期，提供常驻通知（悬浮窗开关 / 暂停 / 退出）。
 * 仅由用户显式操作启动，不使用 START_STICKY，避免无权限时被系统反复拉起。
 */
@AndroidEntryPoint
class MonitorService : Service() {

    @Inject
    lateinit var engine: MonitorEngine

    @Inject
    lateinit var overlayController: OverlayController

    @Inject
    lateinit var runtime: MonitorRuntime

    @Inject
    lateinit var metricRecorder: MetricRecorder

    @Inject
    lateinit var alertMonitor: AlertMonitor

    @Inject
    lateinit var alertNotifier: AlertNotifier

    @Inject
    lateinit var preferences: MonitorPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var samplingPaused = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        alertNotifier.ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        runtime.setServiceRunning(true)
        runtime.setSamplingPaused(false)
        engine.start()
        metricRecorder.start()
        alertMonitor.start()

        serviceScope.launch {
            if (preferences.overlayEnabled.first()) {
                overlayController.show()
            }
        }
        serviceScope.launch {
            runtime.overlayVisible.collect { updateNotification() }
        }
        serviceScope.launch {
            runtime.samplingPaused.collect { updateNotification() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SHOW_OVERLAY -> overlayController.show()
            ACTION_HIDE_OVERLAY -> overlayController.hide()
            ACTION_TOGGLE_OVERLAY -> overlayController.toggle()
            ACTION_TOGGLE_SAMPLING -> toggleSampling()
            else -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController.onConfigurationChanged()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        alertMonitor.stop()
        metricRecorder.stop()
        engine.stop()
        overlayController.release()
        runtime.setSamplingPaused(false)
        runtime.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun toggleSampling() {
        samplingPaused = !samplingPaused
        if (samplingPaused) {
            metricRecorder.flushNow()
            engine.stop()
        } else {
            engine.start()
        }
        runtime.setSamplingPaused(samplingPaused)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // 无通知权限时前台服务仍可运行，通知由系统在任务管理器展示。
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val overlayVisible = runtime.overlayVisible.value
        val paused = runtime.samplingPaused.value
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                getString(
                    if (paused) R.string.notification_text_paused else R.string.notification_text_running,
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppPendingIntent())
            .addAction(
                0,
                getString(
                    if (overlayVisible) R.string.action_hide_overlay else R.string.action_show_overlay,
                ),
                servicePendingIntent(ACTION_TOGGLE_OVERLAY),
            )
            .addAction(
                0,
                getString(
                    if (paused) R.string.action_resume_sampling else R.string.action_pause_sampling,
                ),
                servicePendingIntent(ACTION_TOGGLE_SAMPLING),
            )
            .addAction(
                0,
                getString(R.string.action_stop_service),
                servicePendingIntent(ACTION_STOP),
            )
            .build()
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, MonitorService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_START = "com.t2m.monitor.action.START"
        const val ACTION_STOP = "com.t2m.monitor.action.STOP"
        const val ACTION_SHOW_OVERLAY = "com.t2m.monitor.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.t2m.monitor.action.HIDE_OVERLAY"
        const val ACTION_TOGGLE_OVERLAY = "com.t2m.monitor.action.TOGGLE_OVERLAY"
        const val ACTION_TOGGLE_SAMPLING = "com.t2m.monitor.action.TOGGLE_SAMPLING"

        private const val CHANNEL_ID = "monitor_service"
        private const val NOTIFICATION_ID = 1001
    }
}
