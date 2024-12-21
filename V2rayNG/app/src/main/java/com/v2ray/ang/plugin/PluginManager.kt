/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-AngApplication@sekai.icu>             *
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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.system.Os
import android.widget.Toast
import androidx.core.os.bundleOf
import com.v2ray.ang.AngApplication
import com.v2ray.ang.extension.listenForPackageChanges
import com.v2ray.ang.plugin.PluginContract.METADATA_KEY_ID
import java.io.File
import java.io.FileNotFoundException

object PluginManager {

    class PluginNotFoundException(val plugin: String) : FileNotFoundException(plugin)

    private var receiver: BroadcastReceiver? = null
    private var cachedPlugins: PluginList? = null
    fun fetchPlugins() = synchronized(this) {
        if (receiver == null) receiver = AngApplication.application.listenForPackageChanges {
            synchronized(this) {
                receiver = null
                cachedPlugins = null
            }
        }
        if (cachedPlugins == null) cachedPlugins = PluginList()
        cachedPlugins!!
    }

    private fun buildUri(id: String, authority: String) = Uri.Builder()
        .scheme(PluginContract.SCHEME)
        .authority(authority)
        .path("/$id")
        .build()

    data class InitResult(
        val path: String,
    )

    @Throws(Throwable::class)
    fun init(pluginId: String): InitResult? {
        if (pluginId.isEmpty()) return null
        var throwable: Throwable? = null

        try {
            val result = initNative(pluginId)
            if (result != null) return result
        } catch (t: Throwable) {
            if (throwable == null) throwable = t  //Logs.w(t)
        }

        throw throwable ?: PluginNotFoundException(pluginId)
    }

    private fun initNative(pluginId: String): InitResult? {
        var flags = PackageManager.GET_META_DATA
        if (Build.VERSION.SDK_INT >= 24) {
            flags =
                flags or PackageManager.MATCH_DIRECT_BOOT_UNAWARE or PackageManager.MATCH_DIRECT_BOOT_AWARE
        }
        var providers = AngApplication.application.packageManager.queryIntentContentProviders(
            Intent(PluginContract.ACTION_NATIVE_PLUGIN, buildUri(pluginId, "com.github.dyhkwong.AngApplication")), flags
        )
            .filter { it.providerInfo.exported }
        if (providers.isEmpty()) {
            providers = AngApplication.application.packageManager.queryIntentContentProviders(
                Intent(PluginContract.ACTION_NATIVE_PLUGIN, buildUri(pluginId, "io.nekohasekai.AngApplication")), flags
            )
                .filter { it.providerInfo.exported }
        }
        if (providers.isEmpty()) {
            providers = AngApplication.application.packageManager.queryIntentContentProviders(
                Intent(PluginContract.ACTION_NATIVE_PLUGIN, buildUri(pluginId, "moe.matsuri.lite")), flags
            )
                .filter { it.providerInfo.exported }
        }
        if (providers.isEmpty()) {
            providers = AngApplication.application.packageManager.queryIntentContentProviders(
                Intent(PluginContract.ACTION_NATIVE_PLUGIN, buildUri(pluginId, "fr.husi")), flags
            )
                .filter { it.providerInfo.exported }
        }
        if (providers.isEmpty()) {
            providers = AngApplication.application.packageManager.queryIntentContentProviders(
                Intent(PluginContract.ACTION_NATIVE_PLUGIN), PackageManager.GET_META_DATA
            ).filter {
                it.providerInfo.exported &&
                        it.providerInfo.metaData.containsKey(METADATA_KEY_ID) &&
                        it.providerInfo.metaData.getString(METADATA_KEY_ID) == pluginId
            }
            if (providers.size > 1) {
                providers = listOf(providers[0]) // What if there is more than one?
            }
        }
        if (providers.isEmpty()) return null
        if (providers.size > 1) {
            val message =
                "Conflicting plugins found from: ${providers.joinToString { it.providerInfo.packageName }}"
            Toast.makeText(AngApplication.application, message, Toast.LENGTH_LONG).show()
            throw IllegalStateException(message)
        }
        val provider = providers.single().providerInfo
        var failure: Throwable? = null
        try {
            initNativeFaster(provider)?.also { return InitResult(it) }
        } catch (t: Throwable) {
            //   Logs.w("Initializing native plugin faster mode failed")
            failure = t
        }

        val uri = Uri.Builder().apply {
            scheme(ContentResolver.SCHEME_CONTENT)
            authority(provider.authority)
        }.build()
        try {
            return initNativeFast(
                AngApplication.application.contentResolver,
                pluginId,
                uri
            )?.let { InitResult(it) }
        } catch (t: Throwable) {
            //  Logs.w("Initializing native plugin fast mode failed")
            failure?.also { t.addSuppressed(it) }
            failure = t
        }

        try {
            return initNativeSlow(
                AngApplication.application.contentResolver,
                pluginId,
                uri
            )?.let { InitResult(it) }
        } catch (t: Throwable) {
            failure?.also { t.addSuppressed(it) }
            throw t
        }
    }

    private fun initNativeFaster(provider: ProviderInfo): String? {
        return provider.loadString(PluginContract.METADATA_KEY_EXECUTABLE_PATH)
            ?.let { relativePath ->
                File(provider.applicationInfo.nativeLibraryDir).resolve(relativePath).apply {
                    check(canExecute())
                }.absolutePath
            }
    }

    private fun initNativeFast(cr: ContentResolver, pluginId: String, uri: Uri): String? {
        return cr.call(uri, PluginContract.METHOD_GET_EXECUTABLE, null, bundleOf())
            ?.getString(PluginContract.EXTRA_ENTRY)?.also {
                check(File(it).canExecute())
            }
    }

    @SuppressLint("Recycle")
    private fun initNativeSlow(cr: ContentResolver, pluginId: String, uri: Uri): String? {
        var initialized = false
        fun entryNotFound(): Nothing =
            throw IndexOutOfBoundsException("Plugin entry binary not found")

        val pluginDir = File(AngApplication.application.noBackupFilesDir, "plugin")
        (cr.query(
            uri,
            arrayOf(PluginContract.COLUMN_PATH, PluginContract.COLUMN_MODE),
            null,
            null,
            null
        )
            ?: return null).use { cursor ->
            if (!cursor.moveToFirst()) entryNotFound()
            pluginDir.deleteRecursively()
            if (!pluginDir.mkdirs()) throw FileNotFoundException("Unable to create plugin directory")
            val pluginDirPath = pluginDir.absolutePath + '/'
            do {
                val path = cursor.getString(0)
                val file = File(pluginDir, path)
                check(file.absolutePath.startsWith(pluginDirPath))
                cr.openInputStream(uri.buildUpon().path(path).build())!!.use { inStream ->
                    file.outputStream().use { outStream -> inStream.copyTo(outStream) }
                }
                Os.chmod(
                    file.absolutePath, when (cursor.getType(1)) {
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(1)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(1).toInt(8)
                        else -> throw IllegalArgumentException("File mode should be of type int")
                    }
                )
                if (path == pluginId) initialized = true
            } while (cursor.moveToNext())
        }
        if (!initialized) entryNotFound()
        return File(pluginDir, pluginId).absolutePath
    }

    fun ComponentInfo.loadString(key: String) = when (val value = metaData.getString(key)) {
        is String -> value
        is Int -> AngApplication.application.packageManager.getResourcesForApplication(applicationInfo)
            .getString(value)

        null -> null
        else -> error("meta-data $key has invalid type ${value.javaClass}")
    }
}
