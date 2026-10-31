package com.xiaomifix.notification.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Receiver nhận tín hiệu đánh thức từ AlarmManager khi máy ở chế độ Doze / Deep Sleep.
 * Đảm bảo khôi phục và duy trì FcmHeartbeatService mà không bị chặn bởi giới hạn chạy nền.
 */
class HeartbeatAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!FcmHeartbeatService.isEnabled(context)) return

        val serviceIntent = Intent(context, FcmHeartbeatService::class.java).apply {
            action = FcmHeartbeatService.ACTION_TRIGGER_PULSE
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Throwable) {
            // Trường hợp thiết bị giới hạn FGS ngặt nghèo, ứng dụng sẽ tiếp tục
            // được kích hoạt ở lần mở tiếp theo hoặc qua BootReceiver.
        }
    }
}
