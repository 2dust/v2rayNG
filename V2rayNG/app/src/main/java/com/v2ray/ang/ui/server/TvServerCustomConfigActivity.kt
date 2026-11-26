package com.v2ray.ang.ui.server

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalEditorFocus
import com.v2ray.ang.ui.compose.LocalTvEditor
import com.v2ray.ang.ui.compose.TvEditorTextField
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.rememberEditorFocus
import com.v2ray.ang.ui.compose.rememberTvEditorIme

class TvServerCustomConfigActivity : BaseServerCustomConfigActivity() {
    @Composable
    override fun editorControls(text: TextFieldState, confirmingDelete: Boolean, onBack: () -> Unit): CustomEditorControls {
        val back = remember { FocusRequester() }
        val focus = rememberEditorFocus(fallback = back)
        val ime = rememberTvEditorIme()
        val json = rememberTvJsonFocus(back, focus, ime.visible, text)
        return CustomEditorControls(
            contentPadding = ime.padding.padding(horizontal = 48.dp),
            jsonField = json.field,
            remarks = { value, onValueChange, isError ->
                CompositionLocalProvider(LocalTvEditor provides back, LocalEditorFocus provides focus) {
                    TvEditorTextField(stringResource(R.string.server_lab_remarks), value, onValueChange, isError = isError, announceValue = true)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = focus.target(back).dpadFocusOutline(cornerRadius = 24.dp)) {
                    Icon(painterResource(R.drawable.ic_tv_arrow_back_24dp), contentDescription = stringResource(R.string.acc_back))
                }
            },
            actionModifier = { focus.target().dpadFocusOutline(cornerRadius = 24.dp) },
            scrollFocus = Modifier.focusProperties { canFocus = false },
            jsonContainer = Modifier.padding(top = 8.dp, bottom = 12.dp).then(json.passive ?: Modifier),
            jsonEnabled = json.passive == null,
            keyboardOptions = if (json.allowKeyboardOnFocus) KeyboardOptions.Default else KeyboardOptions(showKeyboardOnFocus = false)
        )
    }
}
