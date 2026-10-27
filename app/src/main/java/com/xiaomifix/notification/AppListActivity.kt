package com.xiaomifix.notification

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiaomifix.notification.databinding.ActivityAppListBinding
import com.xiaomifix.notification.databinding.ItemAppBinding
import com.xiaomifix.notification.model.AppInfo
import com.xiaomifix.notification.util.MiuiHelper
import com.xiaomifix.notification.util.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListActivity : AppCompatActivity() {
    private lateinit var b: ActivityAppListBinding
    private val appList = mutableListOf<AppInfo>()
    private val displayedList = mutableListOf<AppInfo>()
    private lateinit var adapter: AppAdapter

    private val commonPkgs = setOf(
        // OTT & Nhắn tin
        "com.zing.zalo", "com.facebook.orca", "com.facebook.katana", "com.facebook.lite",
        "org.telegram.messenger", "org.thunderdog.challegram", "com.whatsapp",
        "com.google.android.gm", "com.microsoft.office.outlook", "com.discord",
        "com.viber.voip", "com.skype.raider", "com.slack", "com.tencent.mm",
        "jp.naver.line.android", "com.google.android.apps.messaging",
        // MXH & Widget
        "com.instagram.android", "com.locket.Locket", "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically", "com.twitter.android",
        // Mua sắm & Gọi xe / Giao đồ
        "com.shopee.vn", "com.lazada.android", "xyz.be.customer",
        "com.grabtaxi.passenger", "com.deliverynow",
        // Ngân hàng & Ví điện tử
        "com.mservice.momotransfer", "vn.com.vng.zalopay", "com.VCB",
        "com.vnpay.bidv", "vn.com.techcombank.bb.app", "com.techcombank.mobile",
        "com.mbmobile", "com.tpb.mb.gprsandroid", "com.vietinbank.ipay"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        adapter = AppAdapter(displayedList) { app ->
            MiuiHelper.openBatterySaverSettings(this, app.packageName)
        }
        b.rvApps.layoutManager = LinearLayoutManager(this)
        b.rvApps.adapter = adapter

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s.toString().lowercase()
                displayedList.clear()
                displayedList.addAll(if (q.isBlank()) appList else appList.filter {
                    it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                })
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        b.btnSelectAll.setOnClickListener {
            appList.forEach { it.isSelected = true }
            adapter.notifyDataSetChanged()
        }

        b.btnDeselectAll.setOnClickListener {
            appList.forEach { it.isSelected = false }
            adapter.notifyDataSetChanged()
        }

        b.btnApplyBatch.setOnClickListener { applyBatchFix() }
        loadApps()
    }

    private fun loadApps() {
        b.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val myPkg = packageName
            val list = mutableListOf<AppInfo>()
            val seenPkgs = mutableSetOf<String>()

            // 1. Lấy tất cả ứng dụng có Launcher Intent (app người dùng cài đặt và mở được)
            val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            for (ri in resolveInfos) {
                val pkgName = ri.activityInfo.packageName
                if (pkgName == myPkg || seenPkgs.contains(pkgName)) continue
                seenPkgs.add(pkgName)

                val label = ri.loadLabel(pm).toString()
                val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                val isTarget = commonPkgs.contains(pkgName)
                list.add(AppInfo(label, pkgName, icon, isSelected = isTarget))
            }

            // 2. Dự phòng: lấy thêm từ getInstalledApplications nếu có app chưa được add
            try {
                val installed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(0)
                }
                for (app in installed) {
                    val pkgName = app.packageName
                    if (pkgName == myPkg || seenPkgs.contains(pkgName)) continue
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isTarget = commonPkgs.contains(pkgName)
                    if (!isSystem || isTarget) {
                        seenPkgs.add(pkgName)
                        val label = pm.getApplicationLabel(app).toString()
                        val icon = try { pm.getApplicationIcon(app) } catch (_: Exception) { null }
                        list.add(AppInfo(label, pkgName, icon, isSelected = isTarget))
                    }
                }
            } catch (_: Exception) {}

            list.sortWith(compareByDescending<AppInfo> { commonPkgs.contains(it.packageName) }
                .thenBy { it.appName.lowercase() })

            withContext(Dispatchers.Main) {
                appList.clear()
                appList.addAll(list)
                displayedList.clear()
                displayedList.addAll(list)
                adapter.notifyDataSetChanged()
                b.progressBar.visibility = View.GONE
            }
        }
    }

    private fun applyBatchFix() {
        val selected = appList.filter { it.isSelected }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ít nhất 1 app", Toast.LENGTH_SHORT).show()
            return
        }
        if (ShizukuHelper.hasPermission()) {
            b.progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                val pkgList = selected.map { it.packageName }
                val count = ShizukuHelper.optimizeApps(pkgList)
                // Tự động tối ưu luôn cả app fix và hệ thống
                ShizukuHelper.optimizeSelfAndSystem(applicationContext)
                b.progressBar.visibility = View.GONE
                Toast.makeText(this@AppListActivity, "✦ Đã tối ưu siêu tốc $count app qua Shizuku!", Toast.LENGTH_LONG).show()
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle("Quyền Shizuku / ADB")
                .setMessage("Cấp quyền Shizuku để tối ưu tự động hoặc chạm từng app để mở cài đặt pin HyperOS (chọn Không hạn chế).")
                .setPositiveButton("Cấp quyền") { _, _ -> ShizukuHelper.requestPermission(1002) }
                .setNegativeButton("Đóng", null).show()
        }
    }

    inner class AppAdapter(
        private val items: List<AppInfo>,
        private val onClick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {
        inner class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(ItemAppBinding.inflate(LayoutInflater.from(p.context), p, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            h.b.tvAppName.text = item.appName
            h.b.tvPackage.text = item.packageName
            h.b.ivIcon.setImageDrawable(item.icon)
            h.b.cbSelect.setOnCheckedChangeListener(null)
            h.b.cbSelect.isChecked = item.isSelected
            h.b.tvStatus.text = if (commonPkgs.contains(item.packageName)) "App nhắn tin chính" else "Cài đặt pin HyperOS"
            h.b.cbSelect.setOnCheckedChangeListener { _, c -> item.isSelected = c }
            h.b.root.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }
}
