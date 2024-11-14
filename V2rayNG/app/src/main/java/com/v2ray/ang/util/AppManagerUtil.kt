package com.v2ray.ang.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.v2ray.ang.dto.AppInfo
import io.reactivex.rxjava3.core.Observable

object AppManagerUtil {
    private fun loadNetworkAppList(ctx: Context): ArrayList<AppInfo> {
        val packageManager = ctx.packageManager
        val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val apps = ArrayList<AppInfo>()

        for (pkg in packages) {
            val applicationInfo = pkg.applicationInfo ?: continue

            val appName = applicationInfo.loadLabel(packageManager).toString()
            val appIcon = applicationInfo.loadIcon(packageManager) ?: continue
            val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) > 0

            val appInfo = AppInfo(appName, pkg.packageName, appIcon, isSystemApp, 0)
            apps.add(appInfo)
        }

        return apps
    }

    fun rxLoadNetworkAppList(ctx: Context): Observable<ArrayList<AppInfo>> =
        Observable.unsafeCreate {
            it.onNext(loadNetworkAppList(ctx))
        }

//    val PackageInfo.hasInternetPermission: Boolean
//        get() {
//            val permissions = requestedPermissions
//            return permissions?.any { it == Manifest.permission.INTERNET } ?: false
//        }
}
