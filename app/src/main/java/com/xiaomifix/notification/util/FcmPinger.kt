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

    // Bộ Intent chuẩn kích hoạt Google Play Services (GMS) xả thông báo FCM bị nghẽn
    private val FCM_WAKE_INTENTS = listOf(
        "com.google.android.intent.action.GCM_RECONNECT",
        "com.google.android.intent.action.GTALK_HEARTBEAT",
        "com.google.android.intent.action.MCS_HEARTBEAT"
    )

    data class PingResult(val success: Boolean, val latencyMs: Long, val message: String)

    /**
     * Phát broadcast đánh thức Google Play Services để xả toàn bộ thông báo đang nghẽn.
     */
    fun triggerFcmWakeBroadcasts(context: Context) {
        for (action in FCM_WAKE_INTENTS) {
            try {
                context.sendBroadcast(
                    Intent(action).apply {
                        setPackage("com.google.android.gms")
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    }
                )
            } catch (_: Exception) {}
        }
    }

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
        var isSuccess = false
        var latency = 0L

        try {
            socket = Socket()
            try {
                socket.connect(InetSocketAddress(FCM_HOST, FCM_PORT_PRIMARY), TIMEOUT_MS)
            } catch (_: Exception) {
                socket.close()
                socket = Socket()
                socket.connect(InetSocketAddress(FCM_HOST, FCM_PORT_FALLBACK), TIMEOUT_MS)
            }
            latency = System.currentTimeMillis() - start
            isSuccess = true
        } catch (_: Exception) {
            latency = System.currentTimeMillis() - start
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }

        // BẮT BUỘC: Luôn phát sóng đánh thức GMS ở mỗi chu kỳ để duy trì socket push
        // và giải phóng tức thì mọi thông báo đang chờ tại máy chủ Google!
        triggerFcmWakeBroadcasts(context)

        if (isSuccess) {
            PingResult(true, latency, context.getString(R.string.ping_ok, latency))
        } else {
            PingResult(false, latency, context.getString(R.string.ping_lost))
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
