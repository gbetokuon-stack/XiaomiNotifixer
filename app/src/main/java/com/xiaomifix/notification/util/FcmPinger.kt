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
    private const val TIMEOUT_MS = 3500

    data class PingResult(val success: Boolean, val latencyMs: Long, val message: String)

    suspend fun pingFcmServer(context: Context): PingResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var socket: Socket? = null
        try {
            socket = Socket()
            try {
                socket.connect(InetSocketAddress(FCM_HOST, FCM_PORT_PRIMARY), TIMEOUT_MS)
            } catch (_: Exception) {
                // Thử cổng dự phòng 443 nếu cổng 5228 bị firewall chặn
                socket.close()
                socket = Socket()
                socket.connect(InetSocketAddress(FCM_HOST, FCM_PORT_FALLBACK), TIMEOUT_MS)
            }
            val latency = System.currentTimeMillis() - start

            // Broadcast GCM reconnect trigger to Google Play Services
            val gcmIntent = Intent("com.google.android.intent.action.GCM_RECONNECT").apply {
                setPackage("com.google.android.gms")
            }
            try {
                context.sendBroadcast(gcmIntent)
            } catch (_: Exception) {}

            PingResult(true, latency, "FCM Socket OK (${latency}ms)")
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            PingResult(false, latency, "Lỗi ping FCM: ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
