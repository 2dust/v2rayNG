package com.v2ray.ang.ui.shortcut

import com.v2ray.ang.R
import com.v2ray.ang.repository.ShortcutRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.ToastType

class ShortcutViewModel(
    command: ShortcutCommand,
    private val repo: ShortcutRepository
) : BaseViewModel<ShortcutUiState, ShortcutAction>(ShortcutUiState(command)) {

    init {
        dispatchCommand()
    }

    override fun onAction(action: ShortcutAction) {
        when (action) {
            is ShortcutAction.ScanResultReceived -> onScanResult(action.text)
        }
    }

    private fun dispatchCommand() = launch(onError = { reportFailureAndClose() }) {
        val command = state.command

        if (command == ShortcutCommand.IMPORT_QR_CODE) {
            platform(ShortcutEvent.ScanQrCode)
            return@launch
        }

        val running = repo.isCoreRunning()
        when (command) {
            // Idempotent on purpose: tapping "start" while running must not restart the core.
            ShortcutCommand.START -> if (!running) platform(ShortcutEvent.StartService)

            ShortcutCommand.STOP -> if (running) platform(ShortcutEvent.StopService)

            ShortcutCommand.SWITCH -> platform(
                if (running) ShortcutEvent.StopService else ShortcutEvent.StartService
            )

            ShortcutCommand.IMPORT_QR_CODE -> Unit // unreachable, handled above
        }

        if (command.hidesTask) platform(ShortcutEvent.MoveToBack)
        finishWith()
    }

    /**
     * Imports the scanned payload, reports the outcome and hands control to the main screen.
     *
     * [BaseResult.Changed] rather than [BaseResult.Saved]: `Changed.notify` is false, so a caller
     * cannot add a second "ok" on top of the toast this screen already showed. The entry is
     * normally launched by the system launcher, which shows nothing by itself.
     */
    private fun onScanResult(text: String?) {
        if (text.isNullOrBlank()) {
            finishWith()
            return
        }
        launch(onError = { reportFailureAndClose() }) {
            val imported = repo.importBatchConfig(text)
            if (imported <= 0) {
                toastError(R.string.toast_none_data)
                finishWith()
                return@launch
            }
            toast(BaseText.of(R.string.title_import_config_count, imported), ToastType.SUCCESS)
            navigate(AppRoute.Main)
            finishWith(BaseResult.Changed(refreshList = true))
        }
    }

    private fun reportFailureAndClose() {
        toastError()
        finishWith()
    }
}
