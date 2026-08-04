package com.v2ray.ang.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.dto.LogFileInfo
import com.v2ray.ang.enums.PingType
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.LogFileManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.handler.MmkvManager.rememberMmkvString
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.PreferenceGroupHeader
import com.v2ray.ang.ui.compose.ResumePauseEffect
import com.v2ray.ang.ui.compose.SettingsCategoryItem
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsFileItem
import com.v2ray.ang.ui.compose.SettingsGroupCard
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.ui.main.GlassBarItem
import com.v2ray.ang.ui.main.LiquidGlassBar
import com.v2ray.ang.ui.logcat.LogFileActivity
import com.v2ray.ang.ui.logcat.LogcatActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : BaseComponentActivity() {

    companion object {
        /** Просьба главному экрану открыть шторку импорта: «+» нажали здесь. */
        const val EXTRA_OPEN_IMPORT = "open_import"
    }

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Системный слайд уводил бы вместе со страницей и нижнюю капсулу, а она
        // одинакова на обоих экранах: кроссфейд оставляет её визуально на месте
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.screen_fade_in, R.anim.screen_fade_out)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.screen_fade_in, R.anim.screen_fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.screen_fade_in, R.anim.screen_fade_out)
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.screen_fade_in, R.anim.screen_fade_out)
        }
    }

    @Composable
    override fun ScreenContent() {
        SettingsScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onModeHelpClicked = { Utils.openUri(this, AppConfig.APP_WIKI_MODE) },
            onImportClick = {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_OPEN_IMPORT, true))
                finish()
            }
        )
    }
}

/** Duration of the slide between the category list and a category screen. */
private const val SCREEN_TRANSITION_MS = 220

/**
 * A single settings category, opened as its own screen from the settings root.
 */
enum class SettingsCategory(
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int
) {
    UI(R.string.title_ui_settings, R.string.summary_settings_ui),
    MODE(R.string.title_mode_settings, R.string.summary_settings_mode),
    VPN_TUNNEL(R.string.title_vpn_settings, R.string.summary_settings_vpn),
    CORE(R.string.title_core_settings, R.string.summary_settings_core),
    MUX(R.string.title_mux_settings, R.string.summary_settings_mux),
    FRAGMENT(R.string.title_fragment_settings, R.string.summary_settings_fragment),
    OBSERVATORY(R.string.title_observatory_settings, R.string.summary_settings_observatory),
    LOGS(R.string.title_log_settings, R.string.summary_settings_logs),
    ADVANCED(R.string.title_advanced, R.string.summary_settings_advanced)
}

private data class SettingsSection(
    @StringRes val titleRes: Int,
    val categories: List<SettingsCategory>
)

