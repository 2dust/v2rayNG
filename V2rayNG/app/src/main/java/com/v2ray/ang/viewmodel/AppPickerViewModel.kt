package com.v2ray.ang.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * ViewModel for AppPicker screen.
 * Holds selected packages and displayed app list.
 */
class AppPickerViewModel : ViewModel() {

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _displayedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val displayedApps: StateFlow<List<AppInfo>> = _displayedApps.asStateFlow()

    // Cached full list
    private var appsAll: List<AppInfo> = emptyList()

    fun initialize(initialSelected: Collection<String>) {
        _selectedPackages.value = initialSelected.toSet()
    }

    fun loadApps(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val apps = withContext(Dispatchers.IO) {
                    val list = AppManagerUtil.loadNetworkAppList(context)
                    val special = createSpecialItemUnidentified(context)
                    val sorted = sortApps(list + special)
                    sorted
                }
                appsAll = apps
                _displayedApps.value = apps
            } catch (e: Exception) {
                LogUtil.e("AppPickerViewModel", "Failed to load app list", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterApps(query: String) {
        val key = query.uppercase()
        _displayedApps.value = if (key.isNotEmpty()) {
            appsAll.filter {
                it.appName.uppercase().contains(key) || it.packageName.uppercase().contains(key)
            }
        } else {
            appsAll
        }
    }

    fun toggleApp(packageName: String) {
        val current = _selectedPackages.value
        val newSet = if (current.contains(packageName)) {
            current - packageName
        } else {
            current + packageName
        }
        _selectedPackages.value = newSet
        // Re‑sort when selection changes
        sortAndUpdateDisplayed()
    }

    fun selectAll() {
        val allPackages = _displayedApps.value.map { it.packageName }.toSet()
        _selectedPackages.value = allPackages
        sortAndUpdateDisplayed()
    }

    fun invertSelection() {
        val current = _selectedPackages.value
        val allPackages = _displayedApps.value.map { it.packageName }.toSet()
        val inverted = allPackages.filter { !current.contains(it) }.toSet()
        _selectedPackages.value = inverted
        sortAndUpdateDisplayed()
    }

    fun getSelectedPackages(): List<String> = _selectedPackages.value.sorted()

    private fun sortAndUpdateDisplayed() {
        _displayedApps.value = sortApps(appsAll)
    }

    private fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance()
        return apps.sortedWith { p1, p2 ->
            val p1Selected = _selectedPackages.value.contains(p1.packageName)
            val p2Selected = _selectedPackages.value.contains(p2.packageName)
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
