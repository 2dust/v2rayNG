package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

private const val TV_FOCUS_EXPANSION_DURATION_MILLIS = 100

@Composable
internal fun TvIconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    contentColor: Color? = null,
    contentDescription: String? = label
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val resolvedContentColor = contentColor ?: MaterialTheme.colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .background(containerColor, shape)
            .dpadFocusOutline(
                focusRequester = focusRequester,
                cornerRadius = 24.dp,
                focusContainerColor = containerColor.takeUnless { it == Color.Transparent }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                contentDescription?.let { description ->
                    Modifier.semantics(mergeDescendants = true) { this.contentDescription = description }
                } ?: Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = resolvedContentColor,
            modifier = Modifier.size(24.dp)
        )
        FocusedActionLabel(isFocused, label, resolvedContentColor)
    }
}

@Composable
private fun FocusedActionLabel(visible: Boolean, label: String, color: Color, gap: Dp = 8.dp) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(tween(TV_FOCUS_EXPANSION_DURATION_MILLIS)) + fadeIn(tween(TV_FOCUS_EXPANSION_DURATION_MILLIS)),
        exit = shrinkHorizontally(tween(TV_FOCUS_EXPANSION_DURATION_MILLIS)) + fadeOut(tween(TV_FOCUS_EXPANSION_DURATION_MILLIS))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(gap))
            Text(label, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
internal fun TvTopBarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    clearFocusRequester: FocusRequester,
    focusOrder: List<FocusRequester>,
    onMoveDown: () -> Boolean
) {
    val state = rememberTvTextFieldState(focusRequester)
    LaunchedEffect(state) { state.beginEditing() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).tvSearchFieldFocus(state, focusOrder, onMoveDown)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().tvTextFieldEditorFocus(state),
                readOnly = !state.isEditing,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                placeholder = {
                    Text(stringResource(R.string.menu_item_search), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                },
                interactionSource = state.interactionSource,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.secondary,
                    selectionColors = TextSelectionColors(
                        handleColor = MaterialTheme.colorScheme.secondary,
                        backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                    )
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
        }
        if (query.isNotEmpty()) {
            TvIconButton(
                icon = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                label = stringResource(R.string.action_clear),
                focusRequester = clearFocusRequester,
                modifier = Modifier.dpadTopBarFocusNavigation(clearFocusRequester, focusOrder, onMoveDown),
                onClick = {
                    onQueryChange("")
                    focusRequester.requestFocus()
                }
            )
        }
    }
}
