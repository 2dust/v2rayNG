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
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.apppicker.AppPickerActivity
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.LocalEditorFocus
import com.v2ray.ang.ui.compose.LocalTvEditor
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
    RoutingRuleTextField(stringResource(R.string.sub_setting_remarks), draft.remarks, isError = remarksError)
    locked(stringResource(R.string.routing_settings_locked))
    Text(
        text = stringResource(R.string.routing_settings_tips),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    RoutingRuleTextField(stringResource(R.string.routing_settings_domain), draft.domain, placeholder = commaTip)
    RoutingRuleTextField(stringResource(R.string.routing_settings_ip), draft.ip, placeholder = commaTip)
    RoutingRuleTextField(stringResource(R.string.routing_settings_process), draft.process, placeholder = commaTip, enabled = canUseProcess, announceValue = false)
    if (canUseProcess) RoutingProcessPicker(draft)
    RoutingRuleTextField(stringResource(R.string.routing_settings_port), draft.port)
    RoutingRuleTextField(stringResource(R.string.routing_settings_protocol), draft.protocol, placeholder = stringResource(R.string.routing_settings_protocol_tip))
    FormDropdownField(stringResource(R.string.routing_settings_network), draft.network.value.ifBlank { routingNetworkOptions.last() },
        routingNetworkOptions, { draft.network.value = it })
    outbound(stringResource(R.string.routing_settings_outbound_tag))
}

@Composable
private fun RoutingRuleTextField(
    label: String, field: MutableState<String>, placeholder: String? = null,
    enabled: Boolean = true, isError: Boolean = false, announceValue: Boolean = true
) {
    FormTextField(label, field.value, { field.value = it }, enabled = enabled, placeholder = placeholder, isError = isError, announceValue = announceValue)
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
            .then(LocalEditorFocus.current?.target() ?: Modifier)
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
