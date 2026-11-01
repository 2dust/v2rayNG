package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import kotlin.math.roundToInt

/** What a long-pressed tab looked like and where it was, so the menu can lift it above the dimmed screen. */
internal data class TabMenuRequest(
    val group: GroupMapItem,
    val bounds: Rect,
    val selected: Boolean,
    val count: Int,
)

private val MENU_GAP = 8.dp
private val MENU_SCREEN_MARGIN = 8.dp
private val MENU_MIN_WIDTH = 200.dp
private val MENU_ITEM_HEIGHT = 48.dp

/**
 * Context menu of a subscription tab, in the style of Telegram's folder menu: the screen is dimmed
 * (by the dialog window), the pressed tab stays lit as a pill at its original position, and a
 * rounded card with icon rows opens right below it, aligned with the tab's start edge.
 */
@Composable
internal fun GroupTabContextMenu(
    request: TabMenuRequest,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val gapPx = with(density) { MENU_GAP.roundToPx() }
    val marginPx = with(density) { MENU_SCREEN_MARGIN.roundToPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Layout(
            modifier = Modifier
                .fillMaxSize()
                // The whole dialog area is the "outside": a tap anywhere that is not the menu closes it.
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            content = {
                LiftedTab(request)
                MenuCard(onEdit = onEdit, onDelete = onDelete)
            }
        ) { measurables, constraints ->
            val screenWidth = constraints.maxWidth
            val screenHeight = constraints.maxHeight
            val tabWidth = request.bounds.width.roundToInt()
            val tabHeight = request.bounds.height.roundToInt()
            val tab = measurables[0].measure(Constraints.fixed(tabWidth, tabHeight))
            val card = measurables[1].measure(
                Constraints(maxWidth = (screenWidth - 2 * marginPx).coerceAtLeast(0))
            )

            val tabLeft = request.bounds.left.roundToInt()
            val tabTop = request.bounds.top.roundToInt()
            // Align the card with the tab's start edge, then keep it fully on screen.
            val preferredX = if (layoutDirection == LayoutDirection.Ltr) tabLeft else tabLeft + tabWidth - card.width
            val x = preferredX.coerceIn(marginPx, (screenWidth - card.width - marginPx).coerceAtLeast(marginPx))
            val y = (tabTop + tabHeight + gapPx).coerceAtMost((screenHeight - card.height - marginPx).coerceAtLeast(0))

            layout(screenWidth, screenHeight) {
                tab.place(tabLeft, tabTop)
                card.place(x, y)
            }
        }
    }
}

/** A copy of the pressed tab drawn on a solid pill so it stands out from the dimmed screen. */
@Composable
private fun LiftedTab(request: TabMenuRequest) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(groupTabBarColor())
            .background(if (request.selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupTabContent(
            remarks = request.group.remarks,
            count = request.count,
            selected = request.selected
        )
    }
}

@Composable
private fun MenuCard(onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = groupTabBarColor(),
        shadowElevation = 8.dp
    ) {
        Column(
            Modifier
                .defaultMinSize(minWidth = MENU_MIN_WIDTH)
                .width(IntrinsicSize.Max)
                .padding(vertical = 6.dp)
        ) {
            MenuRow(
                iconRes = R.drawable.ic_edit_24dp,
                text = stringResource(R.string.action_edit),
                color = colors.onSurface,
                onClick = onEdit
            )
            MenuRow(
                iconRes = R.drawable.ic_delete_24dp,
                text = stringResource(R.string.action_delete),
                color = colors.error,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun MenuRow(iconRes: Int, text: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_ITEM_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
