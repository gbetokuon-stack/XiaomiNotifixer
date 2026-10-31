package com.xiaomifix.notification.service

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xiaomifix.notification.util.MiuiHelper

class NotificationKeepAliveListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Khi listener được kết nối, tiến trình nhận mức ưu tiên cao hơn từ hệ thống.
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Chỉ yêu cầu kết nối lại nếu người dùng vẫn đang cấp quyền này trong hệ thống.
        if (!MiuiHelper.isNotificationListenerEnabled(this)) return

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (MiuiHelper.isNotificationListenerEnabled(this)) {
                    requestRebind(
                        ComponentName(this, NotificationKeepAliveListener::class.java)
                    )
                }
            } catch (_: Exception) {
            }
        }, REBIND_DELAY_MS)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Không can thiệp vào nội dung thông báo của ứng dụng khác.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    companion object {
        private const val REBIND_DELAY_MS = 2_000L
    }
}
