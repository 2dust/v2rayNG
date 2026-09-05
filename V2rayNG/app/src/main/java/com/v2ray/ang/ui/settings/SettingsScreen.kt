package com.v2ray.ang.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.repository.BoolPref
import com.v2ray.ang.repository.StringPref
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.CollapsiblePreferenceGroupHeader
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.StringOptions
import com.v2ray.ang.ui.compose.rememberStringOptions
import com.v2ray.ang.ui.compose.verticalScrollbar

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onPlatformEvent: (SettingsEvent) -> Boolean,
) {
    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack = remember(onAction) { { onAction(SettingsAction.Back) } }
    BackHandler(onBack = onBack)

    BaseScreen(
        viewModel = viewModel,
        showLoading = false,
        onEvent = { event -> event is SettingsEvent && onPlatformEvent(event) },
        topBar = {
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            AppTopBar(
                title = stringResource(R.string.title_settings),
                onBackClick = onBack,
                isLoading = isLoading,
            )
        },
    ) { state, action ->
        if (state.loaded) SettingsContent(state = state, onAction = action)
    }
}

// ===== section scaffolding =====

private enum class Section(@StringRes val title: Int) {
    UI(R.string.title_ui_settings),
    VPN(R.string.title_vpn_settings),
    CORE(R.string.title_core_settings),
    MUX(R.string.title_mux_settings),
    FRAGMENT(R.string.title_fragment_settings),
    OBSERVATORY(R.string.title_observatory_settings),
    ADVANCED(R.string.title_advanced),
    MODE(R.string.title_mode_settings),
}

private val ContentBottomGap = 24.dp

/** Collapse state is pure UI state; one holder replaces eight remembered booleans. */
@Stable
private class SectionState(expanded: Set<Section>) {
    private val flags = mutableStateMapOf<Section, Boolean>().apply {
        Section.entries.forEach { put(it, it in expanded) }
    }

    operator fun get(section: Section): Boolean = flags[section] == true

    fun set(section: Section, expanded: Boolean) {
        flags[section] = expanded
    }

    private fun expandedNames(): List<String> = Section.entries.filter { get(it) }.map { it.name }

    companion object {
        private val Default = setOf(Section.UI, Section.VPN, Section.CORE)

        val Saver = listSaver<SectionState, String>(
            save = { it.expandedNames() },
            restore = { names -> SectionState(names.mapTo(HashSet()) { Section.valueOf(it) }) },
        )

        fun default() = SectionState(Default)
    }
}

/**
 * Reads its own expand flag, so collapsing one group does not recompose the other seven.
 */
@Composable
private fun SectionGroup(
    section: Section,
    sections: SectionState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val expanded = sections[section]
    CollapsiblePreferenceGroupHeader(
        title = stringResource(section.title),
        expanded = expanded,
        onExpandedChange = { sections.set(section, it) },
        modifier = modifier,
    )
    if (expanded) content()
}

// ===== leaf bindings =====
// Values are passed in as primitives instead of the whole UiState: that is what lets Compose
// skip the ~50 untouched items when a single preference changes.

@Composable
private fun BoolItem(
    pref: BoolPref,
    checked: Boolean,
    @StringRes title: Int,
    @StringRes summary: Int? = null,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val onCheckedChange = remember(pref, onAction) {
        { value: Boolean -> onAction(SettingsAction.BoolChanged(pref, value)) }
    }
    SettingsSwitchItem(
        title = stringResource(title),
        summary = summary?.let { stringResource(it) },
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
    )
}

@Composable
private fun TextItem(
    pref: StringPref,
    value: String,
    @StringRes title: Int,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardNumber: Boolean = false,
) {
    val onValueChanged = remember(pref, onAction) {
        { text: String -> onAction(SettingsAction.TextChanged(pref, text)) }
    }
    SettingsEditItem(
        title = stringResource(title),
        value = value,
        enabled = enabled,
        isPassword = isPassword,
        keyboardNumber = keyboardNumber,
        onValueChanged = onValueChanged,
        modifier = modifier,
    )
}

