package com.v2ray.ang.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import kotlinx.coroutines.launch

@Composable
internal fun TvDeleteConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    itemName: String? = null
) {
    val dismissFocusRequester = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(dismissFocusRequester) { requestFocusWhenReady(dismissFocusRequester) }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = if (itemName.isNullOrBlank()) message else "$message\n\n$itemName",
                modifier = Modifier.fillMaxWidth()
                    .dpadFocusOutline()
                    .focusProperties { canFocus = scroll.canScrollForward || scroll.canScrollBackward }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val step = scroll.viewportSize / 4f
                        val delta = when {
                            event.key == Key.DirectionUp && scroll.canScrollBackward -> -step
                            event.key == Key.DirectionDown && scroll.canScrollForward -> step
                            else -> return@onKeyEvent false
                        }
                        scope.launch { scroll.scrollBy(delta) }
                        true
                    }
                    .focusable()
                    .verticalScrollbar(scroll)
                    .verticalScroll(scroll),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            TvDialogButton(
                text = stringResource(R.string.action_delete),
                onClick = onConfirm,
                icon = { Icon(painterResource(R.drawable.ic_delete_24dp), null, Modifier.size(24.dp)) }
            )
        },
        dismissButton = {
            TvDialogButton(stringResource(R.string.action_cancel), onDismiss, focusRequester = dismissFocusRequester)
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun TvDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    val shape = RoundedCornerShape(24.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .widthIn(min = 96.dp)
            .dpadFocusOutline(focusRequester = focusRequester, cornerRadius = 24.dp),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun TvQRCodeDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    val closeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(closeFocusRequester) { requestFocusWhenReady(closeFocusRequester) }
    val closeText = stringResource(R.string.action_close)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.acc_qr_code),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        },
        confirmButton = {
            TvDialogButton(text = closeText, onClick = onDismiss, focusRequester = closeFocusRequester)
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
internal fun <T> TvActionDialog(
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(first) { if (options.isNotEmpty()) requestFocusWhenReady(first) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            LazyColumn(
                modifier = Modifier.dpadPopupHorizontalNavigation(onMovePrevious = onDismiss),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options.size) { index ->
                    val option = options[index]
                    TvDialogButton(
                        text = optionText(option),
                        onClick = { onSelected(option) },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = first.takeIf { index == 0 }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TvDialogButton(stringResource(R.string.action_cancel), onDismiss) },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
