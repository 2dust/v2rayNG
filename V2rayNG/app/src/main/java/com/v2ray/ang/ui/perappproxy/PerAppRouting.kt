package com.v2ray.ang.ui.perappproxy

import androidx.annotation.StringRes
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo

internal enum class PerAppRoutingMode { VPN, ROOT, PROXY_ONLY }

/** Describes configured app inclusion, not connection status or individual destination rules. */
internal class PerAppRouting(
    private val mode: PerAppRoutingMode,
    private val enabled: Boolean,
    private val bypass: Boolean,
    selectedPackages: Set<String>,
    installedApps: List<AppInfo>,
    selfPackageName: String,
) {
    private val selfUid = installedApps.firstOrNull { it.packageName == selfPackageName }?.uid
    private val selectionEmpty = selectedPackages.isEmpty()
    private val selectedUids = installedApps.mapNotNull { app ->
        // CoreVpnService removes only its own package before building an allowlist.
        val excludedSelf = mode == PerAppRoutingMode.VPN && !bypass && app.packageName == selfPackageName
        app.uid.takeIf { app.packageName in selectedPackages && !excludedSelf }
    }.toSet()

    @StringRes
    fun descriptionRes(app: AppInfo): Int? {
        if (!enabled || mode == PerAppRoutingMode.PROXY_ONLY) {
            return R.string.acc_per_app_routing_disabled
        }
        val uid = app.uid ?: return null
        val throughApp = when (mode) {
            PerAppRoutingMode.ROOT -> uid != selfUid && ((uid in selectedUids) != bypass)
            PerAppRoutingMode.VPN -> when {
                // The empty-selection fallback excludes self and includes every other UID.
                selectionEmpty -> uid != selfUid
                bypass -> uid != selfUid && uid !in selectedUids
                // No successful addAllowedApplication calls means Android includes all apps,
                // including when the saved selection contains only self or removed packages.
                selectedUids.isEmpty() -> true
                else -> uid in selectedUids
            }
            PerAppRoutingMode.PROXY_ONLY -> false // Handled before resolving the UID.
        }
        return if (throughApp) R.string.acc_app_routed_through else R.string.acc_app_routed_directly
    }
}
