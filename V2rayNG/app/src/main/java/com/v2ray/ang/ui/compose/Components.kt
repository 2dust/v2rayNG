package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.v2ray.ang.R
import com.v2ray.ang.util.AppIconFetcher

private const val TV_FOCUS_EXPANSION_DURATION_MILLIS = 100

internal data class AppTopBarAction(
    val icon: Painter, val label: String, val onClick: () -> Unit = {}, val enabled: Boolean = true,
    val contentDescription: String = label, val key: Any = label,
    val menuActions: List<AppSelectionMenuAction> = emptyList(), val focusRequester: FocusRequester? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    title: String,
    onBackClick: () -> Unit,
    initialFocus: Boolean = true,
    isLoading: Boolean = false,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    searchPlaceholder: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    navigationFocusRequester: FocusRequester? = null,
    navigationIcon: @Composable ((FocusRequester) -> Unit)? = null,
    actionItems: List<AppTopBarAction> = emptyList(),
    onMoveDown: (() -> Boolean)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val defaultNavigationFocusRequester = rememberDpadFocusRequester(
        requestFocus = navigationFocusRequester == null && initialFocus && !isSearchActive,
        requestKey = isSearchActive
    )
    val resolvedNavigationFocusRequester =
        navigationFocusRequester ?: defaultNavigationFocusRequester
    val actionFocusRequesters = rememberDpadFocusTargets(actionItems.map { it.key }) { FocusRequester() }
    val resolvedSearchInputFocusRequester = remember { FocusRequester() }
    val resolvedSearchClearFocusRequester = remember { FocusRequester() }
    fun actionFocus(action: AppTopBarAction) = action.focusRequester ?: actionFocusRequesters.getValue(action.key)
    val resolvedActionFocusRequesters = actionItems.filter { it.enabled }.map(::actionFocus)
    val hasSearchQuery = searchQuery.isNotEmpty()
    val topBarFocusOrder = remember(
        resolvedNavigationFocusRequester, resolvedSearchInputFocusRequester,
        resolvedSearchClearFocusRequester, resolvedActionFocusRequesters,
        isSearchActive, hasSearchQuery
    ) {
        buildList {
            add(resolvedNavigationFocusRequester)
            if (isSearchActive) {
                add(resolvedSearchInputFocusRequester)
                if (hasSearchQuery) add(resolvedSearchClearFocusRequester)
            }
            addAll(resolvedActionFocusRequesters)
        }
    }
    val navigationModifier = Modifier.dpadTopBarFocusNavigation(
        resolvedNavigationFocusRequester,
        topBarFocusOrder,
        onMoveDown = { onMoveDown?.invoke() ?: false }
    )

    Column {
        TopAppBar(
            title = {
                if (isSearchActive) {
                    SearchInputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = searchPlaceholder,
                        focusRequester = resolvedSearchInputFocusRequester,
                        clearFocusRequester = resolvedSearchClearFocusRequester,
                        focusOrder = topBarFocusOrder,
                        onMoveDown = { onMoveDown?.invoke() ?: false }
                    )
                } else if (titleContent != null) {
                    titleContent()
                } else {
                    Text(text = title)
                }
            },
            navigationIcon = {
                if (navigationIcon != null) {
                    navigationIcon(resolvedNavigationFocusRequester)
                } else {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_arrow_back_24dp),
                        label = stringResource(R.string.action_back),
                        contentDescription = stringResource(R.string.acc_back),
                        onClick = if (isSearchActive) onSearchClose else onBackClick,
                        focusRequester = resolvedNavigationFocusRequester,
                        modifier = navigationModifier
                    )
                }
            },
            actions = {
                actionItems.forEach { action ->
                    key(action.key) {
                        var showMenu by remember { mutableStateOf(false) }
                        val focusRequester = actionFocus(action)
                        Box {
                            AppIconButton(
                                icon = action.icon,
                                label = action.label,
                                contentDescription = action.contentDescription,
                                onClick = { if (action.menuActions.isEmpty()) action.onClick() else showMenu = true },
                                enabled = action.enabled,
                                focusRequester = focusRequester,
                                modifier = Modifier.dpadTopBarFocusNavigation(
                                    focusRequester, topBarFocusOrder, onMoveDown = { onMoveDown?.invoke() ?: false }
                                )
                            )
                            if (action.menuActions.isNotEmpty()) {
                                val previous = adjacentDpadFocusTarget(focusRequester, topBarFocusOrder, DpadHorizontalDirection.Previous)
                                val next = adjacentDpadFocusTarget(focusRequester, topBarFocusOrder, DpadHorizontalDirection.Next)
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.dpadPopupHorizontalNavigation(
                                        onMovePrevious = { showMenu = false; previous?.requestFocus() },
                                        onMoveNext = next?.let { { showMenu = false; it.requestFocus() } }
                                    )
                                ) {
                                    action.menuActions.forEach { item ->
                                        AppDropdownMenuItem(item.label, onClick = { showMenu = false; item.onClick() })
                                    }
                                }
                            }
                        }
                    }
                }
                actions()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.tvSafeAreaPadding(horizontal = 48.dp, vertical = 12.dp)
        )
        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/**
 * Keeps the existing icon-only Material button on touch devices. On television devices the
 * same action reveals its label while focused, improving remote discoverability without
 * permanently consuming toolbar or row space.
 */
