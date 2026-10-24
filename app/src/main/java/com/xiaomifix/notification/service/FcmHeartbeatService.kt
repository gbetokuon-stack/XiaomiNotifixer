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
    private var powerManager: PowerManager? = null

    companion object {
        const val CHANNEL_ID = "fcm_keep_alive_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PING_UPDATE = "com.xiaomifix.notification.PING_UPDATE"
        const val EXTRA_STATUS = "extra_status"

        // Tối ưu tiết kiệm pin:
        // - Khi tắt màn hình: 6 phút (360s) giữ tunnel NAT ổn định không bị nhà mạng ngắt
        // - Khi bật màn hình: 15 phút (900s) vì thiết bị đang hoạt động, không cần ping liên tục
        const val SCREEN_OFF_INTERVAL_MS = 360_000L
        const val SCREEN_ON_INTERVAL_MS = 900_000L

        @Volatile
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        createNotificationChannel()
        startServiceInForeground()
        startHeartbeatLoop()
    }

    private fun startServiceInForeground() {
        val notification = buildForegroundNotification("Bảo vệ kết nối push FCM (Chế độ siêu tiết kiệm pin)")
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

    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (isActive) {
                val isScreenOn = powerManager?.isInteractive ?: false
                val waitTime = if (isScreenOn) SCREEN_ON_INTERVAL_MS else SCREEN_OFF_INTERVAL_MS

                // Chỉ ping khi cần thiết
                val result = FcmPinger.pingFcmServer(applicationContext)

                val statusText = if (result.success) {
                    "FCM Ping OK: ${result.latencyMs}ms (Chu kỳ ${waitTime / 60000}p)"
                } else {
                    "FCM Reconnect: ${result.message}"
                }

                // Gửi broadcast nội bộ cho MainActivity nếu app đang mở
                val updateIntent = Intent(ACTION_PING_UPDATE).apply {
                    putExtra(EXTRA_STATUS, statusText)
                    setPackage(packageName)
                }
                sendBroadcast(updateIntent)

                delay(waitTime)
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
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_fcm_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.channel_fcm_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}


