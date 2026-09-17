package com.v2ray.ang.util

import android.content.Context
import android.content.pm.PackageManager
import com.v2ray.ang.AppConfig
import java.util.concurrent.ConcurrentHashMap

object PackageUidResolver {

    // In-process cache to avoid resolving the same package UID repeatedly.
    private val packageUidCache = ConcurrentHashMap<String, String>()

    fun packageNamesToUids(context: Context, packageNames: List<String>): List<String> {
        return packageNames.mapNotNull { pkg ->
            packageUidCache[pkg] ?: resolveUid(context, pkg)?.also { uid ->
                packageUidCache[pkg] = uid
            }
        }
    }

    private fun resolveUid(context: Context, packageName: String): String? {
        // Special token for connections whose UID cannot be resolved (mapped to -1)
        if (packageName == AppConfig.UNIDENTIFIED_PACKAGE) {
            val uid = "-1"
            LogUtil.d(AppConfig.TAG, "Special package: $packageName -> UID: $uid")
            return uid
        }

        return try {
            val uid = context.packageManager.getPackageUid(packageName, 0).toString()
            LogUtil.d(AppConfig.TAG, "Package: $packageName -> UID: $uid")
            uid
        } catch (_: PackageManager.NameNotFoundException) {
            LogUtil.w(AppConfig.TAG, "Package not found: $packageName")
            null
        }
    }
}
