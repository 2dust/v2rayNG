package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * ViewModel for AppPicker screen.
 */
class AppPickerViewModel(application: Application) : BaseViewModel(application) {

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _displayedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val displayedApps: StateFlow<List<AppInfo>> = _displayedApps.asStateFlow()

    private var appsAll: List<AppInfo> = emptyList()
    private var currentQuery: String = ""
    private var SelectedSnapshot: Set<String> = emptySet()

    fun initialize(initialSelected: Collection<String>) {
        _selectedPackages.value = initialSelected.toSet()
    }

    fun loadApps(context: Context) {
        launchLoading {
            try {
                SelectedSnapshot = _selectedPackages.value.toSet()
                val apps = withContext(Dispatchers.IO) {
                    val list = AppManagerUtil.loadNetworkAppList(context)
                    val special = createSpecialItemUnidentified(context)
                    sortApps(list + special)
                }
                appsAll = apps
                _displayedApps.value = applyFilter(currentQuery)
            } catch (e: Exception) {
                LogUtil.e("AppPickerViewModel", "Failed to load app list", e)
                toastError(com.v2ray.ang.R.string.toast_failure)
            }
        }
    }

    fun filterApps(query: String) {
        currentQuery = query
        _displayedApps.value = applyFilter(query)
    }

    fun toggleApp(packageName: String) {
        val current = _selectedPackages.value
        _selectedPackages.value = if (current.contains(packageName)) {
            current - packageName
        } else {
            current + packageName
        }
    }

    fun selectAll() {
        val displayedPackages = _displayedApps.value.map { it.packageName }.toSet()
        _selectedPackages.value = _selectedPackages.value + displayedPackages
    }

    fun invertSelection() {
        val current = _selectedPackages.value
        val displayedPackages = _displayedApps.value.map { it.packageName }.toSet()
        _selectedPackages.value = (current - displayedPackages) +
                (displayedPackages - current)
    }

    fun getSelectedPackages(): List<String> = _selectedPackages.value.sorted()

    private fun applyFilter(query: String): List<AppInfo> {
        if (query.isBlank()) return appsAll
        val key = query.uppercase()
        return appsAll.filter {
            it.appName.uppercase().contains(key) || it.packageName.uppercase().contains(key)
        }
    }

    private fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance()
        val snapshot = SelectedSnapshot
        return apps.sortedWith { p1, p2 ->
            val p1Selected = snapshot.contains(p1.packageName)
            val p2Selected = snapshot.contains(p2.packageName)
            when {
                p1Selected && !p2Selected -> -1
                !p1Selected && p2Selected -> 1
                p1.isSystemApp && !p2.isSystemApp -> 1
                !p1.isSystemApp && p2.isSystemApp -> -1
                else -> collator.compare(p1.appName, p2.appName)
            }
        }
    }

    private fun createSpecialItemUnidentified(context: Context): AppInfo {
        val icon = context.getDrawable(android.R.drawable.ic_menu_help)
            ?: context.getDrawable(android.R.drawable.sym_def_app_icon)
            ?: error("No fallback drawable available")
        return AppInfo(
            appName = context.getString(com.v2ray.ang.R.string.app_picker_unknown_app),
            packageName = AppConfig.UNIDENTIFIED_PACKAGE,
            appIcon = icon,
            isSystemApp = false,
            isSelected = 0
        )
    }
}
