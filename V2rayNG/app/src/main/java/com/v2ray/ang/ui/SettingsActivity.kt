package com.v2ray.ang.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.core.root.RootManager
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MmkvPreferenceDataStore

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(R.layout.activity_settings, showHomeAsUp = true, title = getString(R.string.title_settings))
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val localDns by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_LOCAL_DNS_ENABLED) }
        private val fakeDns by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_FAKE_DNS_ENABLED) }
        private val appendHttpProxy by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_APPEND_HTTP_PROXY) }

        //        private val localDnsPort by lazy { findPreference<EditTextPreference>(AppConfig.PREF_LOCAL_DNS_PORT) }
        private val vpnDns by lazy { findPreference<EditTextPreference>(AppConfig.PREF_VPN_DNS) }
        private val vpnBypassLan by lazy { findPreference<ListPreference>(AppConfig.PREF_VPN_BYPASS_LAN) }
        private val vpnInterfaceAddress by lazy { findPreference<ListPreference>(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX) }
        private val vpnMtu by lazy { findPreference<EditTextPreference>(AppConfig.PREF_VPN_MTU) }

        private val mux by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_MUX_ENABLED) }
        private val muxConcurrency by lazy { findPreference<EditTextPreference>(AppConfig.PREF_MUX_CONCURRENCY) }
        private val muxXudpConcurrency by lazy { findPreference<EditTextPreference>(AppConfig.PREF_MUX_XUDP_CONCURRENCY) }
        private val muxXudpQuic by lazy { findPreference<ListPreference>(AppConfig.PREF_MUX_XUDP_QUIC) }

        private val fragment by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_FRAGMENT_ENABLED) }
        private val fragmentPackets by lazy { findPreference<ListPreference>(AppConfig.PREF_FRAGMENT_PACKETS) }
        private val fragmentLength by lazy { findPreference<EditTextPreference>(AppConfig.PREF_FRAGMENT_LENGTH) }
        private val fragmentInterval by lazy { findPreference<EditTextPreference>(AppConfig.PREF_FRAGMENT_INTERVAL) }

        private val mode by lazy { findPreference<ListPreference>(AppConfig.PREF_MODE) }
        private val lanSharing by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_ROOT_LAN_SHARING) }

        private val hevTunLogLevel by lazy { findPreference<ListPreference>(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL) }
        private val hevTunRwTimeout by lazy { findPreference<EditTextPreference>(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT) }
        private val useHevTun by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_USE_HEV_TUNNEL) }

        private val enableLocalProxy by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_ENABLE_LOCAL_PROXY) }
        private val socksPort by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_PORT) }
        private val dynamicSocksPort by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_DYNAMIC_SOCKS_PORT) }
        private val socksUsername by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_USERNAME) }
        private val socksPassword by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_PASSWORD) }
        private val socksEnableUdp by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_SOCKS_ENABLE_UDP) }
        private val proxySharing by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_PROXY_SHARING) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            // Use MMKV as the storage backend for all Preferences
            // This prevents inconsistencies between SharedPreferences and MMKV
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()

            addPreferencesFromResource(R.xml.pref_settings)

            // Populate run-mode options (root modes stay selectable; root is probed on demand).
            applyModeOptions()

            initPreferenceSummaries()

            localDns?.setOnPreferenceChangeListener { _, any ->
                updateLocalDns(any as Boolean)
                true
            }

            mux?.setOnPreferenceChangeListener { _, newValue ->
                updateMux(newValue as Boolean)
                true
            }
            muxConcurrency?.setOnPreferenceChangeListener { _, newValue ->
                updateMuxConcurrency(newValue as String)
                true
            }
            muxXudpConcurrency?.setOnPreferenceChangeListener { _, newValue ->
                updateMuxXudpConcurrency(newValue as String)
                true
            }

            fragment?.setOnPreferenceChangeListener { _, newValue ->
                updateFragment(newValue as Boolean)
                true
            }


            useHevTun?.setOnPreferenceChangeListener { _, newValue ->
                updateHevTunSettings(newValue as Boolean)
                true
            }

            enableLocalProxy?.setOnPreferenceChangeListener { _, newValue ->
                updateEnableLocalProxy(newValue as Boolean)
                true
            }

            dynamicSocksPort?.setOnPreferenceChangeListener { _, newValue ->
                updateDynamicSocksPort(newValue as Boolean)
                true
            }

            // Root is an opt-in feature: probe su only when the user actually enables LAN
            // sharing, never at startup. If root is denied, leave the box unchecked.
            lanSharing?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true && !RootManager.cachedRoot()) {
                    RootManager.refreshAsync { hasRoot ->
                        activity?.runOnUiThread {
                            if (!isAdded) return@runOnUiThread
                            if (hasRoot) {
                                lanSharing?.isChecked = true
                            } else {
                                context?.toastError(R.string.toast_root_required)
                            }
                        }
                    }
                    false // accepted asynchronously once root is confirmed
                } else {
                    true
                }
            }
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            // Use the custom chooser for the run-mode preference so root modes can be shown
            // greyed-out; every other preference keeps the standard dialog. Intercepting here
            // (instead of a click listener) guarantees a single dialog.
            if (preference.key == AppConfig.PREF_MODE) {
                showModeDialog()
                return
            }
            super.onDisplayPreferenceDialog(preference)
        }

        private fun initPreferenceSummaries() {
            fun updateSummary(pref: androidx.preference.Preference) {
                when (pref) {
                    is EditTextPreference -> {
                        if (pref.key == AppConfig.PREF_SOCKS_PASSWORD) {
                            pref.summary = if (pref.text.isNullOrEmpty()) "" else "******"
                        } else {
                            pref.summary = pref.text.orEmpty()
                        }
                        pref.setOnPreferenceChangeListener { p, newValue ->
                            if (p.key == AppConfig.PREF_SOCKS_PASSWORD) {
                                p.summary = if ((newValue as? String).isNullOrEmpty()) "" else "******"
                            } else {
                                p.summary = (newValue as? String).orEmpty()
                            }
                            true
                        }
                    }

                    is ListPreference -> {
                        pref.summary = pref.entry ?: ""
                        pref.setOnPreferenceChangeListener { p, newValue ->
                            val lp = p as ListPreference
                            val idx = lp.findIndexOfValue(newValue as? String)
                            lp.summary = (if (idx >= 0) lp.entries[idx] else newValue) as CharSequence?
                            true
                        }
                    }

                    is CheckBoxPreference, is androidx.preference.SwitchPreferenceCompat -> {
                    }
                }
            }

            fun traverse(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    when (val p = group.getPreference(i)) {
                        is androidx.preference.PreferenceGroup -> traverse(p)
                        else -> updateSummary(p)
                    }
                }
            }

            preferenceScreen?.let { traverse(it) }
        }

        override fun onStart() {
            super.onStart()

            updateHevTunSettings(MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, true))

            // Initialize mode-dependent UI states
            updateMode(MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, VPN))

            // Initialize local proxy state
            updateEnableLocalProxy(MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true))

            // Initialize mux-dependent UI states
            updateMux(MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false))

            // Initialize fragment-dependent UI states
            updateFragment(MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false))

            updateDynamicSocksPort(MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false))
        }

        private data class ModeOption(val value: String, val labelRes: Int, val rootOnly: Boolean)

        // Single source of truth for the offered run modes. All are always shown; root
        // modes are greyed-out (not hidden) for non-root users.
        // REDIRECT is intentionally retired (Tun2socks supersedes it, full TCP+UDP).
        // TPROXY is not offered yet (needs a bundled root xray binary).
        private val modeOptions = listOf(
            ModeOption(AppConfig.MODE_VPN, R.string.mode_vpn, false),
            ModeOption(AppConfig.MODE_PROXY_ONLY, R.string.mode_proxy_only, false),
            ModeOption(AppConfig.MODE_TUN2SOCKS, R.string.mode_tun2socks, true),
        )

        /**
         * Keep the ListPreference's entries/values in sync. Falls back to VPN only when the
         * persisted value is unknown; a valid root selection is left intact — root is probed
         * on demand when the user picks a root mode and re-checked on service start.
         */
        private fun applyModeOptions() {
            mode?.entryValues = modeOptions.map { it.value }.toTypedArray()
            mode?.entries = modeOptions.map { getString(it.labelRes) }.toTypedArray()

            val current = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.MODE_VPN)
            if (modeOptions.firstOrNull { it.value == current } == null) {
                MmkvManager.encodeSettings(AppConfig.PREF_MODE, AppConfig.MODE_VPN)
                mode?.value = AppConfig.MODE_VPN
            }
            mode?.let { lp ->
                val idx = lp.findIndexOfValue(lp.value)
                lp.summary = if (idx >= 0) lp.entries[idx] else lp.value
            }
            updateMode(MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.MODE_VPN))
        }

        /**
         * Custom mode chooser. Root modes are always selectable; choosing one probes su on
         * demand (no startup prompt) and, if root is denied, drops the selection with a toast.
         */
        private fun showModeDialog() {
            val ctx = context ?: return
            val labels = modeOptions.map { getString(it.labelRes) }
            val current = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.MODE_VPN)
            val checked = modeOptions.indexOfFirst { it.value == current }.coerceAtLeast(0)

            AlertDialog.Builder(ctx)
                .setTitle(R.string.title_mode)
                .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                    val opt = modeOptions[which]
                    if (opt.rootOnly && !RootManager.cachedRoot()) {
                        dialog.dismiss()
                        RootManager.refreshAsync { hasRoot ->
                            activity?.runOnUiThread {
                                if (!isAdded) return@runOnUiThread
                                if (hasRoot) selectMode(opt, labels[which])
                                else context?.toastError(R.string.toast_root_required)
                            }
                        }
                    } else {
                        selectMode(opt, labels[which])
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        /** Persist and apply a chosen run mode. */
        private fun selectMode(opt: ModeOption, label: String) {
            mode?.value = opt.value
            MmkvManager.encodeSettings(AppConfig.PREF_MODE, opt.value)
            updateMode(opt.value)
            mode?.summary = label
        }

        private fun updateMode(value: String?) {
            val vpn = value == VPN
            localDns?.isEnabled = vpn
            fakeDns?.isEnabled = vpn
            appendHttpProxy?.isEnabled = vpn
//            localDnsPort?.isEnabled = vpn
            vpnDns?.isEnabled = vpn
            vpnBypassLan?.isEnabled = vpn
            vpnInterfaceAddress?.isEnabled = vpn
            vpnMtu?.isEnabled = vpn
            useHevTun?.isEnabled = vpn
            // Transparent LAN / tethering sharing applies to VPN and Root modes. Root is
            // verified on demand when the box is checked, so the toggle stays enabled here.
            // Proxy-only already has its own "allow connections from other devices" option
            // (PREF_PROXY_SHARING), so it's excluded.
            lanSharing?.isEnabled = value != AppConfig.MODE_PROXY_ONLY
            updateHevTunSettings(false)
            if (vpn) {
                updateLocalDns(
                    MmkvManager.decodeSettingsBool(
                        AppConfig.PREF_LOCAL_DNS_ENABLED,
                        false
                    )
                )
                updateHevTunSettings(
                    MmkvManager.decodeSettingsBool(
                        AppConfig.PREF_USE_HEV_TUNNEL,
                        false
                    )
                )
            }
        }

        private fun updateLocalDns(enabled: Boolean) {
            fakeDns?.isEnabled = enabled
//            localDnsPort?.isEnabled = enabled
            vpnDns?.isEnabled = !enabled
        }

        private fun updateMux(enabled: Boolean) {
            muxConcurrency?.isEnabled = enabled
            muxXudpConcurrency?.isEnabled = enabled
            muxXudpQuic?.isEnabled = enabled
            if (enabled) {
                updateMuxConcurrency(MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_CONCURRENCY, "8"))
                updateMuxXudpConcurrency(MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_XUDP_CONCURRENCY, "8"))
            }
        }

        private fun updateMuxConcurrency(value: String?) {
            val concurrency = value?.toIntOrNull() ?: 8
            muxConcurrency?.summary = concurrency.toString()
        }


        private fun updateMuxXudpConcurrency(value: String?) {
            if (value == null) {
                muxXudpQuic?.isEnabled = true
            } else {
                val concurrency = value.toIntOrNull() ?: 8
                muxXudpConcurrency?.summary = concurrency.toString()
                muxXudpQuic?.isEnabled = concurrency >= 0
            }
        }

        private fun updateFragment(enabled: Boolean) {
            fragmentPackets?.isEnabled = enabled
            fragmentLength?.isEnabled = enabled
            fragmentInterval?.isEnabled = enabled
        }

        private fun updateDynamicSocksPort(enabled: Boolean) {
            socksPort?.isEnabled = (enableLocalProxy?.isChecked == true) && !enabled
        }

        private fun updateEnableLocalProxy(enabled: Boolean) {
            val dynamic = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false)
            socksPort?.isEnabled = enabled && !dynamic
            dynamicSocksPort?.isEnabled = enabled
            socksUsername?.isEnabled = enabled
            socksPassword?.isEnabled = enabled
            socksEnableUdp?.isEnabled = enabled
            proxySharing?.isEnabled = enabled

            if (!enabled) {
                if (appendHttpProxy?.isChecked == true) {
                    appendHttpProxy?.isChecked = false
                    MmkvManager.encodeSettings(AppConfig.PREF_APPEND_HTTP_PROXY, false)
                }
                appendHttpProxy?.isEnabled = false
            } else {
                val vpn = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) == VPN
                appendHttpProxy?.isEnabled = vpn
            }
        }

        private fun updateHevTunSettings(enabled: Boolean) {
            hevTunLogLevel?.isEnabled = enabled
            hevTunRwTimeout?.isEnabled = enabled

            if (enabled) {
                if (enableLocalProxy?.isChecked == false) {
                    enableLocalProxy?.isChecked = true
                    MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
                }
                enableLocalProxy?.isEnabled = false
            } else {
                enableLocalProxy?.isEnabled = true
            }
            updateEnableLocalProxy(enableLocalProxy?.isChecked == true)
        }
    }
}
