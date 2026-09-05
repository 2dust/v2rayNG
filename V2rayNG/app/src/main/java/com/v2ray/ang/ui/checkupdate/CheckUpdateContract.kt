package com.v2ray.ang.ui.checkupdate

import androidx.compose.runtime.Immutable
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseUiState

/** A confirmed, downloadable update. */
@Immutable
data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val downloadUrl: String
)

/**
 * The single state of the update-check screen.
 */
@Immutable
data class CheckUpdateUiState(
    val checkPreRelease: Boolean = false,
    val versionText: String = ""
) : BaseUiState

/** The complete set of user intents of the update-check screen. */
sealed interface CheckUpdateAction : BaseAction {
    data object Back : CheckUpdateAction
    data class TogglePreRelease(val enabled: Boolean) : CheckUpdateAction
    data object CheckNow : CheckUpdateAction
    data class DownloadConfirmed(val url: String) : CheckUpdateAction
}

/** One-time effects of this screen, intercepted inside `BaseScreen(onEvent = …)`. */
sealed interface CheckUpdateEvent : BaseEvent.Platform {

    /** A newer release exists; the UI decides how to present it. */
    @Immutable
    data class UpdateAvailable(val update: UpdateInfo) : CheckUpdateEvent
}