@Composable
fun AppIconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    contentColor: Color? = null,
    contentDescription: String? = label
) {
    if (!isTelevisionDevice()) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            if (contentColor == null) {
                Icon(painter = icon, contentDescription = contentDescription)
            } else {
                Icon(painter = icon, contentDescription = contentDescription, tint = contentColor)
            }
        }
        return
    }

    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val resolvedContentColor = contentColor ?: MaterialTheme.colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .background(containerColor, shape)
            .dpadFocusOutline(
                focusRequester = focusRequester,
                cornerRadius = 24.dp,
                focusContainerColor = containerColor.takeUnless { it == Color.Transparent }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                contentDescription?.let { description ->
                    Modifier.semantics(mergeDescendants = true) { this.contentDescription = description }
                } ?: Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = resolvedContentColor,
            modifier = Modifier.size(24.dp)
        )
        FocusedActionLabel(isFocused, label, resolvedContentColor)
    }
}

/**
 * A compact television row switch. The Material switch track is scaled from 32 dp to the
 * same 24 dp height as an action icon, while the containing control keeps a remote-friendly
 * 48 dp focus target. Its accessible label expands inside the focused outline.
 */
@Composable
fun AppRowSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    label: String,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(48.dp)
            .dpadFocusOutline(focusRequester = focusRequester, cornerRadius = 24.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .semantics(mergeDescendants = true) {
                contentDescription = label
            }
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.scale(0.75f).clearAndSetSemantics {},
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                checkedTrackColor = colorFabActive
            )
        )
        FocusedActionLabel(isFocused, label, MaterialTheme.colorScheme.onSurface, gap = 4.dp)
    }
}

@Composable
private fun FocusedActionLabel(visible: Boolean, label: String, color: Color, gap: Dp = 8.dp) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(tween(TV_FOCUS_EXPANSION_DURATION_MILLIS)) + fadeIn(tween(TV_FOCUS_EXPANSION_DURATION_MILLIS)),
        exit = shrinkHorizontally(tween(TV_FOCUS_EXPANSION_DURATION_MILLIS)) + fadeOut(tween(TV_FOCUS_EXPANSION_DURATION_MILLIS))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(gap))
            Text(label, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/**
 * A dropdown item with one click owner plus the shared TV-safe inset and focus outline.
 * Accepting plain text prevents callers from nesting another interactive control in the row.
 */
@Composable
fun AppDropdownMenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick, modifier = modifier.tvMenuItemFocus(), enabled = enabled)
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String?,
    focusRequester: FocusRequester,
    clearFocusRequester: FocusRequester,
    focusOrder: List<FocusRequester>,
    onMoveDown: () -> Boolean
) {
    val tvFieldState = if (isTelevisionDevice()) rememberTvTextFieldState(focusRequester) else null
    fun moveFocus(direction: DpadHorizontalDirection): Boolean {
        return (adjacentDpadFocusTarget(focusRequester, focusOrder, direction) ?: focusRequester).requestFocus()
    }
    LaunchedEffect(tvFieldState, focusRequester) {
        if (tvFieldState == null) requestFocusWhenReady(focusRequester) else tvFieldState.beginEditing()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .tvAwareTextFieldFocus(
                    state = tvFieldState,
                    enabled = true,
                    navigation = TvTextFieldNavigation(
                        focusRequester = focusRequester,
                        onMoveUp = { true },
                        onMoveDown = onMoveDown,
                        onMovePrevious = { moveFocus(DpadHorizontalDirection.Previous) },
                        onMoveNext = { moveFocus(DpadHorizontalDirection.Next) }
                    ),
                    onActivate = { tvFieldState?.beginEditing() }
                )
        ) {
            MaterialOutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                readOnly = tvFieldState != null && !tvFieldState.isEditing,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                placeholder = { if (placeholder != null) Text(placeholder, style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)) },
                interactionSource = tvFieldState?.interactionSource,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.secondary,
                    selectionColors = TextSelectionColors(
                        handleColor = MaterialTheme.colorScheme.secondary,
                        backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                    )
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(tvFieldState?.let { Modifier.tvTextFieldEditorFocus(it) }
                        ?: Modifier.focusRequester(focusRequester))
            )
        }
        if (query.isNotEmpty()) {
            AppIconButton(
                icon = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                label = stringResource(R.string.action_clear),
                focusRequester = clearFocusRequester,
                modifier = Modifier.dpadTopBarFocusNavigation(clearFocusRequester, focusOrder, onMoveDown),
                onClick = {
                    onQueryChange("")
                    focusRequester.requestFocus()
                }
            )
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
    focusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dpadFocusOutline(focusRequester = focusRequester)
            .semantics(mergeDescendants = true) {
                contentDescription = appName
                role = Role.Checkbox
                toggleableState = ToggleableState(checked)
            }
            .dpadClickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val model = remember(icon, packageName) {
            if (icon != null) {
                icon
            } else {
                val data = "appicon:$packageName"
                ImageRequest.Builder(context)
                    .data(data)
                    .fetcherFactory(AppIconFetcher.Factory(context))
                    .build()
            }
        }

        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Fit,
            error = painterResource(R.drawable.ic_image_24dp),
            fallback = painterResource(R.drawable.ic_image_24dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).clearAndSetSemantics {}) {
            Text(text = appName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
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
            modifier = Modifier.clearAndSetSemantics {},
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary)
        )
    }
}

@Composable
fun ItemDivider() {
    AppDivider(modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    val color = if (LocalDarkTheme.current) dividerColorDark else dividerColorLight
    HorizontalDivider(modifier = modifier.fillMaxWidth(), thickness = 1.dp, color = color)
}

@Composable
fun NavigationBarsSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
}

@Composable
fun NavigationBarsBottomPadding(): PaddingValues {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(bottom = bottom)
}

@Composable
fun VersionInfoBlock(
    versionText: String,
    appIdText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = versionText, style = MaterialTheme.typography.bodySmall)
        if (appIdText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = appIdText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
