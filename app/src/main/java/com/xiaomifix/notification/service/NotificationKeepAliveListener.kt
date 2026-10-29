package com.xiaomifix.notification.service

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationKeepAliveListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Khi listener được kết nối, tiến trình nhận mức ưu tiên cao hơn từ hệ thống.
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Android thường tự rebind, nhưng chủ động yêu cầu lại giúp listener
        // không bị mất sau khi HyperOS dọn tiến trình.
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                requestRebind(
                    ComponentName(this, NotificationKeepAliveListener::class.java)
                )
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
