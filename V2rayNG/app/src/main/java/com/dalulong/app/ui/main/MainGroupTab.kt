package com.dalulong.app.ui.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dalulong.app.compose.colorFabActive
import com.dalulong.app.dto.GroupMapItem
import com.dalulong.app.dto.entities.ServersCache
import kotlinx.coroutines.flow.StateFlow

@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex.coerceIn(0, groups.lastIndex),
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 16.dp,
        minTabWidth = 56.dp,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier
                    .tabIndicatorOffset(
                        selectedTabIndex = selectedTabIndex.coerceIn(0, groups.lastIndex),
                        matchContentSize = true
                    )
                    .clip(RoundedCornerShape(3.dp)),
                width = Dp.Unspecified,
                color = colorFabActive
            )
        },
        divider = {}
    ) {
        groups.forEachIndexed { index, group ->
            GroupTabItem(
                group = group,
                selected = index == selectedTabIndex,
                serverFlowProvider = { mainViewModel.serversForGroup(group.id) },
                onClick = { onTabClick(index) }
            )
        }
    }
}

@Composable
private fun GroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    serverFlowProvider: () -> StateFlow<List<ServersCache>>,
    onClick: () -> Unit
) {
    val serverFlow = remember(group.id) { serverFlowProvider() }
    val servers by serverFlow.collectAsStateWithLifecycle()
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            val text = if (group.id.isEmpty()) {
                group.remarks
            } else {
                "${group.remarks} (${servers.size})"
            }
            Text(
                text = text,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
