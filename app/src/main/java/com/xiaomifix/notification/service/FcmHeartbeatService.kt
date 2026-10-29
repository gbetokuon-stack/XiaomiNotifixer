package com.xiaomifix.notification.service

import android.annotation.SuppressLint
import android.app.ActivityManager
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.xiaomifix.notification.MainActivity
import com.xiaomifix.notification.R
import com.xiaomifix.notification.util.FcmPinger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FcmHeartbeatService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var powerManager: PowerManager? = null

    companion object {
        const val PREFS_NAME = "xiaomi_notifixer"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_LAST_PING = "last_ping_text"
        const val KEY_LAST_PING_OK = "last_ping_ok"

        const val CHANNEL_ID = "fcm_keep_alive_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PING_UPDATE = "com.xiaomifix.notification.PING_UPDATE"
        const val ACTION_STATE_CHANGED = "com.xiaomifix.notification.SERVICE_STATE"
        const val ACTION_STOP = "com.xiaomifix.notification.action.STOP"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_OK = "extra_ok"
        const val EXTRA_RUNNING = "extra_running"

        // Khi tắt màn hình: 6 phút, đủ giữ tunnel NAT mà không đánh thức CPU quá nhiều.
        // Khi bật màn hình: 15 phút, vì thiết bị đang hoạt động chủ động.
        const val SCREEN_OFF_INTERVAL_MS = 360_000L
        const val SCREEN_ON_INTERVAL_MS = 900_000L

        @Volatile
        var isRunning = false
            private set

        @SuppressLint("ApplySharedPref")
        fun setEnabled(context: Context, enabled: Boolean) {
            // Dùng commit() để trạng thái bật/tắt không bị mất nếu tiến trình
            // bị hệ thống kill ngay sau khi người dùng bấm Tắt.
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SERVICE_ENABLED, enabled)
                .commit()
        }

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, false)
        }

        fun isServiceRunning(context: Context): Boolean {
            if (isRunning) return true
            return try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                @Suppress("DEPRECATION")
                manager?.getRunningServices(Int.MAX_VALUE)
                    ?.any { it.service.className == FcmHeartbeatService::class.java.name } == true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Không khởi tạo foreground service nếu người dùng đã tắt.
        if (!isEnabled(this)) {
            stopSelf()
            return
        }
        isRunning = true
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        createNotificationChannel()

        try {
            startServiceInForeground()
        } catch (_: Exception) {
            isRunning = false
            stopSelf()
            return
        }
        sendStateUpdate(true)
        startHeartbeatLoop()
    }

    private fun startServiceInForeground() {
        val notification = buildForegroundNotification(
            getString(R.string.notification_connecting)
        )
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
                val screenOn = powerManager?.isInteractive ?: false
                val interval = if (screenOn) SCREEN_ON_INTERVAL_MS else SCREEN_OFF_INTERVAL_MS

                val result = FcmPinger.pingFcmServer(applicationContext)
                val status = if (result.success) {
                    getString(
                        R.string.notification_ping_ok,
                        result.latencyMs,
                        (interval / 60_000L).toInt()
                    )
                } else {
                    result.message
                }

                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_PING, status)
                    .putBoolean(KEY_LAST_PING_OK, result.success)
                    .apply()

                updateForegroundNotification(status)
                sendPingUpdate(status, result.success)

                delay(interval)
            }
        }
    }

    private fun sendPingUpdate(status: String, ok: Boolean) {
        try {
            sendBroadcast(
                Intent(ACTION_PING_UPDATE).apply {
                    putExtra(EXTRA_STATUS, status)
                    putExtra(EXTRA_OK, ok)
                    setPackage(packageName)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun sendStateUpdate(running: Boolean) {
        try {
            sendBroadcast(
                Intent(ACTION_STATE_CHANGED).apply {
                    putExtra(EXTRA_RUNNING, running)
                    setPackage(packageName)
                }
            )
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateForegroundNotification(status: String) {
        // Thông báo FGS là tùy chọn về mặt hiển thị: nếu người dùng chưa cấp
        // POST_NOTIFICATIONS, bỏ qua thay vì làm dừng dịch vụ.
        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildForegroundNotification(status))
        } catch (_: Exception) {
        }
    }

    private fun buildForegroundNotification(content: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FcmHeartbeatService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_power, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_fcm_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_fcm_desc)
            setSound(null, null)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setEnabled(this, false)
            sendStateUpdate(false)
            stopSelf()
            return START_NOT_STICKY
        }

        // Không để hệ thống tự khởi động lại dịch vụ nếu người dùng đã tắt.
        if (!isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        sendStateUpdate(false)
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
