package com.v2ray.ang.ui

import android.os.Bundle
import android.view.View
import android.text.InputType
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.extension.toLongEx
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.util.Utils
import java.util.concurrent.TimeUnit

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

        private val autoUpdateCheck by lazy { findPreference<CheckBoxPreference>(AppConfig.SUBSCRIPTION_AUTO_UPDATE) }
        private val autoUpdateInterval by lazy { findPreference<EditTextPreference>(AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL) }
        private val mode by lazy { findPreference<ListPreference>(AppConfig.PREF_MODE) }
        private val socksPortAuto by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_SOCKS_PORT_AUTO) }
        private val socksGeneratedPort by lazy { findPreference<CopyablePreference>("pref_socks_port_auto_preview") }
        private val socksPort by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_PORT) }
        private val socksAuthAuto by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_SOCKS_AUTH_AUTO) }
        private val socksGeneratedUsername by lazy { findPreference<CopyablePreference>("pref_socks_auth_auto_username_preview") }
        private val socksGeneratedPassword by lazy { findPreference<CopyablePreference>("pref_socks_auth_auto_password_preview") }
        private val socksUsername by lazy { findPreference<CopyableEditTextPreference>(AppConfig.PREF_SOCKS_USERNAME) }
        private val socksPassword by lazy { findPreference<CopyableEditTextPreference>(AppConfig.PREF_SOCKS_PASSWORD) }

        private val hevTunLogLevel by lazy { findPreference<ListPreference>(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL) }
        private val hevTunRwTimeout by lazy { findPreference<EditTextPreference>(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT) }
        private val useHevTun by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_USE_HEV_TUNNEL) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            // Use MMKV as the storage backend for all Preferences
            // This prevents inconsistencies between SharedPreferences and MMKV
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()

            addPreferencesFromResource(R.xml.pref_settings)

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

            autoUpdateCheck?.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as Boolean
                autoUpdateCheck?.isChecked = value
                autoUpdateInterval?.isEnabled = value
                autoUpdateInterval?.text?.toLongEx()?.let {
                    if (newValue) configureUpdateTask(it) else cancelUpdateTask()
                }
                true
            }
            mode?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                updateMode(valueStr)
                true
            }
            mode?.dialogLayoutResource = R.layout.preference_with_help_link
            socksPortAuto?.setOnPreferenceChangeListener { _, newValue ->
                val autoEnabled = newValue as Boolean
                if (!autoEnabled) {
                    val generatedPort = SettingsManager.getLocalSocksPort().toString()
                    socksPort?.text = generatedPort
                    socksPort?.summary = getEditTextSummary(socksPort!!, generatedPort)
                }
                updateSocksPortUi(autoEnabled)
                true
            }
            socksPassword?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            socksAuthAuto?.setOnPreferenceChangeListener { _, newValue ->
                val autoEnabled = newValue as Boolean
                if (!autoEnabled) {
                    val generatedUser = SettingsManager.getLocalSocksAuthUser()
                    val generatedPass = SettingsManager.getLocalSocksAuthPass()
                    socksUsername?.text = generatedUser
                    socksPassword?.text = generatedPass
                    socksUsername?.summary = getEditTextSummary(socksUsername!!, generatedUser)
                    socksPassword?.summary = getEditTextSummary(socksPassword!!, generatedPass)
                }
                updateSocksAuthUi(autoEnabled)
                true
            }
            socksGeneratedPort?.onCopyClick = {
                copyToClipboard(socksGeneratedPort?.summary?.toString().orEmpty())
            }
            socksGeneratedUsername?.onCopyClick = {
                copyToClipboard(socksGeneratedUsername?.summary?.toString().orEmpty())
            }
            socksGeneratedPassword?.onCopyClick = {
                copyToClipboard(socksGeneratedPassword?.summary?.toString().orEmpty())
            }
            socksUsername?.onCopyClick = {
                copyToClipboard(socksUsername?.text.orEmpty())
            }
            socksPassword?.onCopyClick = {
                copyToClipboard(socksPassword?.text.orEmpty())
            }

            useHevTun?.setOnPreferenceChangeListener { _, newValue ->
                updateHevTunSettings(newValue as Boolean)
                true
            }
        }

        private fun initPreferenceSummaries() {
            fun updateSummary(pref: androidx.preference.Preference) {
                when (pref) {
                    is EditTextPreference -> {
                        pref.summary = getEditTextSummary(pref)
                        pref.setOnPreferenceChangeListener { p, newValue ->
                            val editTextPreference = p as EditTextPreference
                            editTextPreference.summary = getEditTextSummary(editTextPreference, newValue as? String)
                            if (!SettingsManager.isSocksAuthAuto() &&
                                (editTextPreference.key == AppConfig.PREF_SOCKS_USERNAME || editTextPreference.key == AppConfig.PREF_SOCKS_PASSWORD)
                            ) {
                                updateSocksAuthUi(false)
                            }
                            if (!SettingsManager.isSocksPortAuto() && editTextPreference.key == AppConfig.PREF_SOCKS_PORT) {
                                updateSocksPortUi(false)
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

        private fun getEditTextSummary(pref: EditTextPreference, value: String? = pref.text): String {
            return when (pref.key) {
                AppConfig.PREF_SOCKS_PORT -> value?.takeIf { it.isNotEmpty() }
                    ?: getString(R.string.summary_pref_socks_port)

                AppConfig.PREF_SOCKS_USERNAME -> value?.takeIf { it.isNotEmpty() }
                    ?: getString(R.string.summary_pref_socks_username)

                AppConfig.PREF_SOCKS_PASSWORD -> if (value.isNullOrEmpty()) {
                    getString(R.string.summary_pref_socks_password)
                } else {
                    "*".repeat(value.length.coerceAtMost(8))
                }

                else -> value.orEmpty()
            }
        }

        override fun onStart() {
            super.onStart()
            updateHevTunSettings(MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, true))

            // Initialize mode-dependent UI states
            updateMode(MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, VPN))

            // Initialize mux-dependent UI states
            updateMux(MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false))

            // Initialize fragment-dependent UI states
            updateFragment(MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false))

            // Initialize auto-update interval state
            autoUpdateInterval?.isEnabled = MmkvManager.decodeSettingsBool(AppConfig.SUBSCRIPTION_AUTO_UPDATE, false)
            updateSocksPortUi(SettingsManager.isSocksPortAuto())
            updateSocksAuthUi(SettingsManager.isSocksAuthAuto())
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

        private fun configureUpdateTask(interval: Long) {
            val rw = RemoteWorkManager.getInstance(AngApplication.application)
            rw.cancelUniqueWork(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
            rw.enqueueUniquePeriodicWork(
                AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(
                    SubscriptionUpdater.UpdateTask::class.java,
                    interval,
                    TimeUnit.MINUTES
                )
                    .apply {
                        setInitialDelay(interval, TimeUnit.MINUTES)
                    }
                    .build()
            )
        }

        private fun cancelUpdateTask() {
            val rw = RemoteWorkManager.getInstance(AngApplication.application)
            rw.cancelUniqueWork(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
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

        private fun updateSocksAuthUi(autoEnabled: Boolean) {
            socksGeneratedUsername?.isVisible = autoEnabled
            socksGeneratedPassword?.isVisible = autoEnabled
            socksUsername?.isVisible = !autoEnabled
            socksPassword?.isVisible = !autoEnabled

            if (autoEnabled) {
                socksGeneratedUsername?.summary = SettingsManager.getLocalSocksAuthUser()
                socksGeneratedPassword?.summary = SettingsManager.getLocalSocksAuthPass()
                socksGeneratedUsername?.title = getString(R.string.title_pref_socks_auth_auto_username_preview)
                socksGeneratedPassword?.title = getString(R.string.title_pref_socks_auth_auto_password_preview)
            }

            socksGeneratedUsername?.refreshCopyButton()
            socksGeneratedPassword?.refreshCopyButton()
            socksUsername?.refreshCopyButton()
            socksPassword?.refreshCopyButton()
        }

        private fun updateSocksPortUi(autoEnabled: Boolean) {
            socksGeneratedPort?.isVisible = autoEnabled
            socksPort?.isVisible = !autoEnabled

            if (autoEnabled) {
                val generatedPort = MmkvManager.decodeSettingsString(AppConfig.CACHE_SOCKS_PORT).orEmpty()
                socksGeneratedPort?.summary = if (generatedPort.isNotEmpty()) {
                    generatedPort
                } else {
                    getString(R.string.summary_pref_socks_port_auto_pending)
                }
                socksGeneratedPort?.title = getString(R.string.title_pref_socks_port_auto_preview)
            }

            socksGeneratedPort?.refreshCopyButton()
        }

        private fun copyToClipboard(value: String) {
            if (value.isBlank() || value == getString(R.string.summary_pref_socks_port_auto_pending)) {
                return
            }
            Utils.setClipboard(requireContext(), value)
            requireContext().toast(R.string.toast_copied_to_clipboard)
        }

        private fun updateHevTunSettings(enabled: Boolean) {
            hevTunLogLevel?.isEnabled = enabled
            hevTunRwTimeout?.isEnabled = enabled
        }
    }

    fun onModeHelpClicked(view: View) {
        Utils.openUri(this, AppConfig.APP_WIKI_MODE)
    }
}
