package com.v2ray.ang.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import sh.calvin.reorderable.ReorderableCollectionItemScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBackClick: () -> Unit,
    isLoading: Boolean = false,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    searchPlaceholder: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column {
        TopAppBar(
            title = {
                if (isSearchActive) {
                    SearchInputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = searchPlaceholder
                    )
                } else {
                    Text(text = title)
                }
            },
            navigationIcon = {
                if (navigationIcon != null) {
                    navigationIcon()
                } else {
                    IconButton(onClick = if (isSearchActive) onSearchClose else onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = "Back"
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
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colorFabActive)
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
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
            placeholder = { if (placeholder != null) Text(placeholder, style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)) },
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
            modifier = Modifier.weight(1f).focusRequester(focusRequester)
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(painterResource(android.R.drawable.ic_menu_close_clear_cancel), "Clear")
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            when (icon) {
                is Bitmap -> {
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                is Int -> {
                    Image(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                is Drawable -> {
                    val bitmap = remember(icon) { createBitmapFromDrawable(icon) }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Box(modifier = Modifier.size(40.dp))
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colorFabActive)
        )
    }
}

private fun createBitmapFromDrawable(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    val color = if (LocalDarkTheme.current) dividerColorDark else dividerColorLight
    HorizontalDivider(modifier = modifier.fillMaxWidth(), thickness = 1.dp, color = color)
}

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    hasBottomBar: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentWindowInsets = if (hasBottomBar) {
            // bottomBar handles navigationBars itself, so only top + IME are needed here
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            )
        } else {
            WindowInsets.systemBars
        },
        content = content
    )
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

@Composable
private fun reorderableElevation(isDragging: Boolean) = animateDpAsState(
    targetValue = if (isDragging) 4.dp else 0.dp,
    label = "ReorderableElevation"
)

@Composable
fun ReorderableListItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val elevation by reorderableElevation(isDragging)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(with(scope) { Modifier.longPressDraggableHandle() }),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun ReorderableGridItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    content: @Composable () -> Unit
) {
    val elevation by reorderableElevation(isDragging)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(with(scope) { Modifier.longPressDraggableHandle() }),
        shadowElevation = elevation
    ) {
        content()
    }
}
