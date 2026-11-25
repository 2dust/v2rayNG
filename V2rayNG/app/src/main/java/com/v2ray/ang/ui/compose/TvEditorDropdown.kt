package com.v2ray.ang.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.settings.TvSettingsListDialog

@Composable
internal fun TvEditorDropdown(
    label: String, value: String, options: List<String>, onValueChange: (String) -> Unit,
    modifier: Modifier, enabled: Boolean, placeholder: String?, supportingText: String?,
    optionLabel: (String) -> String = { it }
) {
    val back = checkNotNull(LocalTvEditor.current)
    val anchor = remember { FocusRequester() }
    val anchorFocus = LocalEditorFocus.current?.target(anchor) ?: Modifier.focusRequester(anchor)
    val interaction = remember { MutableInteractionSource() }
    var open by rememberSaveable { mutableStateOf(false) }
    RestoreTvDialogOpener(open, anchor)
    val displayValue = optionLabel(value)
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null) },
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false }.clearAndSetSemantics { }
        )
        Box(
            Modifier.matchParentSize()
                .then(anchorFocus)
                .focusProperties { start = back }
                .semantics { contentDescription = if (displayValue.isBlank()) label else "$label: $displayValue" }
                .clickable(interactionSource = interaction, indication = null, enabled = enabled && options.isNotEmpty(), role = Role.Button) {
                    open = true
                }
        )
    }
    if (open) {
        val choices = options.map { option -> optionLabel(option).ifBlank { "---" } to option }
        TvSettingsListDialog(label, choices, value, onSelected = { open = false; onValueChange(it) }, onDismiss = { open = false })
    }
}
