package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.role
import com.v2ray.ang.ui.compose.AppBackdrop
import com.v2ray.ang.ui.compose.PrimaryGradient
import com.v2ray.ang.ui.compose.liquidGlass
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.Utils
import com.v2ray.ang.ui.compose.verticalScrollbar

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    // Xfinder tabs, shown first in the drawer.
    XfinderConfigs(R.drawable.ic_cloud_download_24dp, R.string.xf_nav_configs),
    IpScanner(R.drawable.ic_search_24dp, R.string.xf_nav_scanner),
    XfinderLogs(R.drawable.ic_description_24dp, R.string.xf_nav_logs),
    Subscriptions(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
    PerAppProxy(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
    Routing(R.drawable.ic_routing_24dp, R.string.routing_settings_title),
    UserAssets(R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
    Settings(R.drawable.ic_settings_24dp, R.string.xf_nav_settings),
    Language(R.drawable.ic_translate_24dp, R.string.mx_nav_language),
    Logcat(R.drawable.ic_logcat_24dp, R.string.title_logcat),
    CheckUpdate(R.drawable.ic_check_update_24dp, R.string.update_check_for_update),
    BackupRestore(R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
    About(R.drawable.ic_about_24dp, R.string.title_about)
}

private val xfinderDrawerItems = listOf(
    MainDestination.XfinderConfigs,
    MainDestination.IpScanner,
    MainDestination.XfinderLogs,
    MainDestination.Settings,
    MainDestination.Language
)

private val primaryDrawerItems = xfinderDrawerItems + listOf(
    MainDestination.Subscriptions,
    MainDestination.PerAppProxy,
    MainDestination.Routing,
    MainDestination.UserAssets
)

private val drawerItems = primaryDrawerItems + listOf(
    MainDestination.Logcat,
    MainDestination.CheckUpdate,
    MainDestination.BackupRestore,
    MainDestination.About
)

@Composable
fun MainDrawerContent(drawerState: DrawerState, onNavigate: (MainDestination) -> Unit) {
    val drawerScrollState = rememberScrollState()

    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.fillMaxWidth(0.82f),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.fillMaxSize()) {
            AppBackdrop()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(drawerScrollState)
                    .verticalScrollbar(drawerScrollState)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                DrawerHeader()
                drawerItems.forEachIndexed { index, item ->
                    if (index == primaryDrawerItems.size || index == xfinderDrawerItems.size) {
                        Spacer(Modifier.height(10.dp))
                    }
                    DrawerGlassItem(item = item, onClick = { onNavigate(item) })
                }
                DrawerTelegramLink()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Full-color logo: tinting the artwork would flatten it into a single-color disc.
        Image(
            painter = painterResource(R.drawable.ic_matixray_logo),
            contentDescription = null,
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** One menu entry: a liquid-glass card with a blue icon tile and the label; one focusable node. */
@Composable
private fun DrawerGlassItem(item: MainDestination, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .liquidGlass(shape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(PrimaryGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Footer of the drawer: the channel's full address (t.me/...), a tap opens it. */
@Composable
private fun DrawerTelegramLink() {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.mx_telegram_channel),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button) { Utils.openUri(context, AppConfig.TG_CHANNEL_URL) }
            .padding(vertical = 10.dp)
    )
}
