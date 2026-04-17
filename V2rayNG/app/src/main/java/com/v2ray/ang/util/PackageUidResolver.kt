package com.v2ray.ang.util

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

object PackageUidResolver {

    private const val TAG = "PackageUidResolver"

    // In-process cache to avoid resolving the same package UID repeatedly.
    private val packageUidCache = ConcurrentHashMap<String, String>()

    val packageUidMap: Map<String, String>
        get() = packageUidCache

    fun packageNamesToUids(context: Context, packageNames: List<String>): List<String> {
        return packageNames.mapNotNull { pkg ->
            packageUidCache[pkg] ?: resolveUid(context, pkg)?.also { uid ->
                packageUidCache[pkg] = uid
            }
        }
    }

    fun uidsToPackageNames(uids: List<String>): List<String> {
        if (uids.isEmpty()) return emptyList()
        return packageUidCache.filterValues { it in uids }.keys.toList()
    }

    fun uidToPackageName(uid: String): String? {
        return packageUidCache.entries.firstOrNull { it.value == uid }?.key
    }

    private fun resolveUid(context: Context, packageName: String): String? {
        return try {
            val uid = context.packageManager.getPackageUid(packageName, 0).toString()
            LogUtil.d(TAG, "Package: $packageName -> UID: $uid")
            uid
        } catch (_: PackageManager.NameNotFoundException) {
            LogUtil.w(TAG, "Package not found: $packageName")
            null
        }
    }
}