private val settingsSections = listOf(
    SettingsSection(
        R.string.title_settings_section_general,
        listOf(SettingsCategory.UI, SettingsCategory.MODE)
    ),
    SettingsSection(
        R.string.title_settings_section_connection,
        listOf(SettingsCategory.VPN_TUNNEL, SettingsCategory.CORE)
    ),
    SettingsSection(
        R.string.title_settings_section_bypass,
        listOf(SettingsCategory.MUX, SettingsCategory.FRAGMENT, SettingsCategory.OBSERVATORY)
    ),
    SettingsSection(
        R.string.title_settings_section_other,
        listOf(SettingsCategory.LOGS, SettingsCategory.ADVANCED)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onModeHelpClicked: () -> Unit,
    onImportClick: () -> Unit = {}
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // Та же капсула, что на главной: пузырёк въезжает на шестерёнку при открытии экрана
    val backdrop = rememberGraphicsLayer()
    var barItem by remember { mutableStateOf(GlassBarItem.HOME) }
    LaunchedEffect(Unit) {
        // Даём кроссфейду улечься, чтобы переезд пузырька было видно целиком
        delay(140)
        barItem = GlassBarItem.SETTINGS
    }

    // Hoisted so the category list keeps its scroll position while a category is open
    val categoryListScrollState = rememberScrollState()

    // Saved as a name so the open category survives configuration changes
    var openCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val openCategory = openCategoryName?.let { name ->
        SettingsCategory.entries.firstOrNull { it.name == name }
    }

    BackHandler(enabled = openCategory != null) { openCategoryName = null }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        modifier = Modifier.drawWithContent {
            backdrop.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop)
        },
        topBar = {
            AppTopBar(
                title = stringResource(openCategory?.titleRes ?: R.string.title_settings),
                onBackClick = {
                    if (openCategory != null) openCategoryName = null else onBackClick()
                },
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = openCategory,
            transitionSpec = {
                // Opening a category slides forward, going back slides the other way
                val forward = initialState == null
                val offset = { width: Int -> if (forward) width else -width }

                (slideInHorizontally(animationSpec = tween(SCREEN_TRANSITION_MS)) { offset(it) } +
                        fadeIn(animationSpec = tween(SCREEN_TRANSITION_MS)))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = tween(SCREEN_TRANSITION_MS)) { -offset(it) / 4 } +
                                fadeOut(animationSpec = tween(SCREEN_TRANSITION_MS))
                    )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "settingsScreen"
        ) { category ->
            val modifier = Modifier.fillMaxSize()

            when (category) {
                null -> SettingsCategoryList(modifier, categoryListScrollState) {
                    openCategoryName = it.name
                }
                SettingsCategory.UI -> UiSettings(modifier)
                SettingsCategory.MODE -> ModeSettings(modifier, viewModel, onModeHelpClicked)
                SettingsCategory.VPN_TUNNEL -> VpnSettings(modifier)
                SettingsCategory.CORE -> CoreSettings(modifier)
                SettingsCategory.MUX -> MuxSettings(modifier)
                SettingsCategory.FRAGMENT -> FragmentSettings(modifier)
                SettingsCategory.OBSERVATORY -> ObservatorySettings(modifier, viewModel)
                SettingsCategory.LOGS -> LogSettings(modifier)
                SettingsCategory.ADVANCED -> AdvancedSettings(modifier)
            }
        }
    }

        LiquidGlassBar(
            backdrop = backdrop,
            selected = barItem,
            onSelect = { item ->
                when (item) {
                    GlassBarItem.HOME -> onBackClick()
                    GlassBarItem.SETTINGS -> Unit
                    GlassBarItem.ADD -> onImportClick()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        )
    }
}

/**
 * Scrollable container shared by the root list and every category screen.
 */
