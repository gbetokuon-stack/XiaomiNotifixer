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
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
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
            // Whitelist Doze Mode (Device Idle)
            "dumpsys deviceidle whitelist +$packageName",
            // AppOps chuẩn Android & MIUI/HyperOS
            "cmd appops set $packageName RUN_IN_BACKGROUND allow",
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $packageName AUTO_START allow",
            "cmd appops set $packageName 10008 allow", // OP_AUTO_START trong mã nguồn MIUI
            "cmd appops set $packageName WAKE_LOCK allow",
            "cmd appops set $packageName START_FOREGROUND allow",
            "cmd appops set $packageName POST_NOTIFICATION allow",
            "cmd appops set $packageName SCHEDULE_EXACT_ALARM allow",
            "cmd appops set $packageName ACCESS_RESTRICTED_SETTINGS allow",
            // Can thiệp HyperOS / MIUI PowerKeeper (Tiết kiệm pin không hạn chế)
            "dumpsys power set-adaptive-power-saver-enabled false",
            "cmd power set-mode 0"
        )
        for (cmd in commands) {
            runCommand(cmd)
        }
        true
    }
}
