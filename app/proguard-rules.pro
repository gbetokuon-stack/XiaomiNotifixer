# Shizuku — giữ toàn bộ public API để Shizuku binder hoạt động đúng
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }

# Giữ toàn bộ code app để service, receiver, broadcast không bị shrink nhầm
-keep class com.xiaomifix.notification.** { *; }

# Giữ các class Android cần thiết cho foreground service + notification listener
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.service.notification.NotificationListenerService

# Coroutines — cần cho suspend functions
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.android.** { *; }