@Composable
private fun SettingsColumn(
    modifier: Modifier,
    scrollState: ScrollState = rememberScrollState(),
    grouped: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        if (grouped) {
            SettingsGroupCard { content() }
        } else {
            content()
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun SettingsCategoryList(
    modifier: Modifier,
    scrollState: ScrollState,
    onCategoryClick: (SettingsCategory) -> Unit
) {
    SettingsColumn(modifier, scrollState, grouped = false) {
        settingsSections.forEach { section ->
            PreferenceGroupHeader(title = stringResource(section.titleRes))
            SettingsGroupCard {
                section.categories.forEach { category ->
                    SettingsCategoryItem(
                        title = stringResource(category.titleRes),
                        summary = stringResource(category.summaryRes),
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UiSettings(modifier: Modifier) {
    var speedEnabled by rememberMmkvBool(AppConfig.PREF_SPEED_ENABLED, false)
    var confirmRemove by rememberMmkvBool(AppConfig.PREF_CONFIRM_REMOVE, false)
    var doubleColumnDisplay by rememberMmkvBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    var groupAllDisplay by rememberMmkvBool(AppConfig.PREF_GROUP_ALL_DISPLAY, false)
    var language by rememberMmkvString(AppConfig.PREF_LANGUAGE, "auto")
    var uiModeNight by rememberMmkvString(AppConfig.PREF_UI_MODE_NIGHT, "0")

    val languageEntries = stringArrayResource(R.array.language_select).toList()
    val languageValues = stringArrayResource(R.array.language_select_value).toList()
    val uiModeNightEntries = stringArrayResource(R.array.ui_mode_night).toList()
    val uiModeNightValues = stringArrayResource(R.array.ui_mode_night_value).toList()

    SettingsColumn(modifier) {
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_speed_enabled),
            summary = stringResource(R.string.summary_pref_speed_enabled),
            checked = speedEnabled,
            onCheckedChange = { speedEnabled = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_confirm_remove),
            summary = stringResource(R.string.summary_pref_confirm_remove),
            checked = confirmRemove,
            onCheckedChange = { confirmRemove = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_double_column_display),
            summary = stringResource(R.string.summary_pref_double_column_display),
            checked = doubleColumnDisplay,
            onCheckedChange = {
                doubleColumnDisplay = it
                SettingsChangeManager.makeSetupGroupTab()
            }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_group_all_display),
            summary = stringResource(R.string.summary_pref_group_all_display),
            checked = groupAllDisplay,
            onCheckedChange = {
                groupAllDisplay = it
                SettingsChangeManager.makeSetupGroupTab()
            }
        )
        SettingsListItem(
            title = stringResource(R.string.title_language),
            entries = languageEntries,
            values = languageValues,
            selectedValue = language,
            onSelected = { language = it }
        )
        SettingsListItem(
            title = stringResource(R.string.title_pref_ui_mode_night),
            entries = uiModeNightEntries,
            values = uiModeNightValues,
            selectedValue = uiModeNight,
            onSelected = {
                uiModeNight = it
                ThemeManager.setThemeMode(it)
            }
        )
    }
}

@Composable
private fun ModeSettings(
    modifier: Modifier,
    viewModel: SettingsViewModel,
    onModeHelpClicked: () -> Unit
) {
    var mode by rememberMmkvString(AppConfig.PREF_MODE, VPN)
    var enableRootMode by rememberMmkvBool(AppConfig.PREF_ROOT_MODE_ENABLE, false)
    var lanSharing by rememberMmkvBool(AppConfig.PREF_ROOT_LAN_SHARING, false)

    val modeEntries = stringArrayResource(R.array.mode_entries).toList()
    val modeValues = stringArrayResource(R.array.mode_value).toList()

    SettingsColumn(modifier) {
        SettingsListItem(
            title = stringResource(R.string.title_mode),
            entries = modeEntries,
            values = modeValues,
            selectedValue = mode,
            onSelected = { mode = it }
        )
        SettingsMenuItem(
            title = stringResource(R.string.title_mode_help),
            onClick = onModeHelpClicked
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_root_mode_enabled),
            summary = stringResource(R.string.summary_root_mode_enabled),
            checked = enableRootMode,
            onCheckedChange = { newValue ->
                if (newValue && !RootManager.cachedRoot()) {
                    viewModel.checkAndRequestRoot {
                        enableRootMode = true
                    }
                } else {
                    enableRootMode = newValue
                }
            }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_root_lan_sharing),
            summary = stringResource(R.string.summary_root_lan_sharing),
            checked = lanSharing,
            onCheckedChange = { newValue ->
                if (newValue && !RootManager.cachedRoot()) {
                    viewModel.checkAndRequestRoot {
                        lanSharing = true
                    }
                } else {
                    lanSharing = newValue
                }
            }
        )
    }
}

@Composable
private fun VpnSettings(modifier: Modifier) {
    var ipv6Enabled by rememberMmkvBool(AppConfig.PREF_IPV6_ENABLED, false)
    var preferIpv6 by rememberMmkvBool(AppConfig.PREF_PREFER_IPV6, false)
    var localDns by rememberMmkvBool(AppConfig.PREF_LOCAL_DNS_ENABLED, false)
    var fakeDns by rememberMmkvBool(AppConfig.PREF_FAKE_DNS_ENABLED, false)
    var vpnDns by rememberMmkvString(AppConfig.PREF_VPN_DNS, "")
    var appendHttpProxy by rememberMmkvBool(AppConfig.PREF_APPEND_HTTP_PROXY, false)
    var vpnBypassLan by rememberMmkvString(AppConfig.PREF_VPN_BYPASS_LAN, "0")
    var vpnInterfaceAddress by rememberMmkvString(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX, "0")
    var vpnMtu by rememberMmkvString(AppConfig.PREF_VPN_MTU, "")
    var useHevTun by rememberMmkvBool(AppConfig.PREF_USE_HEV_TUNNEL, true)
    var hevTunLogLevel by rememberMmkvString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, "warning")
    var hevTunRwTimeout by rememberMmkvString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, "")

    val mode by rememberMmkvString(AppConfig.PREF_MODE, VPN)
    var enableLocalProxy by rememberMmkvBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)

    val isVpn = mode == VPN
    val hevTunEnabled = isVpn && useHevTun
    val effectiveLocalProxy = enableLocalProxy || hevTunEnabled

    val bypassLanEntries = stringArrayResource(R.array.vpn_bypass_lan).toList()
    val bypassLanValues = stringArrayResource(R.array.vpn_bypass_lan_value).toList()
    val interfaceAddrEntries = stringArrayResource(R.array.vpn_interface_address).toList()
    val interfaceAddrValues = stringArrayResource(R.array.vpn_interface_address_value).toList()
    val hevLogEntries = stringArrayResource(R.array.hev_tunnel_loglevel).toList()
    val hevLogValues = stringArrayResource(R.array.hev_tunnel_loglevel).toList()

    SettingsColumn(modifier) {
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_ipv6_enabled),
            summary = stringResource(R.string.summary_pref_ipv6_enabled),
            checked = ipv6Enabled,
            onCheckedChange = { ipv6Enabled = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_prefer_ipv6),
            summary = stringResource(R.string.summary_pref_prefer_ipv6),
            checked = preferIpv6,
            onCheckedChange = { preferIpv6 = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_local_dns_enabled),
            summary = stringResource(R.string.summary_pref_local_dns_enabled),
            checked = localDns,
            enabled = isVpn,
            onCheckedChange = { localDns = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_fake_dns_enabled),
            summary = stringResource(R.string.summary_pref_fake_dns_enabled),
            checked = fakeDns,
            enabled = isVpn && localDns,
            onCheckedChange = { fakeDns = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_vpn_dns),
            value = vpnDns,
            enabled = isVpn && !localDns,
            onValueChanged = { vpnDns = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_append_http_proxy),
            summary = stringResource(R.string.summary_pref_append_http_proxy),
            checked = appendHttpProxy,
            enabled = effectiveLocalProxy,
            onCheckedChange = { appendHttpProxy = it }
        )
        SettingsListItem(
            title = stringResource(R.string.title_pref_vpn_bypass_lan),
            entries = bypassLanEntries,
            values = bypassLanValues,
            selectedValue = vpnBypassLan,
            enabled = isVpn,
            onSelected = { vpnBypassLan = it }
        )
        SettingsListItem(
            title = stringResource(R.string.title_pref_vpn_interface_address),
            entries = interfaceAddrEntries,
            values = interfaceAddrValues,
            selectedValue = vpnInterfaceAddress,
            enabled = isVpn,
            onSelected = { vpnInterfaceAddress = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_vpn_mtu),
            value = vpnMtu,
            enabled = isVpn,
            keyboardNumber = true,
            onValueChanged = { vpnMtu = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_use_hev_tunnel),
            summary = stringResource(R.string.summary_pref_use_hev_tunnel),
            checked = useHevTun,
            enabled = isVpn,
            onCheckedChange = {
                useHevTun = it
                if (it && !enableLocalProxy) {
                    enableLocalProxy = true
                }
            }
        )
        SettingsListItem(
            title = stringResource(R.string.title_pref_hev_tunnel_loglevel),
            entries = hevLogEntries,
            values = hevLogValues,
            selectedValue = hevTunLogLevel,
            enabled = hevTunEnabled,
            onSelected = { hevTunLogLevel = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_hev_tunnel_rw_timeout),
            value = hevTunRwTimeout,
            enabled = hevTunEnabled,
            keyboardNumber = true,
            onValueChanged = { hevTunRwTimeout = it }
        )
    }
}

