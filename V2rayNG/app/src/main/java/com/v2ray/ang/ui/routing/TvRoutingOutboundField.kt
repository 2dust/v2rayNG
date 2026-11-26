package com.v2ray.ang.ui.routing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.R
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.LocalEditorFocus
import com.v2ray.ang.ui.compose.ProfilePickerChoices
import com.v2ray.ang.ui.compose.RestoreTvDialogOpener
import com.v2ray.ang.ui.compose.TvSearchableOptionsDialog
import com.v2ray.ang.ui.compose.dpadFocusOutline

@Composable
internal fun TvRoutingOutboundField(label: String, value: String, onChange: (String) -> Unit, back: FocusRequester) {
    val anchor = remember { FocusRequester() }
    var open by rememberSaveable { mutableStateOf(false) }
    RestoreTvDialogOpener(open, anchor)
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .then(LocalEditorFocus.current?.target(anchor) ?: Modifier)
            .focusProperties { start = back }.dpadFocusOutline(cornerRadius = 24.dp)
            .semantics { stateDescription = value }
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            if (value.isNotBlank()) Text(value, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null)
    }
    if (open) ProfilePickerChoices({ (BUILTIN_OUTBOUND_TAGS + SettingsManager.getProfileRemarks()).toList() }, { open = false }) { options ->
        TvSearchableOptionsDialog(label, options, value, onSelect = { onChange(it); open = false },
            onDismiss = { open = false }, showClearSearch = true)
    }
}
