package com.xiaomifix.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.xiaomifix.notification.databinding.ActivityMainBinding
import com.xiaomifix.notification.service.FcmHeartbeatService
import com.xiaomifix.notification.util.MiuiHelper
import com.xiaomifix.notification.util.ShizukuHelper
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val prefs by lazy {
        getSharedPreferences(FcmHeartbeatService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var startServiceAfterPermission = false

    private val switchListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        if (checked) checkAndStart() else stopHeartbeatService()
    }

    private val pingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FcmHeartbeatService.ACTION_STATE_CHANGED) {
                updateHeartbeatUi(
                    intent.getBooleanExtra(FcmHeartbeatService.EXTRA_RUNNING, false)
                )
                return
            }

            val status = intent?.getStringExtra(FcmHeartbeatService.EXTRA_STATUS) ?: return
            val ok = intent.getBooleanExtra(FcmHeartbeatService.EXTRA_OK, true)
            updatePingPill(ok)
            b.tvPingStats.text = status
            b.tvPingStats.setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (ok) R.color.text_secondary else R.color.status_warning
                )
            )
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldStart = startServiceAfterPermission
        startServiceAfterPermission = false

        if (granted && MiuiHelper.isNotificationPermissionGranted(this)) {
            if (shouldStart) startHeartbeatService()
        } else {
            setSwitchSilently(false)
            updateHeartbeatUi(false)
            showNotificationPermissionDialog()
        }
        updatePermissionIndicators()
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateShizukuState()
        maybeOptimizeSelf()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateShizukuState()
    }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
        // AppListActivity dùng request code riêng; bỏ qua để không tối ưu
        // hoặc hiện toast trùng khi quyền được cấp từ màn hình danh sách app.
        if (requestCode == REQUEST_SHIZUKU) {
            val granted = result == PackageManager.PERMISSION_GRANTED
            updateShizukuState()
            if (granted) {
                maybeOptimizeSelf(force = true)
                Toast.makeText(this@MainActivity, R.string.msg_shizuku_granted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.msg_shizuku_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }

        initViews()
        registerShizukuListeners()
    }

    private fun initViews() {
        b.tvDeviceModel.text = MiuiHelper.getDeviceInfo()
        refreshServiceState()

        b.switchFcmHeartbeat.setOnCheckedChangeListener(switchListener)

        b.btnAppList.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }
        b.btnNotificationPermission.setOnClickListener { onNotificationPermissionClicked() }
        b.btnAutostart.setOnClickListener { MiuiHelper.openAutostart(this) }
        b.btnBatterySaver.setOnClickListener { MiuiHelper.openBatterySaverSettings(this) }
        b.btnBatteryOpt.setOnClickListener {
            if (MiuiHelper.isBatteryOptimizationIgnored(this)) {
                MiuiHelper.openBatteryOptimizationSettings(this)
            } else {
                MiuiHelper.requestIgnoreBatteryOptimizations(this)
            }
        }
        b.btnNotificationListener.setOnClickListener {
            MiuiHelper.openNotificationListenerSettings(this)
        }

        b.permissionCard.clipToOutline = true
        b.layoutShizukuPanel.setOnClickListener { handleShizukuClick() }
    }

    private fun checkAndStart() {
        if (!MiuiHelper.isNotificationPermissionGranted(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startServiceAfterPermission = true
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Android 12 trở xuống: mở cài đặt thông báo của ứng dụng.
                startServiceAfterPermission = false
                setSwitchSilently(false)
                updateHeartbeatUi(false)
                showNotificationPermissionDialog()
            }
            return
        }
        startHeartbeatService()
    }

    private fun startHeartbeatService() {
        try {
            FcmHeartbeatService.setEnabled(this, true)
            if (!FcmHeartbeatService.isServiceRunning(this)) {
                prefs.edit()
                    .remove(FcmHeartbeatService.KEY_LAST_PING)
                    .putBoolean(FcmHeartbeatService.KEY_LAST_PING_OK, true)
                    .apply()
            }
            ContextCompat.startForegroundService(
                this,
                Intent(this, FcmHeartbeatService::class.java)
            )
            updateHeartbeatUi(true)
            Toast.makeText(this, R.string.msg_service_started, Toast.LENGTH_SHORT).show()

            // Nếu hệ thống chặn FGS, đưa công tắc về trạng thái đúng.
            b.root.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                if (FcmHeartbeatService.isEnabled(this) &&
                    !FcmHeartbeatService.isServiceRunning(this)
                ) {
                    FcmHeartbeatService.setEnabled(this, false)
                    setSwitchSilently(false)
                    updateHeartbeatUi(false)
                    Toast.makeText(
                        this,
                        R.string.msg_service_not_running,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, 2_500L)
        } catch (e: Exception) {
            FcmHeartbeatService.setEnabled(this, false)
            setSwitchSilently(false)
            updateHeartbeatUi(false)
            Toast.makeText(
                this,
                getString(R.string.msg_service_start_failed, e.message ?: "unknown"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopHeartbeatService() {
        FcmHeartbeatService.setEnabled(this, false)
        stopService(Intent(this, FcmHeartbeatService::class.java))
        updateHeartbeatUi(false)
        Toast.makeText(this, R.string.msg_service_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun refreshServiceState() {
        updateHeartbeatUi(FcmHeartbeatService.isServiceRunning(this))
    }

    private fun updateHeartbeatUi(running: Boolean) {
        setSwitchSilently(running)

        if (running) {
            val lastPing = prefs.getString(FcmHeartbeatService.KEY_LAST_PING, null)
            val lastPingOk = prefs.getBoolean(FcmHeartbeatService.KEY_LAST_PING_OK, true)
            updatePingPill(lastPingOk)
            b.tvPingStats.text = lastPing ?: getString(R.string.status_connecting)
            b.tvPingStats.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (lastPingOk) R.color.text_secondary else R.color.status_warning
                )
            )
        } else {
            setPill(
                b.tvHeartbeatStatus,
                getString(R.string.status_off),
                R.color.pill_off_bg,
                R.color.text_tertiary
            )
            b.tvPingStats.text = getString(R.string.status_no_data)
            b.tvPingStats.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
        }
    }

    private fun updatePingPill(ok: Boolean) {
        setPill(
            b.tvHeartbeatStatus,
            getString(if (ok) R.string.status_protected else R.string.status_reconnecting),
            if (ok) R.color.pill_good_bg else R.color.pill_warn_bg,
            if (ok) R.color.status_good else R.color.status_warning
        )
    }

    private fun setSwitchSilently(checked: Boolean) {
        if (b.switchFcmHeartbeat.isChecked == checked) return
        b.switchFcmHeartbeat.setOnCheckedChangeListener(null)
        b.switchFcmHeartbeat.isChecked = checked
        b.switchFcmHeartbeat.setOnCheckedChangeListener(switchListener)
    }

    private fun onNotificationPermissionClicked() {
        if (MiuiHelper.isNotificationPermissionGranted(this)) {
            MiuiHelper.openAppNotificationSettings(this, packageName)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startServiceAfterPermission = false
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Android 12 trở xuống không có runtime permission; mở cài đặt.
            MiuiHelper.openAppNotificationSettings(this, packageName)
        }
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_notification_permission_title)
            .setMessage(R.string.dialog_notification_permission_message)
            .setPositiveButton(R.string.action_open_settings) { _, _ ->
                MiuiHelper.openAppNotificationSettings(this, packageName)
            }
            .setNegativeButton(R.string.action_later, null)
            .show()
    }

    private fun updatePermissionIndicators() {
        val notificationGranted = MiuiHelper.isNotificationPermissionGranted(this)
        val dozeGranted = MiuiHelper.isBatteryOptimizationIgnored(this)
        val listenerGranted = MiuiHelper.isNotificationListenerEnabled(this)

        setGrantStatus(b.tvNotificationPermissionStatus, notificationGranted)
        setGrantStatus(b.tvBatteryOptStatus, dozeGranted)
        setGrantStatus(b.tvNotificationListenerStatus, listenerGranted)

        b.tvAutostartStatus.text = getString(R.string.status_action_open)
        b.tvBatterySaverStatus.text = getString(R.string.status_action_open)

        val completed = listOf(notificationGranted, dozeGranted, listenerGranted).count { it }
        b.tvPermissionProgress.text = getString(R.string.permission_progress, completed)
    }

    private fun setGrantStatus(view: TextView, granted: Boolean) {
        view.text = getString(
            if (granted) R.string.status_granted else R.string.status_action_grant
        )
        view.setTextColor(
            ContextCompat.getColor(
                this,
                if (granted) R.color.status_good else R.color.accent
            )
        )
    }

    private fun updateShizukuState() {
        when {
            ShizukuHelper.hasPermission() -> setPill(
                b.tvShizukuStatus,
                getString(R.string.status_shizuku_connected),
                R.color.pill_good_bg,
                R.color.status_good
            )

            ShizukuHelper.isAvailable() -> setPill(
                b.tvShizukuStatus,
                getString(R.string.status_shizuku_permission),
                R.color.pill_warn_bg,
                R.color.status_warning
            )

            else -> setPill(
                b.tvShizukuStatus,
                getString(R.string.status_shizuku_offline),
                R.color.pill_off_bg,
                R.color.text_tertiary
            )
        }
    }

    private fun setPill(view: TextView, text: String, backgroundColor: Int, textColor: Int) {
        view.text = text
        view.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, backgroundColor)
        )
        view.setTextColor(ContextCompat.getColor(this, textColor))
    }

    private fun handleShizukuClick() {
        when {
            !ShizukuHelper.isAvailable() -> showShizukuUnavailableDialog()
            !ShizukuHelper.hasPermission() -> ShizukuHelper.requestPermission(REQUEST_SHIZUKU)
            else -> maybeOptimizeSelf(force = true)
        }
    }

    private fun showShizukuUnavailableDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_shizuku_title)
            .setMessage(R.string.dialog_shizuku_message)
            .setPositiveButton(R.string.action_open_shizuku) { _, _ ->
                MiuiHelper.openShizukuApp(this)
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun maybeOptimizeSelf(force: Boolean = false) {
        if (!ShizukuHelper.hasPermission()) return

        val now = System.currentTimeMillis()
        val lastRun = prefs.getLong(KEY_LAST_OPTIMIZE, 0L)
        if (!force && now - lastRun < AUTO_OPTIMIZE_INTERVAL_MS) return

        prefs.edit().putLong(KEY_LAST_OPTIMIZE, now).apply()
        lifecycleScope.launch {
            ShizukuHelper.optimizeSelfAndSystem(applicationContext)
            if (force) {
                Toast.makeText(
                    this@MainActivity,
                    R.string.msg_self_optimized,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun registerShizukuListeners() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(shizukuListener)
        } catch (_: Throwable) {
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(FcmHeartbeatService.ACTION_PING_UPDATE).apply {
            addAction(FcmHeartbeatService.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            pingReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        maybeRestartService()
        refreshServiceState()
        updateShizukuState()
        updatePermissionIndicators()
        maybeOptimizeSelf()
    }

    /**
     * Tự khởi động lại dịch vụ nếu người dùng đã bật trước đó nhưng tiến trình
     * bị HyperOS dọn trong lúc app đóng. Không làm gì nếu người dùng đã tắt.
     */
    private fun maybeRestartService() {
        if (!FcmHeartbeatService.isEnabled(this)) return
        if (FcmHeartbeatService.isServiceRunning(this)) return

        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, FcmHeartbeatService::class.java)
            )
            b.root.postDelayed({
                if (!isFinishing && !isDestroyed) refreshServiceState()
            }, 600L)
        } catch (_: Exception) {
            // Thiết bị có thể chặn FGS; trạng thái sẽ được phản ánh khi
            // người dùng bật lại công tắc.
        }
    }

    override fun onStop() {
        try {
            unregisterReceiver(pingReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_SHIZUKU = 1001
        private const val KEY_LAST_OPTIMIZE = "last_auto_optimize"
        private const val AUTO_OPTIMIZE_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
