package com.xiaomifix.notification

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiaomifix.notification.databinding.ActivityAppListBinding
import com.xiaomifix.notification.databinding.ItemAppBinding
import com.xiaomifix.notification.model.AppInfo
import com.xiaomifix.notification.service.FcmHeartbeatService
import com.xiaomifix.notification.util.MiuiHelper
import com.xiaomifix.notification.util.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.Locale

class AppListActivity : AppCompatActivity() {
    private lateinit var b: ActivityAppListBinding
    private val appList = mutableListOf<AppInfo>()
    private val displayedList = mutableListOf<AppInfo>()
    private lateinit var adapter: AppAdapter
    private val prefs by lazy {
        getSharedPreferences(FcmHeartbeatService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val optimizedPkgs = mutableSetOf<String>()
    private var isApplying = false
    private var loaded = false

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.msg_shizuku_granted, Toast.LENGTH_SHORT).show()
                applyBatchFix()
            } else {
                Toast.makeText(this, R.string.msg_shizuku_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val commonPkgs = setOf(
        // OTT & Nhắn tin
        "com.zing.zalo", "com.facebook.orca", "com.facebook.katana", "com.facebook.lite",
        "org.telegram.messenger", "org.thunderdog.challegram", "com.whatsapp",
        "com.google.android.gm", "com.microsoft.office.outlook", "com.discord",
        "com.viber.voip", "com.skype.raider", "com.slack", "com.tencent.mm",
        "jp.naver.line.android", "com.google.android.apps.messaging",
        // MXH & Widget
        "com.instagram.android", "com.instagram.barcelona", "com.locket.Locket",
        "com.ss.android.ugc.trill", "com.zhiliaoapp.musically", "com.twitter.android",
        // Mua sắm & Gọi xe / Giao đồ
        "com.shopee.vn", "com.lazada.android", "xyz.be.customer",
        "com.grabtaxi.passenger", "com.deliverynow",
        // Ngân hàng & Ví điện tử
        "com.mservice.momotransfer", "vn.com.vng.zalopay", "com.VCB",
        "com.vnpay.bidv", "vn.com.techcombank.bb.app", "com.techcombank.mobile",
        "com.mbmobile", "com.tpb.mb.gprsandroid", "com.vietinbank.ipay",
        // AI / Tiện ích
        "com.openai.chatgpt", "com.deepseek.chat"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        b = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        optimizedPkgs.addAll(prefs.getStringSet(KEY_OPTIMIZED_APPS, emptySet()).orEmpty())

        adapter = AppAdapter(
            items = displayedList,
            onOpenSettings = { app ->
                MiuiHelper.openBatterySaverSettings(this, app.packageName)
            },
            onSelectionChanged = { updateSelectionUi() }
        )
        b.rvApps.layoutManager = LinearLayoutManager(this)
        b.rvApps.adapter = adapter

        b.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        b.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        b.btnSelectAll.setOnClickListener {
            if (displayedList.isNotEmpty()) {
                displayedList.forEach { it.isSelected = true }
                adapter.notifyItemRangeChanged(0, displayedList.size)
            }
            updateSelectionUi()
        }

        b.btnDeselectAll.setOnClickListener {
            if (displayedList.isNotEmpty()) {
                displayedList.forEach { it.isSelected = false }
                adapter.notifyItemRangeChanged(0, displayedList.size)
            }
            updateSelectionUi()
        }

        b.btnApplyBatch.setOnClickListener { applyBatchFix() }

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }

        updateSelectionUi()
        loadApps()
    }

    private fun filterApps(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())
        val oldSize = displayedList.size
        if (oldSize > 0) {
            adapter.notifyItemRangeRemoved(0, oldSize)
        }
        displayedList.clear()
        displayedList.addAll(
            if (q.isBlank()) {
                appList
            } else {
                appList.filter {
                    it.appName.lowercase(Locale.getDefault()).contains(q) ||
                        it.packageName.lowercase(Locale.getDefault()).contains(q)
                }
            }
        )
        if (displayedList.isNotEmpty()) {
            adapter.notifyItemRangeInserted(0, displayedList.size)
        }
        b.rvApps.scrollToPosition(0)
        updateEmptyState()
    }

