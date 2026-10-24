package com.xiaomifix.notification.util

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object FcmPinger {
    private const val FCM_HOST = "mtalk.google.com"
    private const val FCM_PORT_PRIMARY = 5228
    private const val FCM_PORT_FALLBACK = 443
    private const val TIMEOUT_MS = 2500

    data class PingResult(val success: Boolean, val latencyMs: Long, val message: String)

    suspend fun pingFcmServer(context: Context): PingResult = withContext(Dispatchers.IO) {
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

            // KHI KẾT NỐI THÀNH CÔNG: KHÔNG broadcast GCM_RECONNECT để tránh ép Google Play Services
            // bắt tay TLS lại gây hao pin nghiêm trọng.
            PingResult(true, latency, "FCM Socket OK (${latency}ms)")
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start

            // CHỈ KHI KẾT NỐI THẤT BẠI: Mới kích hoạt GCM_RECONNECT để khôi phục socket
            try {
                val gcmIntent = Intent("com.google.android.intent.action.GCM_RECONNECT").apply {
                    setPackage("com.google.android.gms")
                }
                context.sendBroadcast(gcmIntent)
            } catch (_: Exception) {}

            PingResult(false, latency, "Lỗi ping FCM: ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}

