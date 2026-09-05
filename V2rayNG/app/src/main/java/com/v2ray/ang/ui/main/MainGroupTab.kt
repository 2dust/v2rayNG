package com.v2ray.ang.ui.main

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import kotlinx.coroutines.flow.StateFlow

private val TabRowEdgePadding = 16.dp
private val TabMinWidth = 56.dp
private val TabIndicatorCorner = 3.dp
private const val TabRowContainerAlpha = 0.95f

@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    counts: (String) -> StateFlow<Int>,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeIndex = selectedTabIndex.coerceIn(0, groups.lastIndex)
    PrimaryScrollableTabRow(
        selectedTabIndex = safeIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TabRowContainerAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = TabRowEdgePadding,
        minTabWidth = TabMinWidth,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier
                    .tabIndicatorOffset(selectedTabIndex = safeIndex, matchContentSize = true)
                    .clip(RoundedCornerShape(TabIndicatorCorner)),
                width = Dp.Unspecified,
                color = MaterialTheme.colorScheme.secondary,
            )
        },
        divider = {},
    ) {
        groups.forEachIndexed { index, group ->
            GroupTabItem(
                group = group,
                selected = index == safeIndex,
                counts = counts,
                onClick = { onTabClick(index) },
            )
        }
    }
}

@Composable
private fun GroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    counts: (String) -> StateFlow<Int>,
    onClick: () -> Unit,
) {
    val isAllGroup = group.id.isEmpty()
    val count by remember(group.id) { counts(group.id) }.collectAsStateWithLifecycle()
    val allTitle = stringResource(R.string.filter_config_all)
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                text = if (isAllGroup) allTitle else "${group.remarks} ($count)",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    )
}