@Composable
private fun OptionItem(
    pref: StringPref,
    selectedValue: String,
    @StringRes title: Int,
    entries: StringOptions,
    values: StringOptions,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val onSelected = remember(pref, onAction) {
        { value: String -> onAction(SettingsAction.TextChanged(pref, value)) }
    }
    SettingsListItem(
        title = stringResource(title),
        entries = entries,
        values = values,
        selectedValue = selectedValue,
        enabled = enabled,
        onSelected = onSelected,
        modifier = modifier,
    )
}

// ===== content =====

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val sections = rememberSaveable(saver = SectionState.Saver) { SectionState.default() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
    ) {
        SectionGroup(Section.UI, sections) { UiSection(state, onAction) }
        SectionGroup(Section.VPN, sections) { VpnSection(state, onAction) }
        SectionGroup(Section.CORE, sections) { CoreSection(state, onAction) }
        SectionGroup(Section.MUX, sections) { MuxSection(state, onAction) }
        SectionGroup(Section.FRAGMENT, sections) { FragmentSection(state, onAction) }
        SectionGroup(Section.OBSERVATORY, sections) { ObservatorySection(state, onAction) }
        SectionGroup(Section.ADVANCED, sections) { AdvancedSection(state, onAction) }
        SectionGroup(Section.MODE, sections) { ModeSection(state, onAction) }
        NavigationBarsSpacer()
        Spacer(modifier = Modifier.height(ContentBottomGap))
    }
}

@Composable
private fun UiSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    BoolItem(
        BoolPref.SPEED_ENABLED,
        state[BoolPref.SPEED_ENABLED],
        R.string.title_pref_speed_enabled,
        R.string.summary_pref_speed_enabled,
        onAction = onAction
    )
    BoolItem(
        BoolPref.CONFIRM_REMOVE,
        state[BoolPref.CONFIRM_REMOVE],
        R.string.title_pref_confirm_remove,
        R.string.summary_pref_confirm_remove,
        onAction = onAction
    )
    BoolItem(
        BoolPref.DOUBLE_COLUMN_DISPLAY,
        state[BoolPref.DOUBLE_COLUMN_DISPLAY],
        R.string.title_pref_double_column_display,
        R.string.summary_pref_double_column_display,
        onAction = onAction
    )
    BoolItem(
        BoolPref.GROUP_ALL_DISPLAY,
        state[BoolPref.GROUP_ALL_DISPLAY],
        R.string.title_pref_group_all_display,
        R.string.summary_pref_group_all_display,
        onAction = onAction
    )
    BoolItem(
        pref = BoolPref.DYNAMIC_COLOR,
        checked = state[BoolPref.DYNAMIC_COLOR],
        title = R.string.title_pref_dynamic_color,
        summary = R.string.summary_pref_dynamic_color,
        enabled = state.dynamicColorSupported,
        onAction = onAction
    )
    OptionItem(
        StringPref.LANGUAGE,
        state[StringPref.LANGUAGE],
        R.string.title_language,
        rememberStringOptions(R.array.language_select),
        rememberStringOptions(R.array.language_select_value),
        onAction = onAction
    )
    OptionItem(
        StringPref.UI_MODE_NIGHT,
        state[StringPref.UI_MODE_NIGHT],
        R.string.title_pref_ui_mode_night,
        rememberStringOptions(R.array.ui_mode_night),
        rememberStringOptions(R.array.ui_mode_night_value),
        onAction = onAction
    )
}

