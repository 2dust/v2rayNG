package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.verticalScrollbar

private val DrawerWidthFraction = 0.75f
private val DrawerHeaderHeight = 180.dp
private val LogoSize = 120.dp

@Immutable
private data class DrawerItem(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
    val action: MainAction
)

private val primaryMenu = listOf(
    DrawerItem(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting, MainAction.Navigate(AppRoute.SubSetting)),
    DrawerItem(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings, MainAction.Navigate(AppRoute.PerAppProxy)),
    DrawerItem(R.drawable.ic_routing_24dp, R.string.routing_settings_title, MainAction.Navigate(AppRoute.RoutingSetting)),
    DrawerItem(R.drawable.ic_file_24dp, R.string.title_user_asset_setting, MainAction.Navigate(AppRoute.UserAsset)),
    DrawerItem(R.drawable.ic_settings_24dp, R.string.title_settings, MainAction.Navigate(AppRoute.Settings))
)

private val secondaryMenu = listOf(
    DrawerItem(R.drawable.ic_promotion_24dp, R.string.title_pref_promotion, MainAction.OpenPromotion),
    DrawerItem(R.drawable.ic_logcat_24dp, R.string.title_logcat, MainAction.Navigate(AppRoute.Logcat)),
    DrawerItem(R.drawable.ic_check_update_24dp, R.string.update_check_for_update, MainAction.Navigate(AppRoute.CheckUpdate)),
    DrawerItem(R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore, MainAction.Navigate(AppRoute.Backup)),
    DrawerItem(R.drawable.ic_about_24dp, R.string.title_about, MainAction.Navigate(AppRoute.About))
)

@Composable
fun MainDrawerContent(
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    ModalDrawerSheet(
        modifier = modifier.fillMaxWidth(DrawerWidthFraction),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .verticalScrollbar(scrollState)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DrawerHeaderHeight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val isDarkTheme = LocalDarkTheme.current
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(LogoSize),
                        colorFilter = if (isDarkTheme) {
                            ColorFilter.tint(Color.White, BlendMode.SrcIn)
                        } else {
                            null
                        }
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            primaryMenu.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(stringResource(item.labelRes)) },
                    selected = false,
                    onClick = { onAction(item.action) },
                    icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            AppDivider()

            secondaryMenu.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(stringResource(item.labelRes)) },
                    selected = false,
                    onClick = { onAction(item.action) },
                    icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    }
}