@Composable
private fun CoreSettings(modifier: Modifier) {
    var sniffingEnabled by rememberMmkvBool(AppConfig.PREF_SNIFFING_ENABLED, true)
    var routeOnlyEnabled by rememberMmkvBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false)
    var enableLocalProxy by rememberMmkvBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
    var appendHttpProxy by rememberMmkvBool(AppConfig.PREF_APPEND_HTTP_PROXY, false)
    var proxySharing by rememberMmkvBool(AppConfig.PREF_PROXY_SHARING, false)
    var dynamicSocksPort by rememberMmkvBool(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false)
    var socksPort by rememberMmkvString(AppConfig.PREF_SOCKS_PORT, "")
    var socksUsername by rememberMmkvString(AppConfig.PREF_SOCKS_USERNAME, "")
    var socksPassword by rememberMmkvString(AppConfig.PREF_SOCKS_PASSWORD, "")
    var socksEnableUdp by rememberMmkvBool(AppConfig.PREF_SOCKS_ENABLE_UDP, false)
    var remoteDns by rememberMmkvString(AppConfig.PREF_REMOTE_DNS, "")
    var domesticDns by rememberMmkvString(AppConfig.PREF_DOMESTIC_DNS, "")
    var dnsHosts by rememberMmkvString(AppConfig.PREF_DNS_HOSTS, "")
    var outboundResolveMethod by rememberMmkvString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, "0")

    val mode by rememberMmkvString(AppConfig.PREF_MODE, VPN)
    val useHevTun by rememberMmkvBool(AppConfig.PREF_USE_HEV_TUNNEL, true)

    val localProxyForced = mode == VPN && useHevTun
    val effectiveLocalProxy = enableLocalProxy || localProxyForced

    val outboundResolveEntries = stringArrayResource(R.array.outbound_domain_resolve_method).toList()
    val outboundResolveValues = stringArrayResource(R.array.outbound_domain_resolve_method_value).toList()

    SettingsColumn(modifier) {
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_sniffing_enabled),
            summary = stringResource(R.string.summary_pref_sniffing_enabled),
            checked = sniffingEnabled,
            onCheckedChange = { sniffingEnabled = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_route_only_enabled),
            summary = stringResource(R.string.summary_pref_route_only_enabled),
            checked = routeOnlyEnabled,
            onCheckedChange = { routeOnlyEnabled = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_enable_local_proxy),
            summary = stringResource(R.string.summary_pref_enable_local_proxy),
            checked = enableLocalProxy,
            enabled = !localProxyForced,
            onCheckedChange = {
                if (!localProxyForced) {
                    enableLocalProxy = it
                    if (!it && appendHttpProxy) {
                        appendHttpProxy = false
                    }
                }
            }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_proxy_sharing_enabled),
            summary = stringResource(R.string.summary_pref_proxy_sharing_enabled),
            checked = proxySharing,
            enabled = effectiveLocalProxy,
            onCheckedChange = { proxySharing = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_dynamic_socks_port),
            summary = stringResource(R.string.summary_pref_dynamic_socks_port),
            checked = dynamicSocksPort,
            enabled = effectiveLocalProxy,
            onCheckedChange = { dynamicSocksPort = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_socks_port),
            value = socksPort,
            enabled = effectiveLocalProxy && !dynamicSocksPort,
            keyboardNumber = true,
            onValueChanged = { socksPort = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_socks_username),
            value = socksUsername,
            enabled = effectiveLocalProxy,
            onValueChanged = { socksUsername = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_socks_password),
            value = socksPassword,
            enabled = effectiveLocalProxy,
            isPassword = true,
            onValueChanged = { socksPassword = it }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_socks_enable_udp),
            summary = stringResource(R.string.summary_pref_socks_enable_udp),
            checked = socksEnableUdp,
            enabled = effectiveLocalProxy,
            onCheckedChange = { socksEnableUdp = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_remote_dns),
            value = remoteDns,
            onValueChanged = { remoteDns = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_domestic_dns),
            value = domesticDns,
            onValueChanged = { domesticDns = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_dns_hosts),
            value = dnsHosts,
            onValueChanged = { dnsHosts = it }
        )
        SettingsListItem(
            title = stringResource(R.string.title_outbound_domain_resolve_method),
            entries = outboundResolveEntries,
            values = outboundResolveValues,
            selectedValue = outboundResolveMethod,
            onSelected = { outboundResolveMethod = it }
        )
    }
}

