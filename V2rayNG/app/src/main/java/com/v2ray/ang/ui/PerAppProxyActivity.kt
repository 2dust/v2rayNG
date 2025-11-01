package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityBypassListBinding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.extension.v2RayApplication
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.Utils
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class PerAppProxyActivity : BaseActivity() {
    private val binding by lazy { ActivityBypassListBinding.inflate(layoutInflater) }

    private var adapter: PerAppProxyAdapter? = null
    private var appsAll: List<AppInfo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.per_app_proxy_settings)

        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)

        initList()

        binding.switchPerAppProxy.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, isChecked)
        }
        binding.switchPerAppProxy.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)

        binding.switchBypassApps.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, isChecked)
        }
        binding.switchBypassApps.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)

        binding.layoutSwitchBypassAppsTips.setOnClickListener {
            Toasty.info(this, R.string.summary_pref_per_app_proxy, Toast.LENGTH_LONG, true).show()
        }
    }

    private fun initList() {
        binding.pbWaiting.show()

        lifecycleScope.launch {
            try {
                val blacklist =
                    MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@PerAppProxyActivity)

                    if (blacklist != null) {
                        appsList.forEach { app ->
                            app.isSelected = if (blacklist.contains(app.packageName)) 1 else 0
                        }
                        appsList.sortedWith { p1, p2 ->
                            when {
                                p1.isSelected > p2.isSelected -> -1
                                p1.isSelected < p2.isSelected -> 1
                                p1.isSystemApp > p2.isSystemApp -> 1
                                p1.isSystemApp < p2.isSystemApp -> -1
                                p1.appName.lowercase() > p2.appName.lowercase() -> 1
                                p1.appName.lowercase() < p2.appName.lowercase() -> -1
                                p1.packageName > p2.packageName -> 1
                                p1.packageName < p2.packageName -> -1
                                else -> 0
                            }
                        }
                    } else {
                        val collator = Collator.getInstance()
                        appsList.sortedWith(compareBy(collator) { it.appName })
                    }
                }

                appsAll = apps
                adapter = PerAppProxyAdapter(this@PerAppProxyActivity, apps, blacklist)
                binding.recyclerView.adapter = adapter
            } catch (e: Exception) {
                Log.e(ANG_PACKAGE, "Error loading apps", e)
            } finally {
                binding.pbWaiting.hide()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        adapter?.let {
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, it.blacklist)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bypass_list, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    filterProxyApp(newText.orEmpty())
                    return false
                }
            })
        }

        return super.onCreateOptionsMenu(menu)
    }


    @SuppressLint("NotifyDataSetChanged")
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.select_all -> {
            selectAllApp()
            allowPerAppProxy()
            true
        }

        R.id.select_proxy_app -> {
            selectProxyAppAuto()
            allowPerAppProxy()
            true
        }

        R.id.import_proxy_app -> {
            importProxyApp()
            allowPerAppProxy()
            true
        }

        R.id.export_proxy_app -> {
            exportProxyApp()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun selectAllApp() {
        adapter?.let { it ->
            val pkgNames = it.apps.map { it.packageName }
            if (it.blacklist.containsAll(pkgNames)) {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist?.remove(packageName)
                }
            } else {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist?.add(packageName)
                }
            }
            it.notifyDataSetChanged()
            true
        }
    }

    private fun selectProxyAppAuto() {
        toast(R.string.msg_downloading_content)
        binding.pbWaiting.show()

        val url = AppConfig.ANDROID_PACKAGE_NAME_LIST_URL
        lifecycleScope.launch(Dispatchers.IO) {
            var content = HttpUtil.getUrlContent(url, 5000)
            if (content.isNullOrEmpty()) {
                val httpPort = SettingsManager.getHttpPort()
                content = HttpUtil.getUrlContent(url, 5000, httpPort) ?: ""
            }
            launch(Dispatchers.Main) {
                Log.i(AppConfig.TAG, content)
                selectProxyApp(content, true)
                toastSuccess(R.string.toast_success)
                binding.pbWaiting.hide()
            }
        }
    }

    private fun importProxyApp() {
        val content = Utils.getClipboard(applicationContext)
        if (TextUtils.isEmpty(content)) return
        selectProxyApp(content, false)
        toastSuccess(R.string.toast_success)
    }

    private fun exportProxyApp() {
        var lst = binding.switchBypassApps.isChecked.toString()

        adapter?.blacklist?.forEach block@{
            lst = lst + System.getProperty("line.separator") + it
        }
        Utils.setClipboard(applicationContext, lst)
        toastSuccess(R.string.toast_success)
    }

    private fun allowPerAppProxy() {
        binding.switchPerAppProxy.isChecked = true
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun selectProxyApp(content: String, force: Boolean): Boolean {
        try {
            val proxyApps = if (TextUtils.isEmpty(content)) {
                Utils.readTextFromAssets(v2RayApplication, "proxy_packagename.txt")
            } else {
                content
            }
            if (TextUtils.isEmpty(proxyApps)) return false

            adapter?.blacklist?.clear()

            if (binding.switchBypassApps.isChecked) {
                adapter?.let { it ->
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.i(AppConfig.TAG, packageName)
                        if (!inProxyApps(proxyApps, packageName, force)) {
                            adapter?.blacklist?.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            } else {
                adapter?.let { it ->
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.i(AppConfig.TAG, packageName)
                        if (inProxyApps(proxyApps, packageName, force)) {
                            adapter?.blacklist?.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Error selecting proxy app", e)
            return false
        }
        return true
    }

    private fun inProxyApps(proxyApps: String, packageName: String, force: Boolean): Boolean {
        if (force) {
            if (packageName == "com.google.android.webview") return false
            if (packageName.startsWith("com.google")) return true
        }

        return proxyApps.indexOf(packageName) >= 0
    }

    private fun filterProxyApp(content: String): Boolean {
        val apps = ArrayList<AppInfo>()

        val key = content.uppercase()
        if (key.isNotEmpty()) {
            appsAll?.forEach {
                if (it.appName.uppercase().indexOf(key) >= 0
                    || it.packageName.uppercase().indexOf(key) >= 0
                ) {
                    apps.add(it)
                }
            }
        } else {
            appsAll?.forEach {
                apps.add(it)
            }
        }

        adapter = PerAppProxyAdapter(this, apps, adapter?.blacklist)
        binding.recyclerView.adapter = adapter
        refreshData()
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        adapter?.notifyDataSetChanged()
    }
}
