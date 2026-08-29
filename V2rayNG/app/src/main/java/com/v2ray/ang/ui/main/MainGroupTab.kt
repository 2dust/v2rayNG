package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ServersCache
import kotlinx.coroutines.flow.StateFlow

@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = selectedTabIndex.coerceIn(0, groups.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    LaunchedEffect(selectedIndex) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    // Unlike ScrollableTabRow, a lazy list exposes scroll-to-index semantics. TalkBack can
    // therefore move past the last visible tab (or back before the first visible tab), and the
    // list brings the newly focused off-screen tab into view instead of leaving the tab strip.
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = groups,
            key = { _, group -> group.id },
        ) { index, group ->
            val serverFlow = remember(group.id, mainViewModel) {
                mainViewModel.serversForGroup(group.id)
            }
            GroupTabItem(
                group = group,
                selected = index == selectedIndex,
                serverFlow = serverFlow,
                onClick = { onTabClick(index) }
            )
        }
    }
}

@Composable
private fun GroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    serverFlow: StateFlow<List<ServersCache>>,
    onClick: () -> Unit
) {
    val servers by serverFlow.collectAsStateWithLifecycle()
    val accessibilityLabel = pluralStringResource(
        R.plurals.acc_group_tab,
        servers.size,
        group.remarks,
        servers.size,
    )
    val text = if (group.id.isEmpty()) {
        group.remarks
    } else {
        "${group.remarks} (${servers.size})"
    }

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 56.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                contentDescription = accessibilityLabel
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.semantics { hideFromAccessibility() },
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
    }
}
