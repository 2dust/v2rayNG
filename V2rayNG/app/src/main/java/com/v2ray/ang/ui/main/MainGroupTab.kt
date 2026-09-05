package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        edgePadding = 16.dp,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    ) {
        groups.forEachIndexed { index, group ->
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
    val text = if (group.id.isEmpty()) {
        group.remarks
    } else {
        "${group.remarks} (${servers.size})"
    }

    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 56.dp)
            .heightIn(min = 48.dp),
        text = {
            Text(
                text = text,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
