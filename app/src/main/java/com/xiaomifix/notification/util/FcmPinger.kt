package com.xiaomifix.notification.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.xiaomifix.notification.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object FcmPinger {
    private const val FCM_HOST = "mtalk.google.com"
    private const val FCM_PORT_PRIMARY = 5228
    private const val FCM_PORT_FALLBACK = 443
    private const val TIMEOUT_MS = 2500
    private const val RECONNECT_THROTTLE_MS = 30 * 60 * 1000L

    private var lastReconnectAt = 0L

    data class PingResult(val success: Boolean, val latencyMs: Long, val message: String)

    suspend fun pingFcmServer(context: Context): PingResult = withContext(Dispatchers.IO) {
        if (!hasInternet(context)) {
            return@withContext PingResult(
                false,
                0,
                context.getString(R.string.ping_no_network)
            )
        }

        val start = System.currentTimeMillis()
        var socket: Socket? = null
        try {
            socket = Socket()
            try {
                socket.connect(InetSocketAddress(FCM_HOST, FCM_PORT_PRIMARY), TIMEOUT_MS)
            } catch (_: Exception) {
                socket.close()
                socket = Socket()
                socket.connect(InetSocketAddress(FCM_HOST, FCM_PORT_FALLBACK), TIMEOUT_MS)
            }
            val latency = System.currentTimeMillis() - start

            // Kết nối thành công: không gửi GCM_RECONNECT để tránh bắt tay TLS lặp lại.
            PingResult(true, latency, context.getString(R.string.ping_ok, latency))
        } catch (e: Exception) {
            // Chỉ khi kết nối thất bại mới kích hoạt cơ chế phục hồi socket của GMS.
            // Giới hạn tần suất để không ép GMS bắt tay TLS liên tục trên mạng
            // chặn cổng FCM.
            val now = System.currentTimeMillis()
            if (now - lastReconnectAt >= RECONNECT_THROTTLE_MS) {
                lastReconnectAt = now
                try {
                    context.sendBroadcast(
                        Intent("com.google.android.intent.action.GCM_RECONNECT").apply {
                            setPackage("com.google.android.gms")
                        }
                    )
                } catch (_: Exception) {
                }
            }

            PingResult(
                false,
                System.currentTimeMillis() - start,
                context.getString(R.string.ping_lost)
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun hasInternet(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }
}
