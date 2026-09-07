package com.v2ray.ang.ui.shortcut

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.RemoteControlManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class TaskerItem(val label: String, val guid: String)

internal data class TaskerConfiguration(val item: TaskerItem, val start: Boolean, val token: String)

class TaskerViewModel(application: Application, private val savedState: SavedStateHandle) : BaseViewModel(application) {
    private val _items = MutableStateFlow<List<TaskerItem>>(emptyList())
    val items = _items.asStateFlow()
    val start = savedState.getStateFlow("start", false)
    val selectedGuid = savedState.getStateFlow<String?>("guid", null)

    suspend fun load(callingPackage: String?, initialStart: Boolean?, initialGuid: String?): Boolean {
        val allowed = withContext(Dispatchers.IO) {
            RemoteControlManager.configurationToken(getApplication(), callingPackage) != null
        }
        if (!allowed) return false
        val defaultLabel = getString(R.string.tasker_current_profile)
        _items.value = withContext(Dispatchers.IO) {
            buildList {
                add(TaskerItem(defaultLabel, AppConfig.TASKER_DEFAULT_GUID))
                MmkvManager.decodeAllServerList().forEach { guid ->
                    MmkvManager.decodeServerConfig(guid)?.let { add(TaskerItem(it.remarks, guid)) }
                }
            }
        }
        if (savedState.get<Boolean>("initialized") != true) {
            savedState["start"] = initialStart ?: false
            savedState["guid"] = initialGuid
            savedState["initialized"] = true
        }
        return true
    }

    fun setStart(value: Boolean) { savedState["start"] = value }

    fun select(guid: String) {
        if (_items.value.any { it.guid == guid }) savedState["guid"] = guid
    }

    internal suspend fun configuration(callingPackage: String?): TaskerConfiguration? {
        val item = _items.value.firstOrNull { it.guid == selectedGuid.value } ?: return null
        val token = withContext(Dispatchers.IO) {
            RemoteControlManager.configurationToken(getApplication(), callingPackage)
        } ?: return null
        return TaskerConfiguration(item, start.value, token)
    }
}
