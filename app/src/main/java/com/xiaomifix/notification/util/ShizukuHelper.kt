package com.xiaomifix.notification.util

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!isAvailable()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            if (isAvailable() && !hasPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Throwable) {}
    }

    suspend fun runCommand(command: String): String = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext "Shizuku chưa được cấp quyền."
        }
        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            // Gộp stderr vào stdout để không bỏ sót lỗi và tránh đầy buffer.
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", "$command 2>&1"),
                null,
                null
            ) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Throwable) {
            "Lỗi thực thi: ${e.message}"
        }
    }

    suspend fun optimizeAppForNotifications(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext false
        val commands = listOf(
            "dumpsys deviceidle whitelist +$packageName",
            "cmd appops set $packageName RUN_IN_BACKGROUND allow",
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $packageName AUTO_START allow",
            "cmd appops set $packageName 10008 allow",
            "cmd appops set $packageName WAKE_LOCK allow",
            "cmd appops set $packageName START_FOREGROUND allow",
            "cmd appops set $packageName POST_NOTIFICATION allow",
            "cmd appops set $packageName SCHEDULE_EXACT_ALARM allow",
            "cmd appops set $packageName ACCESS_RESTRICTED_SETTINGS allow",
            "cmd power set-adaptive-power-saver-enabled false",
            "cmd power set-mode 0"
        )
        runCommand(commands.joinToString(" ; "))
        true
    }

    suspend fun optimizeApps(packageNames: List<String>): Int = withContext(Dispatchers.IO) {
        if (!hasPermission() || packageNames.isEmpty()) return@withContext 0

        // Chia nhỏ danh sách để tránh vượt giới hạn độ dài đối số của shell (E2BIG)
        // khi người dùng chọn rất nhiều ứng dụng cùng lúc.
        packageNames.chunked(BATCH_SIZE).forEach { batch ->
            val sb = StringBuilder()
            for (pkg in batch) {
                sb.append("dumpsys deviceidle whitelist +$pkg ; ")
                sb.append("cmd appops set $pkg RUN_IN_BACKGROUND allow ; ")
                sb.append("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow ; ")
                sb.append("cmd appops set $pkg AUTO_START allow ; ")
                sb.append("cmd appops set $pkg 10008 allow ; ")
                sb.append("cmd appops set $pkg WAKE_LOCK allow ; ")
                sb.append("cmd appops set $pkg START_FOREGROUND allow ; ")
                sb.append("cmd appops set $pkg POST_NOTIFICATION allow ; ")
                sb.append("cmd appops set $pkg SCHEDULE_EXACT_ALARM allow ; ")
                sb.append("cmd appops set $pkg ACCESS_RESTRICTED_SETTINGS allow ; ")
            }
            runCommand(sb.toString())
        }

        // Các lệnh toàn hệ thống chỉ cần chạy một lần.
        runCommand(
            "cmd power set-adaptive-power-saver-enabled false ; cmd power set-mode 0"
        )
        packageNames.size
    }

    suspend fun optimizeSelfAndSystem(context: android.content.Context): Boolean = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext false
        val myPkg = context.packageName
        val commands = listOf(
            // Whitelist app fix & Google Play Services trong Doze
            "dumpsys deviceidle whitelist +$myPkg",
            "dumpsys deviceidle whitelist +com.google.android.gms",
            "dumpsys deviceidle whitelist +com.google.android.gsf",
            // Cấp toàn quyền chạy ngầm & autostart cho app fix
            "cmd appops set $myPkg RUN_IN_BACKGROUND allow",
            "cmd appops set $myPkg RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $myPkg AUTO_START allow",
            "cmd appops set $myPkg 10008 allow",
            "cmd appops set $myPkg WAKE_LOCK allow",
            "cmd appops set $myPkg START_FOREGROUND allow",
            "cmd appops set $myPkg POST_NOTIFICATION allow",
            "cmd appops set $myPkg SCHEDULE_EXACT_ALARM allow",
            "cmd appops set $myPkg ACCESS_RESTRICTED_SETTINGS allow",
            "cmd appops set $myPkg SYSTEM_ALERT_WINDOW allow",
            // Cấp quyền cho Google Play Services không bị ngủ đông
            "cmd appops set com.google.android.gms AUTO_START allow",
            "cmd appops set com.google.android.gms 10008 allow",
            "cmd appops set com.google.android.gms RUN_IN_BACKGROUND allow",
            // Vô hiệu hóa Phantom Process Killer trên Android 12-15
            "/system/bin/device_config put activity_manager max_phantom_processes 2147483647",
            // Rút ngắn chu kỳ heartbeat của GMS xuống 120s
            "settings put global gcm_heartbeat_interval_wifi 120000",
            "settings put global gcm_heartbeat_interval_mobile 120000",
            // Tắt giới hạn tiết kiệm pin thích ứng
            "cmd power set-adaptive-power-saver-enabled false",
            "cmd power set-mode 0"
        )
        runCommand(commands.joinToString(" ; "))
        true
    }

    private const val BATCH_SIZE = 25
}
