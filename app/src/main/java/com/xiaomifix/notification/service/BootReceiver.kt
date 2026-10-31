package com.xiaomifix.notification.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        // Chỉ khôi phục dịch vụ nếu người dùng đã bật trước khi khởi động lại máy hoặc cập nhật app.
        if (!FcmHeartbeatService.isEnabled(context)) return

        try {
            // Dùng biến riêng để tránh trùng tên với val `action` ở trên
            val serviceIntent = Intent(context, FcmHeartbeatService::class.java)
            serviceIntent.action = FcmHeartbeatService.ACTION_TRIGGER_PULSE
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Throwable) {
            // Android 15+ có thể chặn FGS specialUse từ BOOT_COMPLETED.
            // Người dùng vẫn có thể mở app để bật lại dịch vụ.
        }
    }
}
