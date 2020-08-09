package com.v2ray.ang.util

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.v2ray.ang.dto.AppInfo
import rx.Observable
import java.util.*

object AppManagerUtil {
    fun loadNetworkAppList(ctx: Context): ArrayList<AppInfo> {
        val packageManager = ctx.packageManager
        val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val apps = ArrayList<AppInfo>()

        for (pkg in packages) {
            if (!pkg.hasInternetPermission && pkg.packageName != "android") continue

            val applicationInfo = pkg.applicationInfo

            val appName = applicationInfo.loadLabel(packageManager).toString()
            val appIcon = applicationInfo.loadIcon(packageManager)
            val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) > 0

            val appInfo = AppInfo(appName, pkg.packageName, appIcon, isSystemApp, 0)
            apps.add(appInfo)
        }

        return apps
    }

    fun rxLoadNetworkAppList(ctx: Context): Observable<ArrayList<AppInfo>> = Observable.create {
        it.onNext(loadNetworkAppList(ctx))
    }

    val PackageInfo.hasInternetPermission: Boolean
        get() {
            val permissions = requestedPermissions
            return permissions?.any { it == Manifest.permission.INTERNET } ?: false
        }
}