@Composable
private fun MuxSettings(modifier: Modifier) {
    var mux by rememberMmkvBool(AppConfig.PREF_MUX_ENABLED, false)
    var muxConcurrency by rememberMmkvString(AppConfig.PREF_MUX_CONCURRENCY, "8")
    var muxXudpConcurrency by rememberMmkvString(AppConfig.PREF_MUX_XUDP_CONCURRENCY, "8")
    var muxXudpQuic by rememberMmkvString(AppConfig.PREF_MUX_XUDP_QUIC, "reject")

    val muxXudpConcurrencyInt = muxXudpConcurrency.toIntOrNull() ?: 8
    val xudpQuicEntries = stringArrayResource(R.array.mux_xudp_quic_entries).toList()
    val xudpQuicValues = stringArrayResource(R.array.mux_xudp_quic_value).toList()

    SettingsColumn(modifier) {
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_mux_enabled),
            summary = stringResource(R.string.summary_pref_mux_enabled),
            checked = mux,
            onCheckedChange = { mux = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_mux_concurency),
            value = muxConcurrency,
            enabled = mux,
            keyboardNumber = true,
            onValueChanged = { muxConcurrency = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_mux_xudp_concurency),
            value = muxXudpConcurrency,
            enabled = mux,
            keyboardNumber = true,
            onValueChanged = { muxXudpConcurrency = it }
        )
        SettingsListItem(
            title = stringResource(R.string.title_pref_mux_xudp_quic),
            entries = xudpQuicEntries,
            values = xudpQuicValues,
            selectedValue = muxXudpQuic,
            enabled = mux && muxXudpConcurrencyInt >= 0,
            onSelected = { muxXudpQuic = it }
        )
    }
}

