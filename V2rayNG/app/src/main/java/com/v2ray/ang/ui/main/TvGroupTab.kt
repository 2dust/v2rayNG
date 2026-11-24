package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation

@Composable
internal fun TvGroupTabBar(
    groups: List<GroupMapItem>,
    mainViewModel: MainViewModel,
    selectedIndex: Int,
    listState: LazyListState,
    tabFocusRequesters: List<FocusRequester>,
    requestFocus: (FocusRequester) -> Boolean,
    onOpenDrawer: (String) -> Unit,
    onMoveUp: () -> Boolean,
    onSelectGroup: (String) -> Unit
) {
    LaunchedEffect(selectedIndex) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(items = groups, key = { _, group -> group.id }) { index, group ->
            val focusRequester = tabFocusRequesters[index]
            val tabModifier = Modifier
                .dpadFocusOutline(focusRequester = focusRequester, cornerRadius = 20.dp)
                .onFocusChanged { if (it.isFocused && index != selectedIndex) onSelectGroup(group.id) }
                .dpadOrderedFocusNavigation(
                    current = focusRequester,
                    order = tabFocusRequesters,
                    onBeforeFirst = { onOpenDrawer(group.id) },
                    requestFocus = requestFocus
                )
                .dpadVerticalFocusNavigation(onMoveUp = onMoveUp, onMoveDown = { false })
            val servers by remember(group.id, mainViewModel) { mainViewModel.serversForGroup(group.id) }.collectAsStateWithLifecycle()
            TvGroupTabItem(
                group = group,
                count = servers.size,
                selected = index == selectedIndex,
                modifier = tabModifier,
                onClick = { onSelectGroup(group.id) }
            )
        }
    }
}

@Composable
private fun TvGroupTabItem(
    group: GroupMapItem, count: Int, selected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val text = if (group.id.isEmpty()) stringResource(R.string.filter_config_all)
    else "${group.remarks} (${count})"

    val indicatorColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.widthIn(min = 56.dp).drawWithContent {
        drawContent()
        val thickness = 3.dp.toPx()
        drawRoundRect(indicatorColor, Offset(0f, size.height - thickness), Size(size.width, thickness), CornerRadius(thickness))
    }, propagateMinConstraints = true) {
        Tab(
            selected = selected,
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            text = { Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
        )
    }
}
