package com.v2ray.ang.repository

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

data class PerAppProxyPreferences(
    val selected: Set<String>,
    val perAppProxyEnabled: Boolean,
    val bypassMode: Boolean
)

open class PerAppProxyRepository(
    private val app: Application,
    private val appList: AppListRepository = AppListRepository(app)
) : BaseRepository() {

    // ---------- Installed applications ----------

    open suspend fun loadApps(selectedSnapshot: Set<String>): List<AppInfo> =
        appList.loadApps(selectedSnapshot = selectedSnapshot, includeUnidentified = false)

    open fun filter(apps: List<AppInfo>, query: String): List<AppInfo> = appList.filter(apps, query)

    // ---------- Selection arithmetic (pure, no I/O) ----------

    /**
     * "Select all" of this screen is a toggle, unlike the picker's additive one: when every
     * visible row is already checked it clears them instead, which is the behaviour the menu item
     * has always had here.
     *
     * @param visible only the rows currently passing the filter, so a search narrows the scope
     */
    open fun toggleAll(current: Set<String>, visible: Collection<String>): Set<String> {
        if (visible.isEmpty()) return current
        val allChecked = visible.all { it in current }
        return if (allChecked) current - visible.toSet() else appList.selectAll(current, visible)
    }

    /** Flips each visible package; entries hidden by the filter are preserved. */
    open fun invert(current: Set<String>, visible: Collection<String>): Set<String> =
        appList.invert(current, visible)

    /**
     * Derives the checked set from a proxy-package list.
     *
     * Runs off the main thread because it is a substring scan of the whole list text for every
     * installed package.
     *
     * @param bypassApps when in bypass mode the meaning inverts: check what must NOT be proxied
     * @param forceGoogleApps treat `com.google.*` as proxied, except the system WebView
     */
    open suspend fun resolveProxyList(
        packageNames: Collection<String>,
        proxyAppList: String,
        bypassApps: Boolean,
        forceGoogleApps: Boolean
    ): Set<String> = withIO {
        appList.fromProxyList(packageNames, proxyAppList, bypassApps, forceGoogleApps)
    }

    // ---------- Persisted state ----------

    open fun loadPreferences() = PerAppProxyPreferences(
        selected = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
            ?.toSet()
            .orEmpty(),
        perAppProxyEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false),
        bypassMode = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)
    )

    open suspend fun saveSelection(selection: Set<String>) {
        withIO { MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, selection.toMutableSet()) }
    }

    open suspend fun setPerAppProxyEnabled(enabled: Boolean) {
        withIO { MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, enabled) }
    }

    open suspend fun setBypassMode(enabled: Boolean) {
        withIO { MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, enabled) }
    }

    /**
     * Raises the global restart flag for consumers outside the result chain (tile, boot receiver,
     * running service).
     */
    open suspend fun notifyRestartService() {
        withIO { SettingsChangeManager.makeRestartService() }
    }

    // ---------- Recommended list ----------

    /**
     * Fetches the community package list.
     * @return the list text, or an empty string when every source failed
     */
    open suspend fun fetchRecommendedList(): String = withIO {
        val url = AppConfig.ANDROID_PACKAGE_NAME_LIST_URL

        HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = TIMEOUT_MS))
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withIO it }

        HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = TIMEOUT_MS,
                httpPort = SettingsManager.getHttpPort(),
                proxyUsername = SettingsManager.getSocksUsername(),
                proxyPassword = SettingsManager.getSocksPassword()
            )
        )?.takeIf { it.isNotBlank() }
            ?.let { return@withIO it }

        runCatching { Utils.readTextFromAssets(app, BUNDLED_PROXY_LIST) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to read bundled proxy list", it) }
            .getOrNull()
            .orEmpty()
    }

    // ---------- Clipboard ----------

    /** Reading the clipboard is a Binder round trip, so it never runs on the main thread. */
    open suspend fun readClipboard(): String = withIO {
        runCatching { Utils.getClipboard(app) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to read clipboard", it) }
            .getOrDefault("")
    }

    /**
     * Serialises the selection - the current mode on the first line, then one package per line,
     * byte-compatible with the exports produced before the migration - and writes it out.
     *
     * @return `false` when the clipboard rejected the write, so the caller can report a failure
     *   instead of a silent success
     */
    open suspend fun exportSelection(bypassMode: Boolean, selection: Set<String>): Boolean = withIO {
        val payload = buildString {
            append(bypassMode)
            selection.forEach {
                append(System.lineSeparator())
                append(it)
            }
        }
        runCatching { Utils.setClipboard(app, payload) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to write clipboard", it) }
            .isSuccess
    }

    private companion object {
        const val TIMEOUT_MS = 10000
        const val BUNDLED_PROXY_LIST = "proxy_package_name"
    }
}