@Composable
private fun FragmentSettings(modifier: Modifier) {
    var fragment by rememberMmkvBool(AppConfig.PREF_FRAGMENT_ENABLED, false)
    var fragmentPackets by rememberMmkvString(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
    var fragmentLength by rememberMmkvString(AppConfig.PREF_FRAGMENT_LENGTH, "50-100")
    var fragmentInterval by rememberMmkvString(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20")
    var fragmentMaxSplit by rememberMmkvString(AppConfig.PREF_FRAGMENT_MAXSPLIT, "10")

    val fragmentPacketsEntries = stringArrayResource(R.array.fragment_packets).toList()
    val fragmentPacketsValues = stringArrayResource(R.array.fragment_packets).toList()

    SettingsColumn(modifier) {
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_fragment_enabled),
            checked = fragment,
            onCheckedChange = { fragment = it }
        )
        SettingsListItem(
            title = stringResource(R.string.title_pref_fragment_packets),
            entries = fragmentPacketsEntries,
            values = fragmentPacketsValues,
            selectedValue = fragmentPackets,
            enabled = fragment,
            onSelected = { fragmentPackets = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_fragment_length),
            value = fragmentLength,
            enabled = fragment,
            onValueChanged = { fragmentLength = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_fragment_interval),
            value = fragmentInterval,
            enabled = fragment,
            onValueChanged = { fragmentInterval = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_fragment_maxsplit),
            value = fragmentMaxSplit,
            enabled = fragment,
            keyboardNumber = true,
            onValueChanged = { fragmentMaxSplit = it }
        )
    }
}

@Composable
private fun ObservatorySettings(modifier: Modifier, viewModel: SettingsViewModel) {
    var observatoryLeastPingInterval by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL, AppConfig.OBSERVATORY_LEAST_PING_INTERVAL)
    var observatoryLeastLoadInterval by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_INTERVAL, AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL)
    var observatoryLeastLoadMethod by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_METHOD, AppConfig.OBSERVATORY_LEAST_LOAD_METHOD)
    var observatoryLeastLoadSampling by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_SAMPLING, AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING)
    var observatoryLeastLoadTimeout by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT, AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT)

    val observatoryLeastLoadMethodEntries = stringArrayResource(R.array.observatory_least_load_method).toList()
    val observatoryLeastLoadMethodValues = stringArrayResource(R.array.observatory_least_load_method).toList()

    SettingsColumn(modifier) {
        SettingsEditItem(
            title = stringResource(R.string.title_pref_observatory_least_ping_interval),
            value = observatoryLeastPingInterval,
            onValueChanged = {
                viewModel.validateObservatoryDuration(it)?.let { value ->
                    observatoryLeastPingInterval = value
                }
            }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_observatory_least_load_interval),
            value = observatoryLeastLoadInterval,
            onValueChanged = {
                viewModel.validateObservatoryDuration(it)?.let { value ->
                    observatoryLeastLoadInterval = value
                }
            }
        )
        SettingsListItem(
            title = stringResource(R.string.title_pref_observatory_least_load_method),
            entries = observatoryLeastLoadMethodEntries,
            values = observatoryLeastLoadMethodValues,
            selectedValue = observatoryLeastLoadMethod,
            onSelected = { observatoryLeastLoadMethod = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_observatory_least_load_sampling),
            value = observatoryLeastLoadSampling,
            keyboardNumber = true,
            onValueChanged = {
                viewModel.validateObservatorySampling(it)?.let { value ->
                    observatoryLeastLoadSampling = value
                }
            }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_observatory_least_load_timeout),
            value = observatoryLeastLoadTimeout,
            onValueChanged = {
                viewModel.validateObservatoryDuration(it)?.let { value ->
                    observatoryLeastLoadTimeout = value
                }
            }
        )
    }
}