@Composable
private fun VpnSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val isVpn = state.isVpn
    val localDns = state[BoolPref.LOCAL_DNS]
    val hevTunnel = state.hevTunnel
    val hevLogLevels = rememberStringOptions(R.array.hev_tunnel_loglevel)
    BoolItem(
        BoolPref.IPV6_ENABLED,
        state[BoolPref.IPV6_ENABLED],
        R.string.title_pref_ipv6_enabled,
        R.string.summary_pref_ipv6_enabled,
        onAction = onAction
    )
    BoolItem(
        BoolPref.PREFER_IPV6,
        state[BoolPref.PREFER_IPV6],
        R.string.title_pref_prefer_ipv6,
        R.string.summary_pref_prefer_ipv6,
        onAction = onAction
    )
    BoolItem(
        BoolPref.LOCAL_DNS,
        localDns,
        R.string.title_pref_local_dns_enabled,
        R.string.summary_pref_local_dns_enabled,
        enabled = isVpn,
        onAction = onAction
    )
    BoolItem(
        BoolPref.FAKE_DNS,
        state[BoolPref.FAKE_DNS],
        R.string.title_pref_fake_dns_enabled,
        R.string.summary_pref_fake_dns_enabled,
        enabled = isVpn && localDns,
        onAction = onAction
    )
    TextItem(
        StringPref.VPN_DNS,
        state[StringPref.VPN_DNS],
        R.string.title_pref_vpn_dns,
        enabled = isVpn && !localDns,
        onAction = onAction
    )
    BoolItem(
        BoolPref.APPEND_HTTP_PROXY,
        state[BoolPref.APPEND_HTTP_PROXY],
        R.string.title_pref_append_http_proxy,
        R.string.summary_pref_append_http_proxy,
        enabled = state.localProxy,
        onAction = onAction
    )
    OptionItem(
        StringPref.VPN_BYPASS_LAN,
        state[StringPref.VPN_BYPASS_LAN],
        R.string.title_pref_vpn_bypass_lan,
        rememberStringOptions(R.array.vpn_bypass_lan),
        rememberStringOptions(R.array.vpn_bypass_lan_value),
        enabled = isVpn,
        onAction = onAction
    )
    OptionItem(
        StringPref.VPN_INTERFACE_ADDRESS,
        state[StringPref.VPN_INTERFACE_ADDRESS],
        R.string.title_pref_vpn_interface_address,
        rememberStringOptions(R.array.vpn_interface_address),
        rememberStringOptions(R.array.vpn_interface_address_value),
        enabled = isVpn,
        onAction = onAction
    )
    TextItem(
        StringPref.VPN_MTU,
        state[StringPref.VPN_MTU],
        R.string.title_pref_vpn_mtu,
        enabled = isVpn,
        keyboardNumber = true,
        onAction = onAction
    )
    BoolItem(
        BoolPref.USE_HEV_TUNNEL,
        state[BoolPref.USE_HEV_TUNNEL],
        R.string.title_pref_use_hev_tunnel,
        R.string.summary_pref_use_hev_tunnel,
        enabled = isVpn,
        onAction = onAction
    )
    OptionItem(
        StringPref.HEV_LOGLEVEL,
        state[StringPref.HEV_LOGLEVEL],
        R.string.title_pref_hev_tunnel_loglevel,
        hevLogLevels,
        hevLogLevels,
        enabled = hevTunnel,
        onAction = onAction
    )
    TextItem(
        StringPref.HEV_RW_TIMEOUT,
        state[StringPref.HEV_RW_TIMEOUT],
        R.string.title_pref_hev_tunnel_rw_timeout,
        enabled = hevTunnel,
        keyboardNumber = true,
        onAction = onAction
    )
}

