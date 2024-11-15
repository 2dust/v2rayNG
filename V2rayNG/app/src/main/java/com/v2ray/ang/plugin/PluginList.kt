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

import android.content.Intent
import android.content.pm.PackageManager
import com.v2ray.ang.AngApplication

class PluginList : ArrayList<Plugin>() {
    init {
        addAll(
            AngApplication.application.packageManager.queryIntentContentProviders(
                Intent(PluginContract.ACTION_NATIVE_PLUGIN), PackageManager.GET_META_DATA
            )
                .filter { it.providerInfo.exported }.map { NativePlugin(it) })
    }

    val lookup = mutableMapOf<String, Plugin>().apply {
        for (plugin in this@PluginList.toList()) {
            fun check(old: Plugin?) {
                if (old != null && old != plugin) {
                    this@PluginList.remove(old)
                }
                /* if (old != null && old !== plugin) {
                     val packages = this@PluginList.filter { it.id == plugin.id }
                         .joinToString { it.packageName }
                     val message = "Conflicting plugins found from: $packages"
                     Toast.makeText(SagerNet.application, message, Toast.LENGTH_LONG).show()
                     throw IllegalStateException(message)
                 }*/
            }
            check(put(plugin.id, plugin))
        }
    }
}