@Composable
private fun LogSettings(modifier: Modifier) {
    val context = LocalContext.current
    var coreLogLevel by rememberMmkvString(AppConfig.PREF_LOGLEVEL, "warning")
    var logToFile by rememberMmkvBool(AppConfig.PREF_CORE_LOG_TO_FILE, false)

    val coreLogLevelEntries = stringArrayResource(R.array.core_loglevel).toList()
    val coreLogLevelValues = stringArrayResource(R.array.core_loglevel).toList()

    // Files grow while the core runs, so the list is rebuilt every time the screen is shown
    var refreshTick by remember { mutableIntStateOf(0) }
    ResumePauseEffect(onResume = { refreshTick++ }, onPause = {})

    val logFiles by produceState(emptyList<LogFileInfo>(), refreshTick, logToFile) {
        value = withContext(Dispatchers.IO) { LogFileManager.listLogFiles(context) }
    }

    SettingsColumn(modifier, grouped = false) {
        PreferenceGroupHeader(title = stringResource(R.string.title_log_settings))
        SettingsGroupCard {
            SettingsListItem(
                title = stringResource(R.string.title_core_loglevel),
                entries = coreLogLevelEntries,
                values = coreLogLevelValues,
                selectedValue = coreLogLevel,
                onSelected = { coreLogLevel = it }
            )
            SettingsSwitchItem(
                title = stringResource(R.string.title_pref_core_log_to_file),
                summary = stringResource(R.string.summary_pref_core_log_to_file),
                checked = logToFile,
                onCheckedChange = { logToFile = it }
            )
            SettingsMenuItem(
                title = stringResource(R.string.title_view_logs),
                onClick = { context.startActivity(Intent(context, LogcatActivity::class.java)) }
            )
        }

        PreferenceGroupHeader(title = stringResource(R.string.title_log_files))
        SettingsGroupCard {
            if (logFiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.log_files_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                logFiles.forEach { logFile ->
                    SettingsFileItem(
                        title = logFile.name,
                        subtitle = formatLogTimestamp(logFile.lastModified),
                        trailingText = logFile.sizeBytes.toTrafficString(),
                        onClick = {
                            context.startActivity(
                                Intent(context, LogFileActivity::class.java).apply {
                                    putExtra(LogFileActivity.EXTRA_PATH, logFile.path)
                                    putExtra(LogFileActivity.EXTRA_NAME, logFile.name)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun formatLogTimestamp(millis: Long): String =
    SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(millis))

@Composable
private fun AdvancedSettings(modifier: Modifier) {
    var isBooted by rememberMmkvBool(AppConfig.PREF_IS_BOOTED, false)
    var pingType by rememberMmkvString(AppConfig.PREF_PING_TYPE, PingType.PROXY_GET.value)
    var delayTestUrl by rememberMmkvString(AppConfig.PREF_DELAY_TEST_URL, "")
    var realPingConcurrency by rememberMmkvString(AppConfig.PREF_REAL_PING_CONCURRENCY, "16")
    var ipApiUrl by rememberMmkvString(AppConfig.PREF_IP_API_URL, "")

    val pingTypeEntries = stringArrayResource(R.array.ping_type_entries).toList()
    val pingTypeValues = stringArrayResource(R.array.ping_type_value).toList()

    SettingsColumn(modifier) {
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_is_booted),
            summary = stringResource(R.string.summary_pref_is_booted),
            checked = isBooted,
            onCheckedChange = { isBooted = it }
        )
        SettingsListItem(
            title = stringResource(R.string.title_pref_ping_type),
            entries = pingTypeEntries,
            values = pingTypeValues,
            selectedValue = pingType,
            onSelected = { pingType = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_delay_test_url),
            value = delayTestUrl,
            onValueChanged = { delayTestUrl = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_real_ping_concurrency),
            value = realPingConcurrency,
            keyboardNumber = true,
            onValueChanged = { realPingConcurrency = it }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_ip_api_url),
            value = ipApiUrl,
            onValueChanged = { ipApiUrl = it }
        )
    }
}