@Composable
private fun CoreSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val localProxy = state.localProxy
    val coreLogLevels = rememberStringOptions(R.array.core_loglevel)
    BoolItem(
        BoolPref.SNIFFING_ENABLED,
        state[BoolPref.SNIFFING_ENABLED],
        R.string.title_pref_sniffing_enabled,
        R.string.summary_pref_sniffing_enabled,
        onAction = onAction
    )
    BoolItem(
        BoolPref.ROUTE_ONLY,
        state[BoolPref.ROUTE_ONLY],
        R.string.title_pref_route_only_enabled,
        R.string.summary_pref_route_only_enabled,
        onAction = onAction
    )
    BoolItem(
        BoolPref.ENABLE_LOCAL_PROXY,
        localProxy,
        R.string.title_pref_enable_local_proxy,
        R.string.summary_pref_enable_local_proxy,
        enabled = !state.localProxyForced,
        onAction = onAction
    )
    BoolItem(
        BoolPref.PROXY_SHARING,
        state[BoolPref.PROXY_SHARING],
        R.string.title_pref_proxy_sharing_enabled,
        R.string.summary_pref_proxy_sharing_enabled,
        enabled = localProxy,
        onAction = onAction
    )
    BoolItem(
        BoolPref.DYNAMIC_SOCKS_PORT,
        state[BoolPref.DYNAMIC_SOCKS_PORT],
        R.string.title_pref_dynamic_socks_port,
        R.string.summary_pref_dynamic_socks_port,
        enabled = localProxy,
        onAction = onAction
    )
    TextItem(
        StringPref.SOCKS_PORT,
        state[StringPref.SOCKS_PORT],
        R.string.title_pref_socks_port,
        enabled = localProxy && !state[BoolPref.DYNAMIC_SOCKS_PORT],
        keyboardNumber = true,
        onAction = onAction
    )
    TextItem(
        StringPref.SOCKS_USERNAME,
        state[StringPref.SOCKS_USERNAME],
        R.string.title_pref_socks_username,
        enabled = localProxy,
        onAction = onAction
    )
    TextItem(
        StringPref.SOCKS_PASSWORD,
        state[StringPref.SOCKS_PASSWORD],
        R.string.title_pref_socks_password,
        enabled = localProxy,
        isPassword = true,
        onAction = onAction
    )
    BoolItem(
        BoolPref.SOCKS_ENABLE_UDP,
        state[BoolPref.SOCKS_ENABLE_UDP],
        R.string.title_pref_socks_enable_udp,
        R.string.summary_pref_socks_enable_udp,
        enabled = localProxy,
        onAction = onAction
    )
    TextItem(
        StringPref.REMOTE_DNS,
        state[StringPref.REMOTE_DNS],
        R.string.title_pref_remote_dns,
        onAction = onAction
    )
    TextItem(
        StringPref.DOMESTIC_DNS,
        state[StringPref.DOMESTIC_DNS],
        R.string.title_pref_domestic_dns,
        onAction = onAction
    )
    TextItem(
        StringPref.DNS_HOSTS,
        state[StringPref.DNS_HOSTS],
        R.string.title_pref_dns_hosts,
        onAction = onAction
    )
    OptionItem(
        StringPref.CORE_LOGLEVEL,
        state[StringPref.CORE_LOGLEVEL],
        R.string.title_core_loglevel,
        coreLogLevels,
        coreLogLevels,
        onAction = onAction
    )
    OptionItem(
        StringPref.OUTBOUND_RESOLVE,
        state[StringPref.OUTBOUND_RESOLVE],
        R.string.title_outbound_domain_resolve_method,
        rememberStringOptions(R.array.outbound_domain_resolve_method),
        rememberStringOptions(R.array.outbound_domain_resolve_method_value),
        onAction = onAction
    )
}

@Composable
private fun MuxSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val mux = state[BoolPref.MUX_ENABLED]
    BoolItem(
        BoolPref.MUX_ENABLED,
        mux,
        R.string.title_pref_mux_enabled,
        R.string.summary_pref_mux_enabled,
        onAction = onAction
    )
    TextItem(
        StringPref.MUX_CONCURRENCY,
        state[StringPref.MUX_CONCURRENCY],
        R.string.title_pref_mux_concurrency,
        enabled = mux,
        keyboardNumber = true,
        onAction = onAction
    )
    TextItem(
        StringPref.MUX_XUDP_CONCURRENCY,
        state[StringPref.MUX_XUDP_CONCURRENCY],
        R.string.title_pref_mux_xudp_concurrency,
        enabled = mux,
        keyboardNumber = true,
        onAction = onAction
    )
    OptionItem(
        StringPref.MUX_XUDP_QUIC,
        state[StringPref.MUX_XUDP_QUIC],
        R.string.title_pref_mux_xudp_quic,
        rememberStringOptions(R.array.mux_xudp_quic_entries),
        rememberStringOptions(R.array.mux_xudp_quic_value),
        enabled = state.xudpQuicEnabled,
        onAction = onAction
    )
}