    private fun loadApps() {
        b.progressBar.isVisible = true
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { buildAppList() }

            appList.clear()
            appList.addAll(list)
            loaded = true

            filterApps(b.etSearch.text.toString())
            b.progressBar.isVisible = false
            updateSelectionUi()
            updateEmptyState()
        }
    }

    private fun buildAppList(): List<AppInfo> {
        val pm = packageManager
        val myPkg = packageName
        val list = mutableListOf<AppInfo>()
        val seenPkgs = mutableSetOf<String>()

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        for (resolveInfo in resolveInfos) {
            val pkgName = resolveInfo.activityInfo.packageName
            if (pkgName == myPkg || seenPkgs.contains(pkgName)) continue
            seenPkgs.add(pkgName)

            val label = resolveInfo.loadLabel(pm).toString()
            val icon = try {
                resolveInfo.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            val isTarget = commonPkgs.contains(pkgName)
            list.add(
                AppInfo(
                    appName = label,
                    packageName = pkgName,
                    icon = icon,
                    isSelected = isTarget,
                    isOptimized = optimizedPkgs.contains(pkgName)
                )
            )
        }

        // Fallback for applications that are not exposed as launcher apps.
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
                    val icon = try {
                        pm.getApplicationIcon(app)
                    } catch (_: Exception) {
                        null
                    }
                    list.add(
                        AppInfo(
                            appName = label,
                            packageName = pkgName,
                            icon = icon,
                            isSelected = isTarget,
                            isOptimized = optimizedPkgs.contains(pkgName)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        list.sortWith(
            compareByDescending<AppInfo> { commonPkgs.contains(it.packageName) }
                .thenBy { it.appName.lowercase(Locale.getDefault()) }
        )
        return list
    }

    private fun applyBatchFix() {
        if (isApplying) return

        val selected = appList.filter { it.isSelected }
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.msg_select_one_app, Toast.LENGTH_SHORT).show()
            return
        }
        if (!ShizukuHelper.hasPermission()) {
            showShizukuDialog()
            return
        }

        isApplying = true
        b.progressBar.isVisible = true
        updateSelectionUi()

        lifecycleScope.launch {
            try {
                val packages = selected.map { it.packageName }
                val count = ShizukuHelper.optimizeApps(packages)
                if (count > 0) {
                    ShizukuHelper.optimizeSelfAndSystem(applicationContext)
                    optimizedPkgs.addAll(packages)
                    prefs.edit().putStringSet(KEY_OPTIMIZED_APPS, optimizedPkgs.toSet()).apply()
                    selected.forEach { it.isOptimized = true }
                    if (displayedList.isNotEmpty()) {
                        adapter.notifyItemRangeChanged(0, displayedList.size)
                    }
                    Toast.makeText(
                        this@AppListActivity,
                        getString(R.string.msg_batch_done, count),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@AppListActivity,
                        R.string.msg_batch_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@AppListActivity,
                    getString(R.string.msg_batch_failed) + " (${e.message})",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isApplying = false
                b.progressBar.isVisible = false
                updateSelectionUi()
            }
        }
    }

    private fun showShizukuDialog() {
        val available = ShizukuHelper.isAvailable()
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_shizuku_title)
            .setMessage(
                if (available) R.string.dialog_shizuku_batch_message
                else R.string.dialog_shizuku_unavailable_message
            )
            .setPositiveButton(
                if (available) R.string.action_grant else R.string.action_open_shizuku
            ) { _, _ ->
                if (available) {
                    ShizukuHelper.requestPermission(SHIZUKU_REQUEST_CODE)
                } else {
                    MiuiHelper.openShizukuApp(this)
                }
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun updateSelectionUi() {
        val count = appList.count { it.isSelected }
        b.tvSelectionSummary.text = if (count == 0) {
            getString(R.string.selection_none)
        } else {
            getString(R.string.selection_count, count)
        }
        b.btnApplyBatch.text = if (count == 0) {
            getString(R.string.apply_none)
        } else {
            getString(R.string.apply_count, count)
        }
        b.btnApplyBatch.isEnabled = count > 0 && !isApplying
    }

    private fun updateEmptyState() {
        b.tvEmpty.isVisible = loaded && displayedList.isEmpty()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(b.etSearch.windowToken, 0)
        b.etSearch.clearFocus()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    inner class AppAdapter(
        private val items: List<AppInfo>,
        private val onOpenSettings: (AppInfo) -> Unit,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        inner class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val context = holder.b.root.context

            holder.b.tvAppName.text = item.appName
            holder.b.tvPackage.text = item.packageName
            holder.b.ivIcon.setImageDrawable(
                item.icon ?: context.packageManager.defaultActivityIcon
            )
            holder.b.tvStatus.text = context.getString(
                if (item.isOptimized) R.string.app_optimized else R.string.app_not_optimized
            )
            holder.b.tvStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.isOptimized) R.color.status_good else R.color.text_tertiary
                )
            )
            holder.b.root.setBackgroundResource(
                if (item.isSelected) R.drawable.bg_item_selected else R.drawable.bg_item
            )

            holder.b.cbSelect.contentDescription = item.appName
            holder.b.btnAppSettings.contentDescription =
                context.getString(R.string.app_settings_desc, item.appName)

            holder.b.cbSelect.setOnCheckedChangeListener(null)
            holder.b.cbSelect.isChecked = item.isSelected
            holder.b.cbSelect.setOnCheckedChangeListener { _, checked ->
                if (item.isSelected != checked) {
                    item.isSelected = checked
                    holder.b.root.setBackgroundResource(
                        if (checked) R.drawable.bg_item_selected else R.drawable.bg_item
                    )
                    onSelectionChanged()
                }
            }

            holder.b.root.setOnClickListener {
                holder.b.cbSelect.isChecked = !holder.b.cbSelect.isChecked
            }
            holder.b.btnAppSettings.setOnClickListener { onOpenSettings(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1002
        private const val KEY_OPTIMIZED_APPS = "optimized_apps"
    }
}
