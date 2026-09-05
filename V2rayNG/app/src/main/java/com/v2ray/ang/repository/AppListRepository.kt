package com.v2ray.ang.repository

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AppInfo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.text.Collator
import java.util.Locale

/**
 * Data layer for every screen that lists installed applications (app picker, per-app proxy).
 */
open class AppListRepository(private val app: Application) : BaseRepository() {

    // ---------- Loading ----------

    /**
     * Loads all network-capable applications, already ordered for display.
     *
     * Order: the "unidentified" pseudo entry first, then apps present in [selectedSnapshot], then
     * user apps, then system apps, each group collated by localized label. The snapshot is taken
     * once at load time on purpose, so rows do not jump around while the user is ticking them.
     *
     * @param selectedSnapshot packages considered checked when computing the order
     * @param includeUnidentified prepend the entry standing for traffic with no owning package
     */
    open suspend fun loadApps(
        selectedSnapshot: Set<String> = emptySet(),
        includeUnidentified: Boolean = true
    ): List<AppInfo> = withIO {
        val sorted = sortApps(queryNetworkApps(), selectedSnapshot)
        if (!includeUnidentified) {
            sorted
        } else {
            buildList(sorted.size + 1) {
                add(unidentifiedApp())
                addAll(sorted)
            }
        }
    }

    /**
     * Queries PackageManager for every package that declares `INTERNET`.
     *
     * Already inside the caller's [withIO], so it does not switch dispatcher again. The loop
     * checks for cancellation because [ApplicationInfo.loadLabel] hits the resource loader once
     * per package, which on a device with several hundred apps takes long enough that a user
     * leaving the screen must not have to wait for it.
     */
    private suspend fun queryNetworkApps(): List<AppInfo> {
        val packageManager = app.packageManager
        val packages = installedPackages(packageManager)
        val apps = ArrayList<AppInfo>(packages.size)

        for (pkg in packages) {
            currentCoroutineContext().ensureActive()
            val applicationInfo = pkg.applicationInfo ?: continue
            if (!pkg.declaresInternet()) continue
            apps.add(
                AppInfo(
                    appName = applicationInfo.loadLabel(packageManager).toString(),
                    packageName = pkg.packageName,
                    isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM > 0,
                    isSelected = 0
                )
            )
        }
        return apps
    }

    private fun installedPackages(packageManager: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

    /**
     * An app with no `INTERNET` permission can never produce traffic, so listing it would only
     * dilute the picker. This is why the query pays for [PackageManager.GET_PERMISSIONS].
     */
    private fun PackageInfo.declaresInternet(): Boolean =
        requestedPermissions?.any { it == Manifest.permission.INTERNET } == true

    /**
     * The pseudo entry for traffic that cannot be attributed to a package.
     *
     * Its label is intentionally empty: the UI resolves `R.string.app_picker_unknown_app` itself,
     * so the text follows a per-app locale change without reloading the list.
     */
    private fun unidentifiedApp() = AppInfo(
        appName = "",
        packageName = AppConfig.UNIDENTIFIED_PACKAGE,
        isSystemApp = false,
        isSelected = 0
    )

    private fun sortApps(apps: List<AppInfo>, selected: Set<String>): List<AppInfo> {
        val collator = Collator.getInstance(Locale.getDefault())
        return apps.sortedWith { a, b ->
            val aSelected = a.packageName in selected
            val bSelected = b.packageName in selected
            when {
                aSelected != bSelected -> if (aSelected) -1 else 1
                a.isSystemApp != b.isSystemApp -> if (a.isSystemApp) 1 else -1
                else -> collator.compare(a.appName, b.appName)
            }
        }
    }

    // ---------- Filtering ----------

    /** Case-insensitive match on label or package name; a blank query keeps the list untouched. */
    open fun filter(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) return apps
        return apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    // ---------- Selection arithmetic (pure, no I/O) ----------

    /** Adds every package of [packageNames] to [current]; already-checked entries stay checked. */
    open fun selectAll(current: Set<String>, packageNames: Collection<String>): Set<String> =
        buildSet(current.size + packageNames.size) {
            addAll(current)
            addAll(packageNames)
        }

    /** Flips each package of [packageNames] inside [current]; entries outside it are preserved. */
    open fun invert(current: Set<String>, packageNames: Collection<String>): Set<String> =
        current.toMutableSet().apply {
            packageNames.forEach { if (!add(it)) remove(it) }
        }

    /**
     * Derives the checked set from an imported proxy-package list.
     *
     * The list text is searched for each installed package name; this is deliberately a substring
     * match, not exact line matching, to stay compatible with the lists users already have.
     *
     * @param bypassApps invert the meaning, i.e. check everything that must NOT be proxied
     * @param forceGoogleApps treat `com.google.*` as proxied (except the system WebView)
     */
    open fun fromProxyList(
        packageNames: Collection<String>,
        proxyAppList: String,
        bypassApps: Boolean,
        forceGoogleApps: Boolean
    ): Set<String> = buildSet(packageNames.size) {
        packageNames.forEach { packageName ->
            val proxied = shouldProxy(packageName, proxyAppList, forceGoogleApps)
            if (if (bypassApps) !proxied else proxied) add(packageName)
        }
    }

    private fun shouldProxy(
        packageName: String,
        proxyAppList: String,
        forceGoogleApps: Boolean
    ): Boolean {
        if (forceGoogleApps) {
            if (packageName == GOOGLE_WEBVIEW_PACKAGE) return false
            if (packageName.startsWith(GOOGLE_PACKAGE_PREFIX)) return true
        }
        return proxyAppList.contains(packageName)
    }

    private companion object {
        const val GOOGLE_PACKAGE_PREFIX = "com.google"
        const val GOOGLE_WEBVIEW_PACKAGE = "com.google.android.webview"
    }
}
