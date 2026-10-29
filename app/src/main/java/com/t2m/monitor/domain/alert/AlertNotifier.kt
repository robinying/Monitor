package com.t2m.monitor.domain.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.t2m.monitor.R
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.model.AlertEvent
import com.t2m.monitor.data.model.AlertType
import com.t2m.monitor.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 告警通知。
 *
 * 无通知权限时只记录日志，不抛异常；同一类型告警复用同一通知 ID，新告警覆盖旧文案。
 */
@Singleton
class AlertNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: MonitorLogger,
) {

    fun ensureChannel() {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.alert_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun notify(event: AlertEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            logger.warn("notification permission missing, alert dropped: ${event.type}")
            return
        }
        val text = buildText(event)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(context.getString(R.string.alert_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())
            .build()
        NotificationManagerCompat.from(context).notify(
            ALERT_NOTIFICATION_BASE_ID + event.type.ordinal,
            notification,
        )
    }

    private fun buildText(event: AlertEvent): String = when (event.type) {
        AlertType.BATTERY_TEMPERATURE_HIGH -> context.getString(
            R.string.alert_text_battery_temperature,
            event.actualValue,
            event.threshold,
        )

        AlertType.BATTERY_LOW -> context.getString(
            R.string.alert_text_battery_low,
            event.actualValue,
            event.threshold,
        )

        AlertType.MEMORY_LOW -> context.getString(
            R.string.alert_text_memory,
            event.actualValue,
            event.threshold,
        )

        AlertType.STORAGE_LOW -> context.getString(
            R.string.alert_text_storage,
            event.actualValue,
            event.threshold,
        )
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        ALERT_CONTENT_REQUEST_CODE,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL_ID = "monitor_alerts"
        const val ALERT_NOTIFICATION_BASE_ID = 2000
        const val ALERT_CONTENT_REQUEST_CODE = 2000
    }
}
