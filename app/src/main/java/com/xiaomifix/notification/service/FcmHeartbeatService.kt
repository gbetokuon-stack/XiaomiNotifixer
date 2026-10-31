package com.xiaomifix.notification.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.xiaomifix.notification.MainActivity
import com.xiaomifix.notification.R
import com.xiaomifix.notification.util.FcmPinger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FcmHeartbeatService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val pulseMutex = Mutex()

    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmManager: AlarmManager? = null

    companion object {
        const val PREFS_NAME = "xiaomi_notifixer"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_LAST_PING = "last_ping_text"
        const val KEY_LAST_PING_OK = "last_ping_ok"
        const val KEY_INTERVAL_MS = "pref_interval_ms"

        const val CHANNEL_ID = "fcm_keep_alive_channel"
        const val NOTIFICATION_ID = 1001
        const val ALARM_REQUEST_CODE = 1003

        const val ACTION_PING_UPDATE = "com.xiaomifix.notification.PING_UPDATE"
        const val ACTION_STATE_CHANGED = "com.xiaomifix.notification.SERVICE_STATE"
        const val ACTION_TRIGGER_PULSE = "com.xiaomifix.notification.TRIGGER_PULSE"
        const val ACTION_STOP = "com.xiaomifix.notification.action.STOP"

        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_OK = "extra_ok"
        const val EXTRA_RUNNING = "extra_running"

        // Chu kỳ tối ưu cho mạng di động & Wi-Fi:
        // Mặc định: 120s (2 phút) giữ kênh push FCM luôn thông suốt
        const val DEFAULT_INTERVAL_MS = 120_000L
        const val FAST_INTERVAL_MS = 60_000L       // 1 phút (Tốc độ nổ thông báo nhanh nhất)
        const val BALANCED_INTERVAL_MS = 180_000L  // 3 phút (Cân bằng tiết kiệm pin)

        @Volatile
        var isRunning = false
            private set

        @SuppressLint("ApplySharedPref")
        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SERVICE_ENABLED, enabled)
                .commit()
        }

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, false)
        }

        fun getIntervalMs(context: Context): Long {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS)
        }

        @SuppressLint("ApplySharedPref")
        fun setIntervalMs(context: Context, ms: Long) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_INTERVAL_MS, ms)
                .commit()
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
        if (!isEnabled(this)) {
            stopSelf()
            return
        }

        isRunning = true
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "XiaomiNotificationFixer::FcmWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }

        createNotificationChannel()

        try {
            startServiceInForeground()
        } catch (_: Exception) {
            isRunning = false
            stopSelf()
            return
        }

        sendStateUpdate(true)
        triggerPulse()
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

    private fun triggerPulse() {
        serviceScope.launch {
            pulseMutex.withLock {
                if (!isEnabled(applicationContext)) {
                    stopPulseService()
                    return@withLock
                }
                performPulseInternal()
                val interval = getIntervalMs(applicationContext)
                scheduleNextAlarm(interval)
            }
        }
    }

    private suspend fun performPulseInternal() {
        // Tạm giữ WakeLock an toàn tối đa 10s để CPU không bị suspend giữa lúc ping qua mạng
        try {
            wakeLock?.acquire(10_000L)
        } catch (_: Exception) {}

        try {
            val interval = getIntervalMs(applicationContext)
            val result = FcmPinger.pingFcmServer(applicationContext)
            val intervalMin = (interval / 60_000L).toInt().coerceAtLeast(1)

            val status = if (result.success) {
                getString(
                    R.string.notification_ping_ok,
                    result.latencyMs,
                    intervalMin
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
        } finally {
            if (wakeLock?.isHeld == true) {
                try { wakeLock?.release() } catch (_: Exception) {}
            }
        }
    }

    private fun scheduleNextAlarm(delayMs: Long) {
        if (!isEnabled(this)) return
        val am = alarmManager ?: return

        val intent = Intent(this, HeartbeatAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_PULSE
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, intent, flags)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        }
    }

    private fun cancelAlarm() {
        try {
            val intent = Intent(this, HeartbeatAlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER_PULSE
            }
            val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, intent, flags)
            if (pendingIntent != null) {
                alarmManager?.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (_: Throwable) {}
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
        } catch (_: Exception) {}
    }

    private fun sendStateUpdate(running: Boolean) {
        try {
            sendBroadcast(
                Intent(ACTION_STATE_CHANGED).apply {
                    putExtra(EXTRA_RUNNING, running)
                    setPackage(packageName)
                }
            )
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun updateForegroundNotification(status: String) {
        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildForegroundNotification(status))
        } catch (_: Exception) {}
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
        if (intent?.action == ACTION_STOP || !isEnabled(this)) {
            stopPulseService()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_TRIGGER_PULSE) {
            triggerPulse()
        }

        return START_STICKY
    }

    private fun stopPulseService() {
        isRunning = false
        setEnabled(this, false)
        cancelAlarm()
        sendStateUpdate(false)
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        cancelAlarm()
        sendStateUpdate(false)
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
