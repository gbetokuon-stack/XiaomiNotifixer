package com.xiaomifix.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.xiaomifix.notification.databinding.ActivityMainBinding
import com.xiaomifix.notification.service.FcmHeartbeatService
import com.xiaomifix.notification.util.MiuiHelper
import com.xiaomifix.notification.util.ShizukuHelper
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    private val pingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(FcmHeartbeatService.EXTRA_STATUS)
            if (!status.isNullOrBlank()) b.tvPingStats.text = status
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startHeartbeatService()
        else Toast.makeText(this, "Cần cấp quyền thông báo!", Toast.LENGTH_SHORT).show()
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        val hasPerm = ShizukuHelper.hasPermission()
        updateShizuku(hasPerm)
        if (hasPerm) {
            lifecycleScope.launch {
                ShizukuHelper.optimizeSelfAndSystem(applicationContext)
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateShizuku(false)
    }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, res ->
        val granted = (res == PackageManager.PERMISSION_GRANTED)
        updateShizuku(granted)
        if (granted) {
            lifecycleScope.launch {
                ShizukuHelper.optimizeSelfAndSystem(applicationContext)
                Toast.makeText(this@MainActivity, "✦ Tự động tối ưu hệ thống & App Fix qua Shizuku!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initViews()
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(shizukuListener)
        } catch (_: Throwable) {}
    }

    private fun initViews() {
        b.tvDeviceModel.text = MiuiHelper.getDeviceInfo()
        b.switchFcmHeartbeat.isChecked = FcmHeartbeatService.isRunning
        updateHeartbeatUi(FcmHeartbeatService.isRunning)

        b.switchFcmHeartbeat.setOnCheckedChangeListener { _, checked ->
            if (checked) checkAndStart() else stopHeartbeatService()
        }

        b.btnAppList.setOnClickListener { startActivity(Intent(this, AppListActivity::class.java)) }
        b.btnAutostart.setOnClickListener { MiuiHelper.openAutostart(this) }
        b.btnBatterySaver.setOnClickListener { MiuiHelper.openBatterySaverSettings(this) }
        b.btnBatteryOpt.setOnClickListener { MiuiHelper.requestIgnoreBatteryOptimizations(this) }
        b.btnNotificationListener.setOnClickListener { MiuiHelper.openNotificationListenerSettings(this) }

        b.layoutShizukuPanel.setOnClickListener {
            if (!ShizukuHelper.isAvailable()) {
                AlertDialog.Builder(this)
                    .setTitle("🔮 Trạng thái Shizuku")
                    .setMessage("Shizuku chưa chạy trên thiết bị.\n\nNếu muốn dùng Shizuku để tối ưu hàng loạt tự động:\n1. Mở app Shizuku\n2. Bật Wireless Debugging để Start Shizuku\n3. Sau khi tối ưu xong, tắt Wireless Debugging để dùng VNeID bình thường.")
                    .setPositiveButton("Đã hiểu", null)
                    .show()
            } else if (!ShizukuHelper.hasPermission()) {
                ShizukuHelper.requestPermission(1001)
            } else {
                lifecycleScope.launch {
                    ShizukuHelper.optimizeSelfAndSystem(applicationContext)
                    Toast.makeText(this@MainActivity, "✦ Đã tối ưu hệ thống & tự khởi chạy cho App Fix!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startHeartbeatService()
    }

    private fun startHeartbeatService() {
        val i = Intent(this, FcmHeartbeatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        updateHeartbeatUi(true)
        Toast.makeText(this, R.string.msg_service_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopHeartbeatService() {
        stopService(Intent(this, FcmHeartbeatService::class.java))
        updateHeartbeatUi(false)
        Toast.makeText(this, R.string.msg_service_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun updateHeartbeatUi(running: Boolean) {
        if (running) {
            b.tvHeartbeatStatus.text = "● BẤT TỬ TRỰC TUYẾN [ONLINE 100%]"
            b.tvHeartbeatStatus.setTextColor(ContextCompat.getColor(this, R.color.status_good))
            b.tvWibuBadge.text = "✦ ACTIVE 100%"
            b.tvWibuBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_neon_green))
        } else {
            b.tvHeartbeatStatus.text = "● SYSTEM OFFLINE [STANDBY]"
            b.tvHeartbeatStatus.setTextColor(ContextCompat.getColor(this, R.color.status_bad))
            b.tvPingStats.text = "> Socket: mtalk.google.com:5228\n> Mode: Ultra Low Power Deep-Sleep\n> Status: Standby"
            b.tvWibuBadge.text = "✦ STANDBY"
        }
    }

    private fun updateShizuku(hasPermission: Boolean) {
        if (hasPermission) {
            b.tvShizukuStatus.text = "✦ OVERDRIVE ACTIVE"
            b.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.status_good))
        } else {
            b.tvShizukuStatus.text = "✧ SHIZUKU OFFLINE"
            b.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
        }
    }

    private fun updatePermissionIndicators() {
        if (MiuiHelper.isBatteryOptimizationIgnored(this)) {
            b.tvBatteryOptStatus.text = "✓ ĐÃ CẤP"
            b.tvBatteryOptStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_neon_green))
        } else {
            b.tvBatteryOptStatus.text = "Cho Phép ›"
            b.tvBatteryOptStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_gold))
        }

        if (MiuiHelper.isNotificationListenerEnabled(this)) {
            b.tvNotificationListenerStatus.text = "✓ ĐÃ CẤP"
            b.tvNotificationListenerStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_neon_green))
        } else {
            b.tvNotificationListenerStatus.text = "Bất Tử ›"
            b.tvNotificationListenerStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_neon_green))
        }
    }

    override fun onResume() {
        super.onResume()
        val hasShizuku = ShizukuHelper.hasPermission()
        updateShizuku(hasShizuku)
        if (hasShizuku) {
            lifecycleScope.launch {
                ShizukuHelper.optimizeSelfAndSystem(applicationContext)
            }
        }
        updatePermissionIndicators()
        b.switchFcmHeartbeat.isChecked = FcmHeartbeatService.isRunning
        updateHeartbeatUi(FcmHeartbeatService.isRunning)
        val filter = IntentFilter(FcmHeartbeatService.ACTION_PING_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pingReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(pingReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
