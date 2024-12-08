/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 * Copyright (C) 2021 by Max Lv <max.c.lv@gmail.com>                          *
 * Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package com.v2ray.ang.plugin

import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import com.v2ray.ang.AngApplication
import com.v2ray.ang.plugin.PluginManager.loadString

abstract class ResolvedPlugin(protected val resolveInfo: ResolveInfo) : Plugin() {
    protected abstract val componentInfo: ComponentInfo

    override val id by lazy { componentInfo.loadString(PluginContract.METADATA_KEY_ID)!! }
    override val version by lazy {
        getPackageInfo(componentInfo.packageName).versionCode
    }
    override val versionName: String by lazy {
        getPackageInfo(componentInfo.packageName).versionName!!
    }
    override val label: CharSequence get() = resolveInfo.loadLabel(AngApplication.application.packageManager)
    override val icon: Drawable get() = resolveInfo.loadIcon(AngApplication.application.packageManager)
    override val packageName: String get() = componentInfo.packageName
    override val directBootAware get() = Build.VERSION.SDK_INT < 24 || componentInfo.directBootAware

    fun getPackageInfo(packageName: String) = AngApplication.application.packageManager.getPackageInfo(
        packageName, if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    )!!
}