@Composable
private fun FragmentSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val fragment = state[BoolPref.FRAGMENT_ENABLED]
    val packets = rememberStringOptions(R.array.fragment_packets)
    BoolItem(
        BoolPref.FRAGMENT_ENABLED,
        fragment,
        R.string.title_pref_fragment_enabled,
        onAction = onAction
    )
    OptionItem(
        StringPref.FRAGMENT_PACKETS,
        state[StringPref.FRAGMENT_PACKETS],
        R.string.title_pref_fragment_packets,
        packets,
        packets,
        enabled = fragment,
        onAction = onAction
    )
    TextItem(
        StringPref.FRAGMENT_LENGTH,
        state[StringPref.FRAGMENT_LENGTH],
        R.string.title_pref_fragment_length,
        enabled = fragment,
        onAction = onAction
    )
    TextItem(
        StringPref.FRAGMENT_INTERVAL,
        state[StringPref.FRAGMENT_INTERVAL],
        R.string.title_pref_fragment_interval,
        enabled = fragment,
        onAction = onAction
    )
    TextItem(
        StringPref.FRAGMENT_MAXSPLIT,
        state[StringPref.FRAGMENT_MAXSPLIT],
        R.string.title_pref_fragment_maxsplit,
        enabled = fragment,
        keyboardNumber = true,
        onAction = onAction
    )
}

@Composable
private fun ObservatorySection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val loadMethods = rememberStringOptions(R.array.observatory_least_load_method)
    TextItem(
        StringPref.OBS_LEAST_PING_INTERVAL,
        state[StringPref.OBS_LEAST_PING_INTERVAL],
        R.string.title_pref_observatory_least_ping_interval,
        onAction = onAction
    )
    TextItem(
        StringPref.OBS_LEAST_LOAD_INTERVAL,
        state[StringPref.OBS_LEAST_LOAD_INTERVAL],
        R.string.title_pref_observatory_least_load_interval,
        onAction = onAction
    )
    OptionItem(
        StringPref.OBS_LEAST_LOAD_METHOD,
        state[StringPref.OBS_LEAST_LOAD_METHOD],
        R.string.title_pref_observatory_least_load_method,
        loadMethods,
        loadMethods,
        onAction = onAction
    )
    TextItem(
        StringPref.OBS_LEAST_LOAD_SAMPLING,
        state[StringPref.OBS_LEAST_LOAD_SAMPLING],
        R.string.title_pref_observatory_least_load_sampling,
        keyboardNumber = true,
        onAction = onAction
    )
    TextItem(
        StringPref.OBS_LEAST_LOAD_TIMEOUT,
        state[StringPref.OBS_LEAST_LOAD_TIMEOUT],
        R.string.title_pref_observatory_least_load_timeout,
        onAction = onAction
    )
}

@Composable
private fun AdvancedSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    BoolItem(
        BoolPref.IS_BOOTED,
        state[BoolPref.IS_BOOTED],
        R.string.title_pref_is_booted,
        R.string.summary_pref_is_booted,
        onAction = onAction
    )
    TextItem(
        StringPref.DELAY_TEST_URL,
        state[StringPref.DELAY_TEST_URL],
        R.string.title_pref_delay_test_url,
        onAction = onAction
    )
    TextItem(
        StringPref.REAL_PING_CONCURRENCY,
        state[StringPref.REAL_PING_CONCURRENCY],
        R.string.title_pref_real_ping_concurrency,
        keyboardNumber = true,
        onAction = onAction
    )
    TextItem(
        StringPref.IP_API_URL,
        state[StringPref.IP_API_URL],
        R.string.title_pref_ip_api_url,
        onAction = onAction
    )
}

@Composable
private fun ModeSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val onModeHelp = remember(onAction) { { onAction(SettingsAction.ModeHelpClicked) } }
    OptionItem(
        StringPref.MODE,
        state[StringPref.MODE],
        R.string.title_mode,
        rememberStringOptions(R.array.mode_entries),
        rememberStringOptions(R.array.mode_value),
        onAction = onAction
    )
    SettingsMenuItem(
        title = stringResource(R.string.title_mode_help),
        onClick = onModeHelp
    )
    BoolItem(
        BoolPref.ROOT_MODE_ENABLE,
        state[BoolPref.ROOT_MODE_ENABLE],
        R.string.title_root_mode_enabled,
        R.string.summary_root_mode_enabled,
        onAction = onAction
    )
    BoolItem(
        BoolPref.ROOT_LAN_SHARING,
        state[BoolPref.ROOT_LAN_SHARING],
        R.string.title_root_lan_sharing,
        R.string.summary_root_lan_sharing,
        onAction = onAction
    )
}
