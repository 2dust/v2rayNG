package com.v2ray.ang.ui.shortcut

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.repository.ShortcutRepository
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.base.BaseViewModel

class TaskerViewModel(
    private val handle: SavedStateHandle,
    private val repo: ShortcutRepository
) : BaseViewModel<TaskerUiState, TaskerAction>(initialState(handle)) {

    init {
        load()
    }

    /**
     * Builds the row list: the synthetic "current server" entry first, then the stored profiles.
     */
    private fun load() = launch(loading = true) {
        val stored = repo.loadTaskerProfiles()
        val items = buildList(stored.size + 1) {
            add(
                TaskerProfileItem(
                    guid = AppConfig.TASKER_DEFAULT_GUID,
                    name = BaseText.of(R.string.tasker_default_profile)
                )
            )
            stored.forEach { profile ->
                add(TaskerProfileItem(profile.guid, BaseText.of(profile.remarks)))
            }
        }
        setState {
            copy(
                profiles = items,
                // Drop a restored guid whose profile has since been deleted.
                selectedGuid = selectedGuid?.takeIf { guid -> items.any { it.guid == guid } }
            )
        }
    }

    override fun onAction(action: TaskerAction) {
        when (action) {
            TaskerAction.Back -> finishWith(BaseResult.Cancelled)
            TaskerAction.Save -> save()

            is TaskerAction.ToggleStartService -> {
                setState { copy(startService = action.enabled) }
                handle[KEY_EDIT_START] = action.enabled
            }

            is TaskerAction.SelectProfile -> {
                setState { copy(selectedGuid = action.guid) }
                handle[KEY_EDIT_GUID] = action.guid
            }
        }
    }

    private fun save() {
        val profile = state.profiles.firstOrNull { it.guid == state.selectedGuid }
        if (profile == null) {
            toastError(R.string.toast_tasker_select_profile)
            return
        }
        val blurb = BaseText.of(
            if (state.startService) R.string.tasker_blurb_start else R.string.tasker_blurb_stop,
            profile.name
        )
        platform(TaskerEvent.SaveSetting(state.startService, profile.guid, blurb))
    }
}

/** Edit-state keys; kept apart from the Tasker protocol extras that seed the initial state. */
private const val KEY_EDIT_START = "tasker_edit_start"
private const val KEY_EDIT_GUID = "tasker_edit_guid"

/**
 * Reads the settings Tasker passes back for editing. Intent extras are exposed through the
 * SavedStateHandle.
 */
private fun initialState(handle: SavedStateHandle): TaskerUiState {
    val bundle = handle.get<Bundle>(AppConfig.TASKER_EXTRA_BUNDLE)
    val hostStart = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false) ?: false
    val hostGuid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID)?.ifEmpty { null }
    return TaskerUiState(
        startService = handle.get<Boolean>(KEY_EDIT_START) ?: hostStart,
        selectedGuid = handle.get<String>(KEY_EDIT_GUID) ?: hostGuid
    )
}
