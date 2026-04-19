package com.v2ray.ang.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityAppPickerBinding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class AppPickerActivity : BaseActivity() {
    companion object {
        private const val EXTRA_SELECTED_PACKAGES = "selected_packages"
        private const val EXTRA_PICKER_TITLE = "picker_title"

        fun createIntent(
            context: Context,
            selectedPackages: Collection<String> = emptyList(),
            title: String? = null
        ): Intent = Intent(context, AppPickerActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(selectedPackages))
            title?.let { putExtra(EXTRA_PICKER_TITLE, it) }
        }

        fun getSelectedPackages(intent: Intent?): List<String> {
            return intent?.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        }
    }

    private val binding by lazy { ActivityAppPickerBinding.inflate(layoutInflater) }
    private val initialSelectedPackages by lazy {
        intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
    }
    private val selectedPackages = LinkedHashSet<String>()
    private var appsAll: List<AppInfo> = emptyList()
    private val adapter = AppSelectorAdapter(selectedPackages)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = resolveScreenTitle())

        selectedPackages.addAll(initialSelectedPackages)
        setupRecyclerView()
        loadApps()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_app_picker, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    filterApps(newText.orEmpty())
                    return false
                }
            })
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.select_all -> {
            selectAllVisible()
            true
        }

        R.id.invert_selection -> {
            invertVisibleSelection()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, getSelectedPackages())
            }
        )
        super.finish()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.adapter = adapter
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
    }

    private fun createSpecialItemUnidentified(): AppInfo {
        val icon = getDrawable(android.R.drawable.ic_menu_manage) ?: getDrawable(android.R.drawable.sym_def_app_icon)!!
        return AppInfo(
            appName = "Catch missing process UID (-1)",
            packageName = "__unidentified__",
            appIcon = icon,
            isSystemApp = false,
            isSelected = 0
        )
    }

    private fun loadApps() {
        showLoading()

        lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@AppPickerActivity)
                    val sortedApps = sortApps(appsList)
                    listOf(createSpecialItemUnidentified()) + sortedApps
                }

                appsAll = apps
                updateDisplayedApps(apps)
            } catch (e: Exception) {
                LogUtil.e("AppPickerActivity", "Failed to load app list", e)
            } finally {
                hideLoading()
            }
        }
    }

    private fun filterApps(content: String) {
        val key = content.uppercase()
        val filteredApps = appsAll.filter { app ->
            key.isEmpty() || matchesSearch(app, key)
        }
        updateDisplayedApps(filteredApps)
    }

    private fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance()
        return apps.sortedWith { p1, p2 ->
            val p1Selected = selectedPackages.contains(p1.packageName)
            val p2Selected = selectedPackages.contains(p2.packageName)
            when {
                p1Selected && !p2Selected -> -1
                !p1Selected && p2Selected -> 1
                p1.isSystemApp && !p2.isSystemApp -> 1
                !p1.isSystemApp && p2.isSystemApp -> -1
                else -> collator.compare(p1.appName, p2.appName)
            }
        }
    }

    private fun matchesSearch(app: AppInfo, keyword: String): Boolean {
        return app.appName.uppercase().contains(keyword) || app.packageName.uppercase().contains(keyword)
    }

    private fun updateDisplayedApps(apps: List<AppInfo>) {
        adapter.submitList(apps)
    }

    private fun selectAllVisible() {
        adapter.apps.forEach { app -> selectedPackages.add(app.packageName) }
        adapter.refreshSelection()
    }

    private fun invertVisibleSelection() {
        adapter.apps.forEach { app ->
            if (selectedPackages.contains(app.packageName)) {
                selectedPackages.remove(app.packageName)
            } else {
                selectedPackages.add(app.packageName)
            }
        }
        adapter.refreshSelection()
    }

    private fun getSelectedPackages(): ArrayList<String> {
        return ArrayList(selectedPackages.sorted())
    }

    private fun resolveScreenTitle(): String {
        return intent.getStringExtra(EXTRA_PICKER_TITLE) ?: getString(R.string.per_app_proxy_settings)
    }
}

