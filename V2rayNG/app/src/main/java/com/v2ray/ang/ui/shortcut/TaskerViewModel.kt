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

internal sealed interface TaskerConfiguration {
    data class Ready(val item: TaskerItem, val start: Boolean, val token: String) : TaskerConfiguration
    data object NoSelection : TaskerConfiguration
    data object AccessDenied : TaskerConfiguration
}

class TaskerViewModel internal constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val remoteControl: RemoteControlManager,
    private val profiles: MmkvManager
) : BaseViewModel(application) {
    constructor(application: Application, savedState: SavedStateHandle) :
        this(application, savedState, RemoteControlManager, MmkvManager)

    private val _items = MutableStateFlow<List<TaskerItem>>(emptyList())
    val items = _items.asStateFlow()
    val start = savedState.getStateFlow("start", false)
    val selectedGuid = savedState.getStateFlow<String?>("guid", null)

    suspend fun load(callingPackage: String?, initialStart: Boolean?, initialGuid: String?): Boolean {
        if (savedState.get<Boolean>("initialized") != true) {
            savedState["start"] = initialStart ?: false
            savedState["guid"] = initialGuid
            savedState["initialized"] = true
        }
        _isLoading.value = true
        try {
            val allowed = withContext(Dispatchers.IO) {
                remoteControl.configurationToken(getApplication(), callingPackage) != null
            }
            if (!allowed) return false
            val defaultLabel = getString(R.string.tasker_current_profile)
            val loadedItems = withContext(Dispatchers.IO) {
                buildList {
                    add(TaskerItem(defaultLabel, AppConfig.TASKER_DEFAULT_GUID))
                    profiles.decodeAllServerList().forEach { guid ->
                        profiles.decodeServerConfig(guid)?.let { add(TaskerItem(it.remarks, guid)) }
                    }
                }
            }
            savedState["guid"] = selectedGuid.value?.takeIf { guid -> loadedItems.any { it.guid == guid } }
            _items.value = loadedItems
            return true
        } finally {
            _isLoading.value = false
        }
    }

    fun setStart(value: Boolean) { savedState["start"] = value }

    fun select(guid: String) {
        if (_items.value.any { it.guid == guid }) savedState["guid"] = guid
    }

    internal suspend fun configuration(callingPackage: String?): TaskerConfiguration {
        if (isLoading.value) return TaskerConfiguration.NoSelection
        val item = _items.value.firstOrNull { it.guid == selectedGuid.value } ?: return TaskerConfiguration.NoSelection
        val token = withContext(Dispatchers.IO) {
            remoteControl.configurationToken(getApplication(), callingPackage)
        } ?: return TaskerConfiguration.AccessDenied
        return TaskerConfiguration.Ready(item, start.value, token)
    }
}
