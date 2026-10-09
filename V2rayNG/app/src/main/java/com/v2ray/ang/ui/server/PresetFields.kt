package com.v2ray.ang.ui.server

import androidx.annotation.ArrayRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.FormTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The finalMask field of the server editors and of the exit-node on the Aether pages: JSON written by hand, or one of
 * the finalMasks the arrow lists, matched in whatever spacing or key order it is written.
 */
@Composable
internal fun FinalMaskField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    PresetField(
        label = label,
        setting = stringResource(R.string.preset_setting_final_mask),
        value = value,
        onValueChange = onValueChange,
        presetNames = R.array.final_mask_preset_names,
        presetValues = R.array.final_mask_preset_values,
        match = FieldPresets.Match.JSON,
        enabled = enabled
    )
}

/**
 * The cipherSuites field of a TLS profile: names joined by ':', written by hand or one of the lists the arrow offers,
 * matched name by name as Xray reads them, whatever the white space around each.
 */
@Composable
internal fun CipherSuitesField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    PresetField(
        label = label,
        setting = stringResource(R.string.preset_setting_cipher_suites),
        value = value,
        onValueChange = onValueChange,
        presetNames = R.array.cipher_suites_preset_names,
        presetValues = R.array.cipher_suites_preset_values,
        match = FieldPresets.Match.NAMES,
        enabled = true
    )
}

/**
 * A text field with an arrow that lists ready-made values by name, for a setting whose value can be too long to list.
 * A pick puts its value in the field, which stays free to edit, so what is saved is the pick as edited, and the name of
 * the value the field holds shows under it. Only the arrow opens the list, so that a tap in the text only moves the
 * cursor, and a pick asks first before it takes the place of a value of the user's own. [setting] names what the field
 * sets, on the arrow and in that question. The first pick, default, blanks the field, which leaves the setting at its
 * default; a blank field shows it as its pick.
 */
@Composable
private fun PresetField(
    label: String,
    setting: String,
    value: String,
    onValueChange: (String) -> Unit,
    @ArrayRes presetNames: Int,
    @ArrayRes presetValues: Int,
    match: FieldPresets.Match,
    enabled: Boolean,
) {
    val names = stringArrayResource(presetNames).toList()
    val values = stringArrayResource(presetValues).toList()
    val presets = remember(values, match) { FieldPresets(values, match) }
    // The text matched and the pick it holds, as FieldPresets.heldOf gives it: matched on Dispatchers.Default, as the
    // JSON of a finalMask is parsed for it, so it can trail the text by a keystroke. Null until the first match is in.
    var held by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(presets, value) {
        val text = value
        held = text to withContext(Dispatchers.Default) { presets.heldOf(text) }
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    // A pick, by index or FieldPresets.DEFAULT, that waits to be let take the place of a value of the user's own.
    var replacing by rememberSaveable { mutableStateOf<Int?>(null) }
    val defaultName = stringResource(R.string.preset_default)

    fun nameOf(pick: Int): String = if (pick == FieldPresets.DEFAULT) defaultName else names.getOrNull(pick).orEmpty()

    fun choose(pick: Int) {
        expanded = false
        // Until the text the field holds now is matched, a pick asks rather than lose a value of the user's own.
        if (FieldPresets.asksBeforeReplacing(value, held?.takeIf { it.first == value }?.second)) {
            replacing = pick
        } else {
            presets.textOf(pick)?.let(onValueChange)
        }
    }

    Box {
        FormTextField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            maxLines = 8,
            // A blank field holds the default pick at once, with nothing to parse.
            supportingText = (if (value.isBlank()) FieldPresets.DEFAULT else held?.second)
                ?.takeIf { it != FieldPresets.NONE }
                ?.let(::nameOf),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }, enabled = enabled) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more_24dp),
                        contentDescription = stringResource(R.string.acc_choose_preset, setting),
                        modifier = Modifier.rotate(if (expanded) 180f else 0f)
                    )
                }
            }
        )
        // Below the field, lined up with its edge inside the padding FormTextField gives it.
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            offset = DpOffset(16.dp, 0.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DropdownMenuItem(
                text = { Text(defaultName) },
                onClick = { choose(FieldPresets.DEFAULT) }
            )
            HorizontalDivider()
            names.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { choose(index) }
                )
            }
        }
    }
    replacing?.let { pick ->
        ConfirmDialog(
            title = stringResource(R.string.preset_replace_title, setting),
            message = stringResource(R.string.preset_replace_message, setting, nameOf(pick)),
            confirmText = stringResource(R.string.preset_action_replace),
            onConfirm = { presets.textOf(pick)?.let(onValueChange) },
            onDismiss = { replacing = null }
        )
    }
}
