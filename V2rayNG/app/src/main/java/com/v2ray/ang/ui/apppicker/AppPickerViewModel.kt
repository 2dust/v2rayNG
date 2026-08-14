package com.v2ray.ang.ui.apppicker

import android.app.Application
import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.AppSelection
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
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

    private var allApps: List<AppInfo>? = null
    private var currentQuery: String = ""
    private var selectedSnapshot: Set<String> = emptySet()
    private var initialized = false
    private var isAppListLoading = false

    fun initialize(initialSelected: Collection<String>) {
        if (initialized) return
        initialized = true
        _selectedPackages.value = initialSelected.toSet()
    }

    fun loadApps(context: Context) {
        allApps?.let { apps ->
            val localizedUnknownApp = getString(R.string.app_picker_unknown_app)
            val updatedApps = apps.map { appInfo ->
                if (appInfo.packageName == AppConfig.UNIDENTIFIED_PACKAGE) {
                    appInfo.copy(appName = localizedUnknownApp)
                } else {
                    appInfo
                }
            }
            allApps = sortApps(updatedApps)
            _displayedApps.value = applyFilter(currentQuery)
            return
        }
        if (isAppListLoading) return

        val applicationContext = context.applicationContext
        val localizedUnknownApp = getString(R.string.app_picker_unknown_app)
        isAppListLoading = true
        launchLoading {
            try {
                selectedSnapshot = _selectedPackages.value
                val apps = withContext(Dispatchers.IO) {
                    val list = AppManagerUtil.loadNetworkAppList(applicationContext)
                    val special = createSpecialItemUnidentified(localizedUnknownApp)
                    sortApps(list + special)
                }
                allApps = apps
                _displayedApps.value = applyFilter(currentQuery)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e("AppPickerViewModel", "Failed to load app list", e)
                toastError(R.string.toast_failure)
            } finally {
                isAppListLoading = false
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
        val displayedApps = _displayedApps.value
        val currentSelection = _selectedPackages.value
        _selectedPackages.value = buildSet(currentSelection.size + displayedApps.size) {
            addAll(currentSelection)
            displayedApps.forEach { add(it.packageName) }
        }
    }

    fun invertSelection() {
        val packageNames = _displayedApps.value.map { it.packageName }
        _selectedPackages.value = AppSelection.invert(_selectedPackages.value, packageNames)
    }

    fun getSelectedPackages(): List<String> = _selectedPackages.value.sorted()

    private fun applyFilter(query: String): List<AppInfo> {
        val apps = allApps ?: return emptyList()
        if (query.isBlank()) return apps

        return apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
        }
    }

    private fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance()
        val snapshot = selectedSnapshot
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

    private fun createSpecialItemUnidentified(appName: String): AppInfo {
        return AppInfo(
            appName = appName,
            packageName = AppConfig.UNIDENTIFIED_PACKAGE,
            isSystemApp = false,
            isSelected = 0
        )
    }
}
