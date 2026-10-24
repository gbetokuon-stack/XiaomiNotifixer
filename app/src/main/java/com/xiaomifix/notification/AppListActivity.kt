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
        "com.zing.zalo", "com.facebook.orca", "org.telegram.messenger",
        "org.thunderdog.challegram", "com.whatsapp", "com.google.android.gm",
        "com.microsoft.office.outlook", "com.discord", "com.viber.voip"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(b.root)

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

        b.btnApplyBatch.setOnClickListener { applyBatchFix() }
        loadApps()
    }

    private fun loadApps() {
        b.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = mutableListOf<AppInfo>()
            for (pkg in packages) {
                val isSystem = (pkg.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isTarget = commonPkgs.contains(pkg.packageName)
                if (!isSystem || isTarget) {
                    val label = pm.getApplicationLabel(pkg).toString()
                    val icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }
                    list.add(AppInfo(label, pkg.packageName, icon, isSelected = isTarget))
                }
            }
            list.sortByDescending { commonPkgs.contains(it.packageName) }
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
                var c = 0
                for (a in selected) {
                    if (ShizukuHelper.optimizeAppForNotifications(a.packageName)) c++
                }
                b.progressBar.visibility = View.GONE
                Toast.makeText(this@AppListActivity, "Đã tối ưu $c app qua Shizuku!", Toast.LENGTH_LONG).show()
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
