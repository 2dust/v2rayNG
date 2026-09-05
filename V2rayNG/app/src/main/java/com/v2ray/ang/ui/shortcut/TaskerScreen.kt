package com.v2ray.ang.ui.shortcut

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.base.asString
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar

private const val CONTENT_TYPE_PROFILE = "tasker_profile"

private val RowHorizontalPad = 16.dp
private val RowVerticalPad = 12.dp
private val RadioTextGap = 8.dp

@Composable
fun TaskerScreen(
    viewModel: TaskerViewModel,
    onEvent: (BaseEvent) -> Boolean
) {
    val onAction = remember(viewModel) { viewModel::onAction }

    BackHandler { onAction(TaskerAction.Back) }

    BaseScreen(
        viewModel = viewModel,
        showLoading = false,
        onEvent = onEvent,
        topBar = {
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            TaskerTopBar(isLoading = isLoading, onAction = onAction)
        }
    ) { state, _ ->
        TaskerContent(state = state, onAction = onAction)
    }
}

@Composable
private fun TaskerTopBar(
    isLoading: Boolean,
    onAction: (TaskerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    AppTopBar(
        title = stringResource(R.string.app_name),
        onBackClick = { onAction(TaskerAction.Back) },
        modifier = modifier,
        isLoading = isLoading,
        actions = {
            IconButton(onClick = { onAction(TaskerAction.Save) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_fab_check),
                    contentDescription = stringResource(R.string.menu_item_save_config)
                )
            }
        }
    )
}

@Composable
private fun TaskerContent(
    state: TaskerUiState,
    onAction: (TaskerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val onToggle = remember(onAction) {
        { enabled: Boolean -> onAction(TaskerAction.ToggleStartService(enabled)) }
    }
    val onSelect = remember(onAction) {
        { guid: String -> onAction(TaskerAction.SelectProfile(guid)) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SettingsSwitchItem(
            title = stringResource(R.string.tasker_start_service),
            checked = state.startService,
            onCheckedChange = onToggle
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollbar(listState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            items(
                items = state.profiles,
                key = { profile -> profile.guid },
                contentType = { CONTENT_TYPE_PROFILE }
            ) { profile ->
                TaskerProfileRow(
                    profile = profile,
                    selected = profile.guid == state.selectedGuid,
                    onSelect = onSelect
                )
            }
        }
    }
}

@Composable
private fun TaskerProfileRow(
    profile: TaskerProfileItem,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(profile.guid) }
            )
            .padding(horizontal = RowHorizontalPad, vertical = RowVerticalPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.secondary
            )
        )
        Spacer(modifier = Modifier.width(RadioTextGap))
        Text(
            text = profile.name.asString(context),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ===== previews =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskerProfileRowPreview() = AppTheme {
    Column {
        TaskerProfileRow(
            profile = TaskerProfileItem("default", BaseText.of("Default (currently selected)")),
            selected = true,
            onSelect = {}
        )
        TaskerProfileRow(
            profile = TaskerProfileItem(
                guid = "guid-2",
                name = BaseText.of("A remarks string long enough to be truncated politely")
            ),
            selected = false,
            onSelect = {}
        )
    }
}
