package com.v2ray.ang.ui

/**
 * Pure selection operations shared by application-list screens.
 *
 * Keeping the calculations separate lets bulk actions build one result before it is published.
 */
internal object AppSelection {

    fun invert(currentSelection: Set<String>, packageNames: Collection<String>): Set<String> {
        return currentSelection.toMutableSet().apply {
            packageNames.forEach { packageName ->
                if (!add(packageName)) {
                    remove(packageName)
                }
            }
        }
    }

    /**
     * Builds the selected package set while preserving the legacy list matching behavior.
     *
     * The full list text is searched for each installed package name. This is intentionally not
     * exact line matching; changing that behavior should be handled separately.
     */
    fun fromProxyList(
        packageNames: Collection<String>,
        proxyAppList: String,
        bypassApps: Boolean,
        forceGoogleApps: Boolean
    ): Set<String> {
        return buildSet(packageNames.size) {
            packageNames.forEach { packageName ->
                val shouldProxy = shouldProxy(packageName, proxyAppList, forceGoogleApps)
                val shouldSelect = if (bypassApps) !shouldProxy else shouldProxy
                if (shouldSelect) {
                    add(packageName)
                }
            }
        }
    }

    private fun shouldProxy(packageName: String, proxyAppList: String, forceGoogleApps: Boolean): Boolean {
        if (forceGoogleApps) {
            if (packageName == "com.google.android.webview") return false
            if (packageName.startsWith("com.google")) return true
        }
        return proxyAppList.contains(packageName)
    }
}
