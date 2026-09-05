package com.v2ray.ang.ui.shortcut

import androidx.compose.runtime.Immutable
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.base.BaseUiState

@Immutable
data class TaskerProfileItem(
    val guid: String,
    val name: BaseText
)

@Immutable
data class TaskerUiState(
    val profiles: List<TaskerProfileItem> = emptyList(),
    /** true = the task starts the core, false = it stops the core. */
    val startService: Boolean = false,
    /** Null until the user picks a profile; saving is rejected while it is null. */
    val selectedGuid: String? = null
) : BaseUiState

/** The complete set of user intents of the Tasker editor. */
sealed interface TaskerAction : BaseAction {
    data object Back : TaskerAction
    data object Save : TaskerAction
    data class ToggleStartService(val enabled: Boolean) : TaskerAction
    data class SelectProfile(val guid: String) : TaskerAction
}

/**
 * Tasker/Locale is an *external* plugin protocol: the host requires its own extras
 * (`TASKER_EXTRA_BUNDLE` + `TASKER_EXTRA_STRING_BLURB`), which [com.v2ray.ang.ui.base.BaseResult]
 * cannot express.
 */
sealed interface TaskerEvent : BaseEvent.Platform {
    /**
     * @param blurb label Tasker shows in its own task list;
     */
    data class SaveSetting(
        val startService: Boolean,
        val guid: String,
        val blurb: BaseText
    ) : TaskerEvent
}
