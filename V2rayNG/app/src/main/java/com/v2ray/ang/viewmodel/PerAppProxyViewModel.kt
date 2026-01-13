package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager

class PerAppProxyViewModel : ViewModel() {
    private val blacklist: MutableSet<String> = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.let {
        HashSet(it)
    } ?: HashSet()

    fun contains(packageName: String): Boolean = blacklist.contains(packageName)

    fun getAll(): Set<String> = blacklist.toSet()

    fun add(packageName: String): Boolean {
        val changed = blacklist.add(packageName)
        if (changed) {
            save()
        }
        return changed
    }

    fun remove(packageName: String): Boolean {
        val changed = blacklist.remove(packageName)
        if (changed) {
            save()
        }
        return changed
    }

    fun toggle(packageName: String) {
        if (blacklist.contains(packageName)) {
            remove(packageName)
        } else {
            add(packageName)
        }
    }

    fun addAll(packages: Collection<String>) {
        if (blacklist.addAll(packages)) {
            save()
        }
    }

    fun removeAll(packages: Collection<String>) {
        if (blacklist.removeAll(packages.toSet())) {
            save()
        }
    }

    fun clear() {
        if (blacklist.isNotEmpty()) {
            blacklist.clear()
            save()
        }
    }

    private fun save() {
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, blacklist)
        SettingsChangeManager.makeRestartService()
    }
}