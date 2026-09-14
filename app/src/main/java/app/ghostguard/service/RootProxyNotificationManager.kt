package app.ghostguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.ghostguard.MainActivity
import app.ghostguard.R
import java.util.Locale

class RootProxyNotificationManager(
    private val context: Context,
) {
    companion object {
        const val NOTIFICATION_ID = 10
        const val CHANNEL_ID = "ghostguard_root_proxy_channel"
    }

    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Root Proxy Mode",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows when Root Proxy ad blocker is active"
                    setShowBadge(false)
                }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        isRunning: Boolean,
        todayBlockedCount: Int,
        startTimestamp: Long,
    ): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val stopIntent =
            Intent(context, RootProxyService::class.java).apply {
                action = RootProxyService.ACTION_STOP
            }
        val stopPendingIntent =
            PendingIntent.getService(
                context,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val pauseIntent =
            Intent(context, RootProxyService::class.java).apply {
                action = RootProxyService.ACTION_PAUSE_1H
            }
        val pausePendingIntent =
            PendingIntent.getService(
                context,
                2,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

        val text =
            if (isRunning) {
                val uptimeStr = formatUptime(System.currentTimeMillis() - startTimestamp)
                context.getString(R.string.vpn_notification_stats_today, todayBlockedCount, uptimeStr)
            } else {
                context.getString(R.string.root_proxy_notification_text)
            }

        return builder
            .setContentTitle(context.getString(R.string.vpn_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                Notification.Action
                    .Builder(
                        null,
                        context.getString(R.string.vpn_notification_action_pause),
                        pausePendingIntent,
                    ).build(),
            ).addAction(
                Notification.Action
                    .Builder(
                        null,
                        context.getString(R.string.vpn_notification_action_stop),
                        stopPendingIntent,
                    ).build(),
            ).setOngoing(true)
            .build()
    }

    fun showPausedNotification() {
        createChannel()

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val startIntent =
            Intent(context, RootProxyService::class.java).apply {
                action = RootProxyService.ACTION_START
            }
        val startPendingIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    3,
                    startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getService(
                    context,
                    3,
                    startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

        val notification =
            builder
                .setContentTitle(context.getString(R.string.vpn_paused_title))
                .setContentText(context.getString(R.string.vpn_paused_text))
                .setSmallIcon(R.drawable.ic_shield_off)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .addAction(
                    Notification.Action
                        .Builder(
                            null,
                            context.getString(R.string.vpn_stopped_action_enable),
                            startPendingIntent,
                        ).build(),
                ).build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    fun showStartFailedNotification() {
        createChannel()

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val retryIntent =
            Intent(context, RootProxyService::class.java).apply {
                action = RootProxyService.ACTION_START
            }
        val retryPendingIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    4,
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getService(
                    context,
                    4,
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

        val notification =
            builder
                .setContentTitle(context.getString(R.string.root_proxy_start_failed_title))
                .setContentText(context.getString(R.string.root_proxy_start_failed_text))
                .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.root_proxy_start_failed_text)))
                .setSmallIcon(R.drawable.ic_shield_off)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .addAction(
                    Notification.Action
                        .Builder(
                            null,
                            context.getString(R.string.vpn_stopped_action_enable),
                            retryPendingIntent,
                        ).build(),
                ).build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    fun updateNotification(notification: Notification) {
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun formatUptime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
}
