package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalEditorFocus
import com.v2ray.ang.ui.compose.LocalTvEditor
import com.v2ray.ang.ui.compose.RestoreTvDialogOpener
import com.v2ray.ang.ui.compose.TvSearchableOptionsDialog
import com.v2ray.ang.ui.compose.dpadFocusOutline

@Composable
internal fun TvPolicyFallbackField(value: String, options: List<String>, onSelect: (String) -> Unit) {
    val uniqueOptions = remember(options) { options.distinct() }
    val focus = checkNotNull(LocalEditorFocus.current)
    val back = checkNotNull(LocalTvEditor.current)
    val picker = remember { FocusRequester() }
    var open by rememberSaveable { mutableStateOf(false) }
    RestoreTvDialogOpener(open, picker)
    PolicyFallbackRow(
        value, onPick = { open = true }, onClear = { onSelect("") },
        pickModifier = focus.target(picker).focusProperties { start = back }.dpadFocusOutline(cornerRadius = 8.dp),
        clearModifier = focus.target().dpadFocusOutline(cornerRadius = 24.dp)
    )
    if (open) {
        TvSearchableOptionsDialog(stringResource(R.string.title_policy_group_fallback), uniqueOptions, value,
            onSelect = { onSelect(it); open = false }, onDismiss = { open = false })
    }
}
