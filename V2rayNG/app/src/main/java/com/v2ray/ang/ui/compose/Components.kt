package com.v2ray.ang.ui.compose

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.v2ray.ang.R
import com.v2ray.ang.util.AppIconFetcher
import sh.calvin.reorderable.ReorderableCollectionItemScope

private val DividerThickness = 1.dp
private val DividerInset = 12.dp
private val AppIconSize = 40.dp
private val IconSize = 24.dp
private val ItemHorizontalPad = 16.dp
private val ItemVerticalPad = 12.dp
private val DragElevation = 4.dp

@Immutable
data class AppSearchState(
    val isActive: Boolean = false,
    val query: String = "",
    val placeholder: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    progress: Float? = null,
    searchState: AppSearchState? = null,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isSearchActive = searchState?.isActive == true

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                if (isSearchActive) {
                    SearchInputField(
                        query = searchState?.query ?: "",
                        onQueryChange = onSearchQueryChange,
                        placeholder = searchState?.placeholder
                    )
                } else {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                if (navigationIcon != null) {
                    navigationIcon()
                } else {
                    IconButton(onClick = if (isSearchActive) onSearchClose else onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.acc_back)
                        )
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            if (progress == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String?
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = appFieldColors(borderless = true),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear_24dp),
                    contentDescription = stringResource(R.string.action_close)
                )
            }
        }
    }
}

@Composable
fun AppListItem(
    appName: String,
    packageName: String,
    icon: Any?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val model = remember(icon, packageName, context) {
        icon ?: ImageRequest.Builder(context)
            .data("appicon:$packageName")
            .fetcherFactory(AppIconFetcher.Factory(context))
            .build()
    }
    val placeholder = painterResource(R.drawable.ic_image_24dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox
            )
            .padding(horizontal = ItemHorizontalPad, vertical = ItemVerticalPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.size(AppIconSize),
            contentScale = ContentScale.Fit,
            error = placeholder,
            fallback = placeholder
        )
        Spacer(modifier = Modifier.width(ItemHorizontalPad))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary)
        )
    }
}

@Composable
fun ItemDivider(modifier: Modifier = Modifier) {
    AppDivider(modifier = modifier.padding(horizontal = DividerInset))
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = DividerThickness,
        color = LocalAppColors.current.divider
    )
}

@Composable
fun VersionInfoBlock(
    versionText: String,
    appIdText: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ItemHorizontalPad),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = versionText, style = MaterialTheme.typography.bodySmall)
        if (appIdText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = appIdText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.dragHandle(): Modifier {
    val haptics = LocalHapticFeedback.current
    return Modifier.longPressDraggableHandle(
        onDragStarted = {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    )
}

@Composable
fun ReorderableListItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val elevation by animateDpAsState(
        targetValue = if (isDragging) DragElevation else 0.dp,
        label = "ReorderableElevation"
    )
    Surface(modifier = modifier.fillMaxWidth(), shadowElevation = elevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(with(scope) { dragHandle() }),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun ReorderableGridItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val elevation by animateDpAsState(
        targetValue = if (isDragging) DragElevation else 0.dp,
        label = "ReorderableElevation"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(with(scope) { dragHandle() }),
        shadowElevation = elevation
    ) {
        content()
    }
}

@Composable
fun NavigationBarsSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
}

@Composable
fun NavigationBarsBottomPadding(extra: Dp = 0.dp): PaddingValues {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(bottom = bottom + extra)
}

// ===== previews =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppTopBarPreview() = AppTheme {
    Column {
        AppTopBar(title = "Settings", onBackClick = {})
        AppTopBar(title = "Loading", onBackClick = {}, isLoading = true)
        AppTopBar(
            title = "A title long enough that it has to be truncated at the end",
            onBackClick = {},
            progress = 0.4f,
            isLoading = true
        )
        AppTopBar(
            title = "Searchable",
            onBackClick = {},
            searchState = AppSearchState(isActive = true, query = "v2ray"),
            onSearchQueryChange = {},
            onSearchClose = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppListItemPreview() = AppTheme {
    Column {
        AppListItem(
            appName = "A very long application name that must be ellipsized",
            packageName = "com.example.some.very.long.package.name",
            icon = null,
            checked = true,
            onCheckedChange = {}
        )
        AppDivider()
        VersionInfoBlock(versionText = "1.0.0 (100)", appIdText = "com.v2ray.ang")
    }
}
