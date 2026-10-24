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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.xiaomifix.notification.databinding.ActivityMainBinding
import com.xiaomifix.notification.service.FcmHeartbeatService
import com.xiaomifix.notification.util.MiuiHelper
import com.xiaomifix.notification.util.ShizukuHelper
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

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, res ->
        updateShizuku(res == PackageManager.PERMISSION_GRANTED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        initViews()
        try { Shizuku.addRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) {}
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
            b.tvHeartbeatStatus.text = getString(R.string.service_running)
            b.tvHeartbeatStatus.setTextColor(ContextCompat.getColor(this, R.color.status_good))
        } else {
            b.tvHeartbeatStatus.text = getString(R.string.service_stopped)
            b.tvHeartbeatStatus.setTextColor(ContextCompat.getColor(this, R.color.status_bad))
            b.tvPingStats.text = "Socket: mtalk.google.com:5228 (Đã dừng)"
        }
    }

    private fun updateShizuku(hasPermission: Boolean) {
        if (hasPermission) {
            b.tvShizukuStatus.text = getString(R.string.shizuku_status_active)
            b.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.status_good))
        } else {
            b.tvShizukuStatus.text = getString(R.string.shizuku_status_inactive)
            b.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
        }
    }

    override fun onResume() {
        super.onResume()
        updateShizuku(ShizukuHelper.hasPermission())
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
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) {}
        super.onDestroy()
    }
}
