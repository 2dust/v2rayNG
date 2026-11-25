package com.v2ray.ang.ui.routing

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.apppicker.AppPickerActivity
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.LocalOutboundEditorFocus
import com.v2ray.ang.ui.compose.LocalTvEditor
import com.v2ray.ang.ui.compose.TvEditorDropdown
import com.v2ray.ang.ui.compose.TvEditorTextField
import com.v2ray.ang.ui.compose.dpadFocusOutline

@Composable
internal fun RoutingRuleFields(
    draft: RoutingRuleDraft,
    canUseProcess: Boolean,
    remarksError: Boolean,
    locked: @Composable (String) -> Unit,
    outbound: @Composable (String) -> Unit
) {
    val commaTip = stringResource(R.string.routing_settings_comma_tip)
    RoutingRuleTextField(stringResource(R.string.sub_setting_remarks), draft.remarks.value, { draft.remarks.value = it }, isError = remarksError)
    locked(stringResource(R.string.routing_settings_locked))
    Text(
        text = stringResource(R.string.routing_settings_tips),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    RoutingRuleTextField(stringResource(R.string.routing_settings_domain), draft.domain.value, { draft.domain.value = it }, placeholder = commaTip)
    RoutingRuleTextField(stringResource(R.string.routing_settings_ip), draft.ip.value, { draft.ip.value = it }, placeholder = commaTip)
    RoutingRuleTextField(stringResource(R.string.routing_settings_process), draft.process.value, { draft.process.value = it },
        placeholder = commaTip, enabled = canUseProcess)
    if (canUseProcess) RoutingProcessPicker(draft)
    RoutingRuleTextField(stringResource(R.string.routing_settings_port), draft.port.value, { draft.port.value = it })
    RoutingRuleTextField(stringResource(R.string.routing_settings_protocol), draft.protocol.value, { draft.protocol.value = it },
        placeholder = stringResource(R.string.routing_settings_protocol_tip))
    val networkLabel = stringResource(R.string.routing_settings_network)
    val network = draft.network.value.ifBlank { routingNetworkOptions.last() }
    val setNetwork: (String) -> Unit = { draft.network.value = it }
    if (LocalTvEditor.current == null) {
        FormDropdownField(networkLabel, network, routingNetworkOptions, setNetwork)
    } else {
        TvEditorDropdown(networkLabel, network, routingNetworkOptions, setNetwork, Modifier, true, null, null)
    }
    outbound(stringResource(R.string.routing_settings_outbound_tag))
}

@Composable
private fun RoutingRuleTextField(
    label: String, value: String, onChange: (String) -> Unit, placeholder: String? = null,
    enabled: Boolean = true, isError: Boolean = false
) {
    if (LocalTvEditor.current == null) {
        FormTextField(label, value, onChange, enabled = enabled, placeholder = placeholder, isError = isError)
    } else {
        TvEditorTextField(label, value, onChange, Modifier, enabled, KeyboardType.Text, placeholder, 5, isError, null)
    }
}

@Composable
private fun RoutingProcessPicker(draft: RoutingRuleDraft) {
    val context = LocalContext.current
    val title = stringResource(R.string.routing_settings_process_select)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            draft.process.value = AppPickerActivity.getSelectedPackages(result.data).joinToString(",")
        }
    }
    val back = LocalTvEditor.current
    val modifier = if (back == null) {
        Modifier.padding(start = 16.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .then(LocalOutboundEditorFocus.current?.target() ?: Modifier)
            .focusProperties { start = back }
            .dpadFocusOutline(cornerRadius = 24.dp)
    }
    TextButton(
        onClick = {
            launcher.launch(AppPickerActivity.createIntent(context, draft.process.value.parseRoutingRuleList().distinct(), title))
        },
        modifier = modifier
    ) {
        Icon(painterResource(R.drawable.ic_per_apps_24dp), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(title)
    }
}
