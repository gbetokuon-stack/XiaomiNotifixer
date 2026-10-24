package com.xiaomifix.notification.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationKeepAliveListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Khi NotificationListener được kết nối, tiến trình có mức độ ưu tiên hệ thống cao nhất
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Có thể bổ sung logging hoặc trigger wake cho ứng dụng mục tiêu
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
