package com.v2ray.ang.ui.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Stable
private class FormDropdownState {
    var expanded by mutableStateOf(false)
        private set
    var restoreFieldFocus by mutableStateOf(false)

    fun open() {
        restoreFieldFocus = false
        expanded = true
    }

    fun close(restoreFocus: Boolean) {
        expanded = false
        restoreFieldFocus = restoreFocus
    }
}

internal fun formDropdownSelectedOptionIndex(
    menuOptions: List<String>,
    value: String
): Int = menuOptions.indexOf(value).takeIf { it >= 0 } ?: 0

@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    maxLines: Int = 5,
    tvNavigation: TvTextFieldNavigation = TvTextFieldNavigation(),
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = false,
) {
    val isTelevision = isTelevisionDevice()
    val tvFieldState = if (isTelevision) {
        rememberTvTextFieldState(tvNavigation.focusRequester)
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .tvAwareTextFieldFocus(
                state = tvFieldState,
                enabled = enabled,
                navigation = tvNavigation,
                onActivate = { tvFieldState?.beginEditing() }
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            spec = OutlinedTextFieldSpec(
                label = label,
                placeholder = placeholder,
                supportingText = supportingText,
                isError = isError,
                enabled = enabled,
                maxLines = maxLines,
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
            ),
            tvFieldState = tvFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .then(tvFieldState?.let { Modifier.tvTextFieldEditorFocus(it) } ?: Modifier)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null
) {
    val dropdownState = remember { FormDropdownState() }
    val menuScrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTelevision = isTelevisionDevice()
    val fieldFocusRequester = if (isTelevision) remember { FocusRequester() } else null
    val fieldInteractionSource = if (isTelevision) remember { MutableInteractionSource() } else null
    val selectedOptionFocusRequester = if (isTelevision) remember { FocusRequester() } else null
    val selectedOptionIndex = formDropdownSelectedOptionIndex(options, value)

    fun dismissMenu() {
        dropdownState.close(restoreFocus = isTelevision)
    }

    fun activateTvField() {
        keyboardController?.hide()
        if (dropdownState.expanded) dismissMenu() else dropdownState.open()
    }

    LaunchedEffect(dropdownState.expanded, dropdownState.restoreFieldFocus, selectedOptionIndex) {
        when {
            dropdownState.expanded &&
                options.isNotEmpty() &&
                selectedOptionFocusRequester != null ->
                requestFocusWhenReady(selectedOptionFocusRequester)

            !dropdownState.expanded &&
                dropdownState.restoreFieldFocus &&
                fieldFocusRequester != null -> {
                requestFocusWhenReady(fieldFocusRequester)
                dropdownState.restoreFieldFocus = false
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = dropdownState.expanded,
        onExpandedChange = { newExpanded ->
            if (!enabled) return@ExposedDropdownMenuBox
            if (newExpanded) keyboardController?.hide()
            if (newExpanded) dropdownState.open() else dismissMenu()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (fieldFocusRequester != null && fieldInteractionSource != null) {
                    Modifier.tvPassiveTextFieldFocus(fieldFocusRequester, fieldInteractionSource, enabled, ::activateTvField)
                } else Modifier
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            spec = OutlinedTextFieldSpec(
                label = label,
                placeholder = placeholder,
                enabled = enabled,
                readOnly = true
            ),
            tvFieldState = null,
            interactionSource = fieldInteractionSource,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownState.expanded)
            },
            modifier = Modifier
                .then(if (isTelevision) Modifier.focusProperties { canFocus = false } else Modifier)
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) keyboardController?.hide()
                }
        )
        ExposedDropdownMenu(
            expanded = dropdownState.expanded,
            onDismissRequest = ::dismissMenu,
            modifier = Modifier.verticalScrollbar(menuScrollState),
            scrollState = menuScrollState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEachIndexed { index, option ->
                AppDropdownMenuItem(
                    text = option,
                    modifier = if (index == selectedOptionIndex && selectedOptionFocusRequester != null) {
                        Modifier.focusRequester(selectedOptionFocusRequester)
                    } else {
                        Modifier
                    },
                    onClick = {
                        onValueChange(option)
                        dismissMenu()
                        if (!isTelevision) focusManager.clearFocus()
                    }
                )
            }
        }
    }
}
