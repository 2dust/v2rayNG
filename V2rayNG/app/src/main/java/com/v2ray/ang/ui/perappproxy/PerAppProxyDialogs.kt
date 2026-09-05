package com.v2ray.ang.ui.perappproxy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.ConfirmDialog

/** Dialogs of this screen.*/
internal sealed interface PerAppProxyDialog {

    data object Info : PerAppProxyDialog
}

/**
 * Holder of the currently open dialog.
 */
@Stable
internal class PerAppProxyDialogHost {

    var current by mutableStateOf<PerAppProxyDialog?>(null)
        private set

    val showInfo: () -> Unit = { current = PerAppProxyDialog.Info }

    val dismiss: () -> Unit = { current = null }
}

/** Single rendering point of every dialog of the screen. */
@Composable
internal fun PerAppProxyDialogs(
    dialog: PerAppProxyDialog?,
    onDismiss: () -> Unit
) {
    when (dialog) {
        null -> Unit
        PerAppProxyDialog.Info -> ConfirmDialog(
            message = stringResource(R.string.summary_pref_per_app_proxy),
            onConfirm = {},
            onDismiss = onDismiss,
            title = stringResource(R.string.title_pref_per_app_proxy),
            confirmText = stringResource(R.string.action_close),
            dismissText = null
        )
    }
}
