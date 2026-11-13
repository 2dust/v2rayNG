package com.v2ray.ang.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState as TvDrawerState
import androidx.tv.material3.DrawerValue as TvDrawerValue
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.ListItemScale as TvListItemScale
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.ModalNavigationDrawer as TvModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.lightColorScheme as tvColorScheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.AppDivider
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Image
import com.v2ray.ang.ui.compose.dpadLogicalHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.verticalScrollbar

private val V2rayNgFontFamily = FontFamily(Font(R.font.montserrat_thin))
private val TvDrawerCollapsedWidth = 72.dp
private val TvDrawerExpandedWidth = 272.dp
private const val TV_DRAWER_WIDTH_ANIMATION_DURATION_MILLIS = 100

@Composable
internal fun AppBrandTitle(
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    Text(
        text = stringResource(R.string.app_name),
        modifier = modifier,
        style = style.copy(
            fontFamily = V2rayNgFontFamily,
            fontWeight = FontWeight.Thin
        ),
        textAlign = textAlign
    )
}

@Composable
fun TvMainNavigationDrawer(
    drawerState: TvDrawerState,
    focusGeneration: Int,
    onClose: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    content: @Composable () -> Unit
) {
    val mobileColors = MaterialTheme.colorScheme
    val tvColors = remember(mobileColors) { mobileColors.toTvColorScheme() }
    TvMaterialTheme(colorScheme = tvColors) {
        TvModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { drawerValue ->
                TvMainDrawerContent(drawerValue, focusGeneration, onClose, onNavigate)
            },
            content = {
                Box(modifier = Modifier.fillMaxSize().padding(start = TvDrawerCollapsedWidth)) { content() }
            }
        )
    }
}

@Composable
private fun NavigationDrawerScope.TvMainDrawerContent(
    drawerValue: TvDrawerValue,
    focusGeneration: Int,
    onClose: () -> Unit,
    onNavigate: (MainDestination) -> Unit
) {
    val focusRequesters = remember { List(drawerItems.size) { FocusRequester() } }
    val drawerScrollState = rememberScrollState()
    var focusedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(drawerValue, focusGeneration) {
        if (drawerValue == TvDrawerValue.Open) {
            requestFocusWhenReady(focusRequesters[focusedIndex], focusRequesters.first())
        }
    }

    val drawerWidth by animateDpAsState(
        targetValue = if (drawerValue == TvDrawerValue.Open) TvDrawerExpandedWidth else TvDrawerCollapsedWidth,
        animationSpec = tween(TV_DRAWER_WIDTH_ANIMATION_DURATION_MILLIS),
        label = "TV drawer width"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (drawerValue == TvDrawerValue.Open) 1f else 0f,
        animationSpec = tween(TV_DRAWER_WIDTH_ANIMATION_DURATION_MILLIS),
        label = "TV drawer labels"
    )
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(drawerWidth)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                // Only the envelope changes width. Keep icons anchored at logical Start and
                // constrain hit/semantic bounds to the visible drawer, without resizing the app.
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(TvDrawerExpandedWidth)
                .navigationBarsPadding()
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            if (drawerValue == TvDrawerValue.Open) {
                Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        val isDarkTheme = LocalDarkTheme.current
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            colorFilter = if (isDarkTheme) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null
                        )
                        AppBrandTitle(
                            style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.width(64.dp).height(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            drawerItems.forEachIndexed { index, item ->
                if (index == primaryDrawerItems.size) {
                    AppDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp))
                }
                TvListItem(
                    selected = false,
                    onClick = { onNavigate(item) },
                    headlineContent = {
                        TvText(stringResource(item.labelRes), modifier = Modifier.alpha(labelAlpha), maxLines = 1)
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            tint = if (drawerValue == TvDrawerValue.Open && focusedIndex == index) {
                                MaterialTheme.colorScheme.inverseOnSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    scale = TvListItemScale.None,
                    modifier = Modifier
                        .focusProperties { canFocus = drawerValue == TvDrawerValue.Open }
                        .focusRequester(focusRequesters[index])
                        .onFocusChanged { if (it.isFocused) focusedIndex = index }
                        .dpadLogicalHorizontalNavigation(onMovePrevious = {}, onMoveNext = onClose)
                        .dpadVerticalFocusNavigation(
                            onMoveUp = {
                                focusRequesters[(index - 1).coerceAtLeast(0)].requestFocus()
                                true
                            },
                            onMoveDown = {
                                focusRequesters[(index + 1).coerceAtMost(focusRequesters.lastIndex)].requestFocus()
                                true
                            }
                        )
                )
            }
        }
    }
}

private fun ColorScheme.toTvColorScheme() = tvColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = surfaceTint,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    border = outline,
    borderVariant = outlineVariant,
    scrim = scrim
)
