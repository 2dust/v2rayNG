package com.v2ray.ang.ui.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.v2ray.ang.dto.GroupMapItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.yield
import kotlin.math.abs

internal class ProfileScrollState {
    val list = LazyListState()
    val grid = LazyGridState()

    suspend fun reveal(index: Int, doubleColumn: Boolean) {
        if (doubleColumn) grid.scrollToItem(index, -grid.layoutInfo.viewportSize.height / 3)
        else list.scrollToItem(index, -list.layoutInfo.viewportSize.height / 3)
    }
}

@Composable
internal fun rememberMainGroupScrollStates(groupIds: List<String>): MutableMap<String, ProfileScrollState> {
    val states = remember { mutableMapOf<String, ProfileScrollState>() }
    LaunchedEffect(groupIds) { states.keys.retainAll(groupIds.toSet()) }
    return states
}

@Composable
internal fun rememberProfileScrollState(states: MutableMap<String, ProfileScrollState>, groupId: String): ProfileScrollState =
    remember(states, groupId) { states.getOrPut(groupId) { ProfileScrollState() } }

@Stable
private class MainPagerCoordinator(val pagerState: PagerState) {
    var programmaticTargetPage by mutableStateOf<Int?>(null)
        private set
    private var navigationGeneration = 0L

    internal fun beginProgrammaticNavigation(targetPage: Int): Long {
        navigationGeneration += 1
        programmaticTargetPage = targetPage
        return navigationGeneration
    }

    internal fun finishProgrammaticNavigation(generation: Long) {
        if (navigationGeneration == generation) programmaticTargetPage = null
    }
}

internal fun mainSelectedGroupIndex(groups: List<GroupMapItem>, selectedGroupId: String): Int =
    groups.indexOfFirst { it.id == selectedGroupId }.takeIf { it >= 0 } ?: 0

@Composable
internal fun rememberMainPhonePagerState(
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    onSelectGroup: (String) -> Unit
): PagerState {
    val latestPageCount by rememberUpdatedState(groups.size.coerceAtLeast(1))
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { latestPageCount })
    val coordinator = remember(pagerState) { MainPagerCoordinator(pagerState) }
    MainPagerCoordinatorEffects(coordinator, groups, selectedGroupId, onSelectGroup)
    return pagerState
}

@Composable
private fun MainPagerCoordinatorEffects(
    coordinator: MainPagerCoordinator,
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    onSelectGroup: (String) -> Unit
) {
    val groupIds = groups.map { it.id }
    val latestGroups by rememberUpdatedState(groups)
    val latestSelectedGroupId by rememberUpdatedState(selectedGroupId)
    val latestOnSelectGroup by rememberUpdatedState(onSelectGroup)

    LaunchedEffect(groupIds, selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val targetPage = mainSelectedGroupIndex(groups, selectedGroupId)
        if (coordinator.pagerState.settledPage == targetPage) return@LaunchedEffect
        val generation = coordinator.beginProgrammaticNavigation(targetPage)
        try {
            coordinator.pagerState.navigateToPageOptimized(targetPage)
        } finally {
            coordinator.finishProgrammaticNavigation(generation)
        }
    }

    // Only a settled, user-driven phone swipe may publish pager state as selection.
    LaunchedEffect(coordinator.pagerState) {
        snapshotFlow { coordinator.pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { settledPage ->
                val currentGroups = latestGroups
                if (settledPage !in currentGroups.indices) return@collect
                val selectedPage = mainSelectedGroupIndex(currentGroups, latestSelectedGroupId)
                if (coordinator.programmaticTargetPage == null && settledPage != selectedPage) {
                    latestOnSelectGroup(currentGroups[settledPage].id)
                }
            }
    }
}

private suspend fun PagerState.navigateToPageOptimized(targetPage: Int) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return
    if (abs(target - current) > 1) {
        val adjacent = if (target > current) target - 1 else target + 1
        scrollToPage(adjacent)
        yield()
    }
    animateScrollToPage(target)
}
