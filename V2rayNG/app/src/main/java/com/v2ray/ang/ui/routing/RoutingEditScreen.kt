package com.v2ray.ang.ui.routing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.StringOptions
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val FormVerticalPadding = 8.dp
private val ButtonStartPadding = 16.dp
private val ButtonIconSpacing = 8.dp
private val BottomSpacerHeight = 36.dp

@Stable
private class RoutingFieldCallbacks(onAction: (RoutingEditAction) -> Unit) {
    private val callbacks = mapOf(
        RoutingField.REMARKS to { value: String -> onAction(RoutingEditAction.UpdateRemarks(value)) },
        RoutingField.DOMAIN to { value: String -> onAction(RoutingEditAction.UpdateDomain(value)) },
        RoutingField.IP to { value: String -> onAction(RoutingEditAction.UpdateIp(value)) },
        RoutingField.PROCESS to { value: String -> onAction(RoutingEditAction.UpdateProcess(value)) },
        RoutingField.PROTOCOL to { value: String -> onAction(RoutingEditAction.UpdateProtocol(value)) },
        RoutingField.NETWORK to { value: String -> onAction(RoutingEditAction.UpdateNetwork(value)) },
        RoutingField.PORT to { value: String -> onAction(RoutingEditAction.UpdatePort(value)) },
        RoutingField.OUTBOUND to { value: String -> onAction(RoutingEditAction.UpdateOutbound(value)) },
    )
    operator fun get(field: RoutingField): (String) -> Unit = callbacks.getValue(field)
}

@Composable
fun RoutingEditScreen(
    viewModel: RoutingEditViewModel,
    modifier: Modifier = Modifier
) {
    val onAction = remember(viewModel) { viewModel::onAction }
    val callbacks = remember(onAction) { RoutingFieldCallbacks(onAction) }

    val isEditFlow = remember(viewModel) {
        viewModel.uiState.map { it.isEdit }.distinctUntilChanged()
    }
    val isEdit by isEditFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.uiState.value.isEdit
    )

    var showDeleteDialog by remember { mutableStateOf(false) }

    BackHandler { onAction(RoutingEditAction.Back) }

    BaseScreen(
        viewModel = viewModel,
        modifier = modifier,
        topBar = { RoutingEditTopBar(isEdit = isEdit, onAction = onAction) },
        onResult = { result -> onAction(RoutingEditAction.ResultReceived(result)) },
        onEvent = { event ->
            when (event) {
                is RoutingEditEvent.ShowDeleteDialog -> {
                    showDeleteDialog = true
                    true
                }
                else -> false
            }
        }
    ) { state, _ ->
        RoutingEditForm(
            state = state,
            callbacks = callbacks,
            onAction = onAction,
            modifier = Modifier.fillMaxSize().imePadding(),
        )
        if (showDeleteDialog) {
            DeleteConfirmDialog(
                message = stringResource(R.string.confirm_delete_routing_rule),
                onConfirm = {
                    showDeleteDialog = false
                    onAction(RoutingEditAction.ConfirmDelete)
                },
                onDismiss = { showDeleteDialog = false },
            )
        }
    }
}

@Composable
private fun RoutingEditTopBar(
    isEdit: Boolean,
    onAction: (RoutingEditAction) -> Unit,
    modifier: Modifier = Modifier
) {
    AppTopBar(
        title = stringResource(R.string.routing_settings_rule_title),
        onBackClick = { onAction(RoutingEditAction.Back) },
        modifier = modifier,
        actions = {
            if (isEdit) {
                IconButton(onClick = { onAction(RoutingEditAction.Delete) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.acc_delete),
                    )
                }
            }
            IconButton(onClick = { onAction(RoutingEditAction.Save) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_fab_check),
                    contentDescription = stringResource(R.string.acc_save),
                )
            }
        },
    )
}

@Composable
private fun RoutingEditForm(
    state: RoutingEditUiState,
    callbacks: RoutingFieldCallbacks,
    onAction: (RoutingEditAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val form = state.form
    val outboundOptions = remember(state.outboundOptions) { StringOptions(state.outboundOptions) }

    Column(
        modifier = modifier
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(vertical = FormVerticalPadding),
    ) {
        FormTextField(
            label = stringResource(R.string.sub_setting_remarks),
            value = form.remarks,
            onValueChange = callbacks[RoutingField.REMARKS],
        )
        SettingsSwitchItem(
            title = stringResource(R.string.routing_settings_locked),
            checked = form.locked,
            onCheckedChange = { locked -> onAction(RoutingEditAction.ToggleLocked(locked)) },
        )
        FormTextField(
            label = stringResource(R.string.routing_settings_domain),
            placeholder = stringResource(R.string.routing_settings_tips),
            value = form.domain,
            onValueChange = callbacks[RoutingField.DOMAIN],
        )
        FormTextField(
            label = stringResource(R.string.routing_settings_ip),
            placeholder = stringResource(R.string.routing_settings_tips),
            value = form.ip,
            onValueChange = callbacks[RoutingField.IP],
        )
        FormTextField(
            label = stringResource(R.string.routing_settings_process),
            placeholder = stringResource(R.string.routing_settings_tips),
            value = form.process,
            onValueChange = callbacks[RoutingField.PROCESS],
            enabled = state.canUseProcess,
        )
        if (state.canUseProcess) {
            TextButton(
                onClick = { onAction(RoutingEditAction.SelectProcess) },
                modifier = Modifier.padding(start = ButtonStartPadding),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_per_apps_24dp),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(ButtonIconSpacing))
                Text(stringResource(R.string.routing_settings_process_select))
            }
        }
        FormTextField(
            label = stringResource(R.string.routing_settings_port),
            value = form.port,
            onValueChange = callbacks[RoutingField.PORT],
        )
        FormTextField(
            label = stringResource(R.string.routing_settings_protocol),
            placeholder = stringResource(R.string.routing_settings_protocol_tip),
            value = form.protocol,
            onValueChange = callbacks[RoutingField.PROTOCOL],
        )
        FormDropdownField(
            label = stringResource(R.string.routing_settings_network),
            value = form.networkOrDefault,
            options = RoutingNetworkOptions,
            onValueChange = callbacks[RoutingField.NETWORK],
        )
        FormDropdownField(
            label = stringResource(R.string.routing_settings_outbound_tag),
            placeholder = stringResource(
                R.string.routing_settings_outbound_tag_hint,
                stringResource(R.string.server_lab_remarks),
            ),
            value = form.outboundTag,
            options = outboundOptions,
            onValueChange = callbacks[RoutingField.OUTBOUND],
            editable = true,
        )
        Spacer(modifier = Modifier.height(BottomSpacerHeight))
        NavigationBarsSpacer()
    }
}

private val RoutingNetworkOptions = StringOptions(ROUTING_NETWORK_OPTIONS)

@Preview(showBackground = true)
@Composable
private fun PreviewRoutingEditForm() {
    AppTheme {
        RoutingEditForm(
            state = RoutingEditUiState(
                ruleId = "preview",
                form = RoutingForm(remarks = "Preview", domain = "geosite:google"),
                outboundOptions = listOf("proxy", "direct"),
                canUseProcess = true,
            ),
            callbacks = RoutingFieldCallbacks {},
            onAction = {},
        )
    }
}
