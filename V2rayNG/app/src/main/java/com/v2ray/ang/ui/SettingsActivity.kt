package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
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
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.util.Utils
import java.util.concurrent.TimeUnit

class SettingsActivity : BaseActivity() {

    companion object {
        const val EXTRA_SHOW_FRAGMENT = "extra_show_fragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fragmentClass = intent.getStringExtra(EXTRA_SHOW_FRAGMENT)
        val titleRes = when (fragmentClass) {
            UISettingsFragment::class.java.name -> R.string.title_ui_settings
            VPNSettingsFragment::class.java.name -> R.string.title_vpn_settings
            CoreSettingsFragment::class.java.name -> R.string.title_core_settings
            SubscriptionSettingsFragment::class.java.name -> R.string.title_pref_subscription_settings
            AdvancedSettingsFragment::class.java.name -> R.string.title_advanced
            else -> R.string.title_settings
        }

        setContentViewWithToolbar(R.layout.activity_settings, showHomeAsUp = true, title = getString(titleRes))

        if (savedInstanceState == null) {
            val fragment = when (fragmentClass) {
                UISettingsFragment::class.java.name -> UISettingsFragment()
                VPNSettingsFragment::class.java.name -> VPNSettingsFragment()
                CoreSettingsFragment::class.java.name -> CoreSettingsFragment()
                SubscriptionSettingsFragment::class.java.name -> SubscriptionSettingsFragment()
                AdvancedSettingsFragment::class.java.name -> AdvancedSettingsFragment()
                else -> SettingsFragment()
            }

            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_content, fragment)
                .commit()
        }
    }

    abstract class BaseSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
        }

        protected fun initPreferenceSummaries() {
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
    }

    class SettingsFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            super.onCreatePreferences(bundle, s)
            addPreferencesFromResource(R.xml.pref_settings)
        }

        override fun onPreferenceTreeClick(preference: androidx.preference.Preference): Boolean {
            if (preference.fragment != null) {
                val intent = Intent(requireContext(), SettingsActivity::class.java)
                intent.putExtra(EXTRA_SHOW_FRAGMENT, preference.fragment)
                startActivity(intent)
                return true
            }
            return super.onPreferenceTreeClick(preference)
        }
    }

    class UISettingsFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            super.onCreatePreferences(bundle, s)
            addPreferencesFromResource(R.xml.pref_settings_ui)
            initPreferenceSummaries()
        }
    }

    class VPNSettingsFragment : BaseSettingsFragment() {
        private val mode by lazy { findPreference<ListPreference>(AppConfig.PREF_MODE) }
        private val tunCategory by lazy { findPreference<androidx.preference.PreferenceCategory>("pref_category_tun") }
        private val tunType by lazy { findPreference<ListPreference>(AppConfig.PREF_USE_HEV_TUNNEL) }
        private val hevTunLogLevel by lazy { findPreference<ListPreference>(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL) }
        private val hevTunRwTimeout by lazy { findPreference<EditTextPreference>(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT) }

        private val enableLocalProxy by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_ENABLE_LOCAL_PROXY) }
        private val proxySharing by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_PROXY_SHARING) }
        private val appendHttpProxy by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_APPEND_HTTP_PROXY) }
        private val socksPort by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_PORT) }
        private val dynamicSocksPort by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_DYNAMIC_SOCKS_PORT) }
        private val socksUsername by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_USERNAME) }
        private val socksPassword by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_PASSWORD) }
        private val enableSocksUdp by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_ENABLE_SOCKS_UDP) }

        private val localDns by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_LOCAL_DNS_ENABLED) }
        private val fakeDns by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_FAKE_DNS_ENABLED) }
        private val vpnDns by lazy { findPreference<EditTextPreference>(AppConfig.PREF_VPN_DNS) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            super.onCreatePreferences(bundle, s)
            addPreferencesFromResource(R.xml.pref_settings_vpn)
            initPreferenceSummaries()

            mode?.setOnPreferenceChangeListener { p, newValue ->
                val value = newValue.toString()
                val lp = p as ListPreference
                val idx = lp.findIndexOfValue(value)
                lp.summary = if (idx >= 0) lp.entries[idx] else value
                updateUIVisibility(value, tunType?.value, enableLocalProxy?.isChecked ?: true)
                true
            }

            tunType?.isPersistent = false
            val isHev = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, false)
            val tunValueStr = isHev.toString()
            tunType?.value = tunValueStr
            tunType?.let { lp ->
                val idx = lp.findIndexOfValue(tunValueStr)
                lp.summary = if (idx >= 0) lp.entries[idx] else tunValueStr
            }

            tunType?.setOnPreferenceChangeListener { p, newValue ->
                val value = newValue.toString()
                val lp = p as ListPreference
                val idx = lp.findIndexOfValue(value)
                lp.summary = if (idx >= 0) lp.entries[idx] else value

                val isHevNew = value == "true"
                MmkvManager.encodeSettings(AppConfig.PREF_USE_HEV_TUNNEL, isHevNew)

                updateUIVisibility(mode?.value, value, enableLocalProxy?.isChecked ?: true)
                true
            }

            enableLocalProxy?.setOnPreferenceChangeListener { _, newValue ->
                updateUIVisibility(mode?.value, tunType?.value, newValue as Boolean)
                true
            }

            localDns?.setOnPreferenceChangeListener { _, any ->
                updateLocalDns(any as Boolean)
                true
            }

            dynamicSocksPort?.setOnPreferenceChangeListener { _, newValue ->
                updateDynamicSocksPort(newValue as Boolean)
                true
            }

            // Initialize visibility immediately
            updateUIVisibility(
                MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, VPN),
                MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, false).toString(),
                MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
            )
            updateLocalDns(MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED, false))
        }

        private fun updateUIVisibility(modeVal: String?, tunVal: String?, proxyEnabled: Boolean) {
            val isVpn = modeVal == VPN
            val isHev = tunVal == "true"
            val canDisableProxy = isVpn && !isHev

            // TUN Category visibility
            tunCategory?.isVisible = isVpn
            hevTunLogLevel?.isVisible = isVpn && isHev
            hevTunRwTimeout?.isVisible = isVpn && isHev

            // Enable Local Proxy logic
            if (!canDisableProxy) {
                enableLocalProxy?.isChecked = true
                enableLocalProxy?.isEnabled = false
            } else {
                enableLocalProxy?.isEnabled = true
                enableLocalProxy?.isChecked = proxyEnabled
            }

            val actualProxyEnabled = enableLocalProxy?.isChecked ?: true

            // SOCKS5/HTTP Proxy sub-settings visibility
            proxySharing?.isVisible = actualProxyEnabled
            appendHttpProxy?.isVisible = actualProxyEnabled
            socksPort?.isVisible = actualProxyEnabled
            dynamicSocksPort?.isVisible = actualProxyEnabled
            socksUsername?.isVisible = actualProxyEnabled
            socksPassword?.isVisible = actualProxyEnabled
            enableSocksUdp?.isVisible = actualProxyEnabled

            if (actualProxyEnabled) {
                updateDynamicSocksPort(dynamicSocksPort?.isChecked ?: false)
            }
        }

        private fun updateLocalDns(enabled: Boolean) {
            fakeDns?.isEnabled = enabled
            vpnDns?.isEnabled = !enabled
        }

        private fun updateDynamicSocksPort(enabled: Boolean) {
            socksPort?.isEnabled = !enabled
        }
    }

    class CoreSettingsFragment : BaseSettingsFragment() {
        private val mux by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_MUX_ENABLED) }
        private val muxConcurrency by lazy { findPreference<EditTextPreference>(AppConfig.PREF_MUX_CONCURRENCY) }
        private val muxXudpConcurrency by lazy { findPreference<EditTextPreference>(AppConfig.PREF_MUX_XUDP_CONCURRENCY) }
        private val muxXudpQuic by lazy { findPreference<ListPreference>(AppConfig.PREF_MUX_XUDP_QUIC) }

        private val fragment by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_FRAGMENT_ENABLED) }
        private val fragmentPackets by lazy { findPreference<ListPreference>(AppConfig.PREF_FRAGMENT_PACKETS) }
        private val fragmentLength by lazy { findPreference<EditTextPreference>(AppConfig.PREF_FRAGMENT_LENGTH) }
        private val fragmentInterval by lazy { findPreference<EditTextPreference>(AppConfig.PREF_FRAGMENT_INTERVAL) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            super.onCreatePreferences(bundle, s)
            addPreferencesFromResource(R.xml.pref_settings_core)
            initPreferenceSummaries()

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

            // Initialize visibility immediately to avoid flicker
            updateMux(MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false))
            updateFragment(MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false))
        }

        private fun updateMux(enabled: Boolean) {
            muxConcurrency?.isVisible = enabled
            muxXudpConcurrency?.isVisible = enabled
            muxXudpQuic?.isVisible = enabled
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
            fragmentPackets?.isVisible = enabled
            fragmentLength?.isVisible = enabled
            fragmentInterval?.isVisible = enabled
        }
    }

    class SubscriptionSettingsFragment : BaseSettingsFragment() {
        private val autoUpdateCheck by lazy { findPreference<CheckBoxPreference>(AppConfig.SUBSCRIPTION_AUTO_UPDATE) }
        private val autoUpdateInterval by lazy { findPreference<EditTextPreference>(AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            super.onCreatePreferences(bundle, s)
            addPreferencesFromResource(R.xml.pref_settings_subscription)
            initPreferenceSummaries()

            autoUpdateCheck?.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as Boolean
                autoUpdateCheck?.isChecked = value
                autoUpdateInterval?.isEnabled = value
                autoUpdateInterval?.text?.toLongEx()?.let {
                    if (newValue) configureUpdateTask(it) else cancelUpdateTask()
                }
                true
            }

            autoUpdateInterval?.isEnabled = MmkvManager.decodeSettingsBool(AppConfig.SUBSCRIPTION_AUTO_UPDATE, false)
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
    }

    class AdvancedSettingsFragment : BaseSettingsFragment() {
        private val mode by lazy { findPreference<ListPreference>(AppConfig.PREF_MODE) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            super.onCreatePreferences(bundle, s)
            addPreferencesFromResource(R.xml.pref_settings_advanced)
            initPreferenceSummaries()

            mode?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                true
            }
            mode?.dialogLayoutResource = R.layout.preference_with_help_link
        }
    }

    fun onModeHelpClicked(view: View) {
        Utils.openUri(this, AppConfig.APP_WIKI_MODE)
    }
}
