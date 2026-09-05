package com.v2ray.ang.ui.shortcut

import androidx.compose.runtime.Immutable
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseUiState

/**
 * The job a launcher shortcut performs. Passing it into the ViewModel is what lets the four
 * `Sc*Activity` entries share a single ViewModel instead of duplicating one per entry.
 */
enum class ShortcutCommand {
    START, STOP, SWITCH, IMPORT_QR_CODE;

    /** The service entries run headless and must not leave a visible task behind. */
    val hidesTask: Boolean get() = this != IMPORT_QR_CODE
}

@Immutable
data class ShortcutUiState(
    /**
     * Which entry is running.
     *
     * The default exists only to satisfy the "every field has a default" rule. Hosts MUST pass the
     * real command: falling back to [ShortcutCommand.SWITCH] would silently turn a "stop" shortcut
     * into a toggle.
     */
    val command: ShortcutCommand = ShortcutCommand.SWITCH
) : BaseUiState

/** The complete set of intents of the shortcut entries. */
sealed interface ShortcutAction : BaseAction {
    /** Result handed back by the QR scanner; `null` means the user cancelled. */
    data class ScanResultReceived(val text: String?) : ShortcutAction
}

/**
 * Platform-level events.
 */
sealed interface ShortcutEvent : BaseEvent.Platform {
    data object MoveToBack : ShortcutEvent
    data object StartService : ShortcutEvent
    data object StopService : ShortcutEvent
    data object ScanQrCode : ShortcutEvent
}
