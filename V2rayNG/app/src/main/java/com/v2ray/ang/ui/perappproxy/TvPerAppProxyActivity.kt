package com.v2ray.ang.ui.perappproxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.TvAppMenuAction
import com.v2ray.ang.ui.compose.TvAppSelectionList
import com.v2ray.ang.ui.compose.TvAppSelectionToolbar
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.rememberTvAppListFocus
import com.v2ray.ang.ui.compose.rememberTvAppSearch
import com.v2ray.ang.ui.compose.requestFocusWhenReady

class TvPerAppProxyActivity : BasePerAppProxyActivity() {
    @Composable
    override fun PerAppContent(state: PerAppProxyState, actions: PerAppProxyActions) {
        TvPerAppProxyScreen(state, actions)
    }
}

private enum class ModeControl { Enable, Bypass, Info }

@Composable
private fun TvPerAppProxyScreen(state: PerAppProxyState, actions: PerAppProxyActions) {
    val search = rememberTvAppSearch(actions.onSearch)
    val appFocus = rememberTvAppListFocus(state.apps)
    val modes = remember { ModeControl.entries.associateWith { FocusRequester() } }
    val modeOrder = remember(modes) { ModeControl.entries.map(modes::getValue) }
    val enable = modes.getValue(ModeControl.Enable)
    val info = modes.getValue(ModeControl.Info)
    val menu = remember(actions) {
        PerAppMenuAction.entries.map { TvAppMenuAction(it.labelRes) { actions.onMenuAction(it) } }
    }
    var focusedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedMode by rememberSaveable { mutableStateOf<ModeControl?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var returnToInfo by remember { mutableStateOf(false) }
    val initialPackage = remember { focusedPackage }
    val initialMode = remember { focusedMode }
    var needsInitialFocus by remember { mutableStateOf(true) }

    LaunchedEffect(state.isLoading, appFocus, showInfo) {
        if (!showInfo && needsInitialFocus && (initialPackage == null || !state.isLoading || initialPackage in appFocus.targets)) {
            needsInitialFocus = false
            if (initialPackage?.let(appFocus::focus) != true) {
                requestFocusWhenReady(initialMode?.let(modes::get) ?: search.toolbarEntry)
            }
        }
    }
    LaunchedEffect(returnToInfo) {
        if (returnToInfo) {
            requestFocusWhenReady(info)
            returnToInfo = false
        }
    }

    @Composable
    fun modeNavigation(control: ModeControl): Modifier = Modifier
        .onFocusChanged {
            if (it.isFocused) {
                focusedMode = control
                focusedPackage = null
            }
        }
        .dpadOrderedFocusNavigation(modes.getValue(control), modeOrder, onBeforeFirst = { search.back.requestFocus() })
        .dpadVerticalFocusNavigation(
            onMoveUp = { search.toolbarEntry.requestFocus() },
            onMoveDown = { appFocus.focusAt(0); true }
        )

    Scaffold(
        topBar = {
            TvAppSelectionToolbar(
                stringResource(R.string.per_app_proxy_settings), state.isLoading, search, menu, actions.onBack,
                onMoveDown = { enable.requestFocus() },
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) {
                        focusedPackage = null
                        focusedMode = null
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).imePadding().padding(horizontal = 48.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvPerAppSwitch(
                    stringResource(R.string.per_app_proxy_settings_enable), state.enabled, actions.onEnabledChanged,
                    enable, Modifier.weight(1f).then(modeNavigation(ModeControl.Enable))
                )
                TvPerAppSwitch(
                    stringResource(R.string.switch_bypass_apps_mode), state.bypassApps, actions.onBypassChanged,
                    modes.getValue(ModeControl.Bypass), Modifier.weight(1f).then(modeNavigation(ModeControl.Bypass))
                )
                TvIconButton(
                    painterResource(R.drawable.ic_about_24dp), stringResource(R.string.action_info), { showInfo = true },
                    modifier = modeNavigation(ModeControl.Info), focusRequester = info,
                    contentDescription = stringResource(R.string.acc_per_app_proxy_information)
                )
            }
            AppDivider()
            TvAppSelectionList(
                state.apps, state.blacklist, appFocus, search.back, actions.onToggleApp,
                onAppFocused = { focusedPackage = it; focusedMode = null },
                onMoveAboveList = { enable.requestFocus() },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
    if (showInfo) {
        TvPerAppInfoDialog {
            showInfo = false
            needsInitialFocus = false
            returnToInfo = true
        }
    }
}

@Composable
private fun TvPerAppSwitch(
    label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, requester: FocusRequester, modifier: Modifier
) {
    Row(
        modifier.heightIn(min = 48.dp).dpadFocusOutline(requester, cornerRadius = 16.dp)
            .toggleable(checked, interactionSource = null, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked, onCheckedChange = null, modifier = Modifier.scale(0.65f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
private fun TvPerAppInfoDialog(onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(close) { requestFocusWhenReady(close) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.summary_pref_per_app_proxy), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.dpadFocusOutline(close, cornerRadius = 24.dp)) {
                Text(stringResource(R.string.action_close))
            }
        },
        modifier = Modifier.onPreviewKeyEvent { event ->
            // Dismiss on release so neither half of the key press reaches the underlying screen.
            if (event.type == KeyEventType.KeyUp) onDismiss()
            true
        },
        properties = DialogProperties(dismissOnClickOutside = false)
    )
}
