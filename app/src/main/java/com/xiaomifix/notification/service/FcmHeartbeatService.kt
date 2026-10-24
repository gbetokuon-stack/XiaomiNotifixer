package com.xiaomifix.notification.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.xiaomifix.notification.MainActivity
import com.xiaomifix.notification.R
import com.xiaomifix.notification.util.FcmPinger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FcmHeartbeatService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "fcm_keep_alive_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PING_UPDATE = "com.xiaomifix.notification.PING_UPDATE"
        const val EXTRA_STATUS = "extra_status"
        const val PING_INTERVAL_MS = 120_000L // 2 phút giữ kết nối NAT

        @Volatile
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startServiceInForeground()
        acquireWakeLock()
        startHeartbeatLoop()
    }

    private fun startServiceInForeground() {
        val notification = buildForegroundNotification("Đang duy trì kết nối push FCM...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "XiaomiNotificationFixer::FcmWakeLock"
        )
    }

    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (isActive) {
                wakeLock?.acquire(1000)
                val result = FcmPinger.pingFcmServer(applicationContext)
                if (wakeLock?.isHeld == true) {
                    try { wakeLock?.release() } catch (_: Exception) {}
                }

                val statusText = if (result.success) {
                    "FCM Ping OK: ${result.latencyMs}ms"
                } else {
                    "FCM Reconnect: ${result.message}"
                }

                updateNotification(statusText)

                val updateIntent = Intent(ACTION_PING_UPDATE).apply {
                    putExtra(EXTRA_STATUS, statusText)
                    setPackage(packageName)
                }
                sendBroadcast(updateIntent)

                delay(PING_INTERVAL_MS)
            }
        }
    }

    private fun buildForegroundNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Xiaomi Notification Keep-Alive")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildForegroundNotification(content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_fcm_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_fcm_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

