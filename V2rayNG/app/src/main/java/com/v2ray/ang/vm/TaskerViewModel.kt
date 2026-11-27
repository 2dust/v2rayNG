package com.v2ray.ang.vm

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.shortcut.TaskerItem
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskerViewModel(application: Application) : BaseViewModel(application) {

    private val _items = MutableStateFlow<List<TaskerItem>>(emptyList())
    val items: StateFlow<List<TaskerItem>> = _items.asStateFlow()

    private val _switchState = MutableStateFlow(false)
    val switchState: StateFlow<Boolean> = _switchState.asStateFlow()

    private val _selectedPosition = MutableStateFlow(-1)
    val selectedPosition: StateFlow<Int> = _selectedPosition.asStateFlow()

    fun loadTaskerConfig(intent: Intent?) {
        val taskerItems = mutableListOf<TaskerItem>()
        taskerItems.add(TaskerItem(label = "Default", guid = AppConfig.TASKER_DEFAULT_GUID))

        MmkvManager.decodeAllServerList().forEach { key ->
            MmkvManager.decodeServerConfig(key)?.let { config ->
                taskerItems.add(TaskerItem(label = config.remarks, guid = key))
            }
        }
        _items.value = taskerItems

        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, "")

            if (switch != null && !TextUtils.isEmpty(guid)) {
                _switchState.value = switch
                _selectedPosition.value = taskerItems.indexOfFirst { it.guid == guid.toString() }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to initialize Tasker settings", e)
        }
    }

    fun setSwitchState(enabled: Boolean) {
        _switchState.value = enabled
    }

    fun setSelectedPosition(position: Int) {
        _selectedPosition.value = position
    }

    fun buildResultIntent(): Intent? {
        val position = _selectedPosition.value
        val itemsList = _items.value
        if (position < 0 || position >= itemsList.size) {
            return null
        }

        val extraBundle = Bundle()
        extraBundle.putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, _switchState.value)
        extraBundle.putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, itemsList[position].guid)

        val blurb = getString(
            if (_switchState.value) R.string.tasker_blurb_start else R.string.tasker_blurb_stop,
            itemsList[position].label
        )

        return Intent().apply {
            putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
            putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, blurb)
        }
    }
}
