package com.v2ray.ang.ui.shortcut

import androidx.compose.runtime.Composable
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.repository.ShortcutRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseHelperActivity
import com.v2ray.ang.ui.base.baseViewModels

/**
 * Toggles the core: stops it when running, starts it otherwise.
 * This entry runs headless and moves to the back immediately.
 */
class ScSwitchActivity : ScServiceActivity(ShortcutCommand.SWITCH)

/** Starts the core unless it is already running. */
class ScStartActivity : ScServiceActivity(ShortcutCommand.START)

/** Stops the core if it is running. */
class ScStopActivity : ScServiceActivity(ShortcutCommand.STOP)

/**
 * Shared host of the three service entries. It holds no state and makes no decisions - it only
 * performs the platform work described by [ShortcutEvent] and hands the ViewModel its command.
 */
abstract class ScServiceActivity(private val command: ShortcutCommand) : BaseActivity() {

    private val viewModel: ShortcutViewModel by baseViewModels { _, _ ->
        ShortcutViewModel(command, ShortcutRepository())
    }

    @Composable
    override fun ScreenContent() = ShortcutScreen(viewModel, ::handleEvent)

    /** @return true when the event has been consumed. */
    private fun handleEvent(event: BaseEvent): Boolean = when (event) {
        ShortcutEvent.MoveToBack -> {
            moveTaskToBack(true)
            true
        }

        ShortcutEvent.StartService -> {
            LauncherManager.startServiceFromToggle(this)
            true
        }

        ShortcutEvent.StopService -> {
            LauncherManager.stopService(this)
            true
        }

        else -> false
    }
}

/**
 * Imports a profile from a QR code, then opens the main screen.
 * Extends [BaseHelperActivity] because scanning is a platform capability.
 */
class ScScannerActivity : BaseHelperActivity() {

    private val viewModel: ShortcutViewModel by baseViewModels { _, _ ->
        ShortcutViewModel(ShortcutCommand.IMPORT_QR_CODE, ShortcutRepository())
    }

    @Composable
    override fun ScreenContent() = ShortcutScreen(viewModel, ::handleEvent)

    /** @return true when the event has been consumed. */
    private fun handleEvent(event: BaseEvent): Boolean = when (event) {
        ShortcutEvent.ScanQrCode -> {
            // The scan result re-enters the ViewModel as an Action - the Activity decides nothing.
            scanQrCode { text -> viewModel.onAction(ShortcutAction.ScanResultReceived(text)) }
            true
        }

        else -> false
    }
}
