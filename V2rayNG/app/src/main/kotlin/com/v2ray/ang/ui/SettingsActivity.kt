package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.viewModels
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.multiprocess.RemoteWorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.service.SubscriptionUpdater
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.SettingsViewModel
import java.util.concurrent.TimeUnit

class SettingsActivity : BaseActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.title_settings)

        settingsViewModel.startListenPreferenceChange()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

        private val perAppProxy by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_PER_APP_PROXY) }
        private val localDns by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_LOCAL_DNS_ENABLED) }
        private val fakeDns by lazy { findPreference<CheckBoxPreference>(AppConfig.PREF_FAKE_DNS_ENABLED) }
        private val localDnsPort by lazy { findPreference<EditTextPreference>(AppConfig.PREF_LOCAL_DNS_PORT) }
        private val vpnDns by lazy { findPreference<EditTextPreference>(AppConfig.PREF_VPN_DNS) }

        private val routingCustom by lazy { findPreference<Preference>(AppConfig.PREF_ROUTING_CUSTOM) }

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

        private val socksPort by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_PORT) }
        private val httpPort by lazy { findPreference<EditTextPreference>(AppConfig.PREF_HTTP_PORT) }
        private val remoteDns by lazy { findPreference<EditTextPreference>(AppConfig.PREF_REMOTE_DNS) }
        private val domesticDns by lazy { findPreference<EditTextPreference>(AppConfig.PREF_DOMESTIC_DNS) }
        private val delayTestUrl by lazy { findPreference<EditTextPreference>(AppConfig.PREF_DELAY_TEST_URL) }
        private val mode by lazy { findPreference<ListPreference>(AppConfig.PREF_MODE) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.pref_settings)

            perAppProxy?.setOnPreferenceClickListener {
                startActivity(Intent(activity, PerAppProxyActivity::class.java))
                perAppProxy?.isChecked = true
                false
            }
            localDns?.setOnPreferenceChangeListener { _, any ->
                updateLocalDns(any as Boolean)
                true
            }
            localDnsPort?.setOnPreferenceChangeListener { _, any ->
                val nval = any as String
                localDnsPort?.summary =
                    if (TextUtils.isEmpty(nval)) AppConfig.PORT_LOCAL_DNS else nval
                true
            }
            vpnDns?.setOnPreferenceChangeListener { _, any ->
                vpnDns?.summary = any as String
                true
            }

            routingCustom?.setOnPreferenceClickListener {
                startActivity(Intent(activity, RoutingSettingsActivity::class.java))
                false
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
            fragmentPackets?.setOnPreferenceChangeListener { _, newValue ->
                updateFragmentPackets(newValue as String)
                true
            }
            fragmentLength?.setOnPreferenceChangeListener { _, newValue ->
                updateFragmentLength(newValue as String)
                true
            }
            fragmentInterval?.setOnPreferenceChangeListener { _, newValue ->
                updateFragmentInterval(newValue as String)
                true
            }

            autoUpdateCheck?.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as Boolean
                autoUpdateCheck?.isChecked = value
                autoUpdateInterval?.isEnabled = value
                autoUpdateInterval?.text?.toLong()?.let {
                    if (newValue) configureUpdateTask(it) else cancelUpdateTask()
                }
                true
            }
            autoUpdateInterval?.setOnPreferenceChangeListener { _, any ->
                var nval = any as String

                // It must be greater than 15 minutes because WorkManager couldn't run tasks under 15 minutes intervals
                nval =
                    if (TextUtils.isEmpty(nval) || nval.toLong() < 15) AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL else nval
                autoUpdateInterval?.summary = nval
                configureUpdateTask(nval.toLong())
                true
            }

            socksPort?.setOnPreferenceChangeListener { _, any ->
                val nval = any as String
                socksPort?.summary = if (TextUtils.isEmpty(nval)) AppConfig.PORT_SOCKS else nval
                true
            }
            httpPort?.setOnPreferenceChangeListener { _, any ->
                val nval = any as String
                httpPort?.summary = if (TextUtils.isEmpty(nval)) AppConfig.PORT_HTTP else nval
                true
            }
            remoteDns?.setOnPreferenceChangeListener { _, any ->
                val nval = any as String
                remoteDns?.summary = if (nval == "") AppConfig.DNS_PROXY else nval
                true
            }
            domesticDns?.setOnPreferenceChangeListener { _, any ->
                val nval = any as String
                domesticDns?.summary = if (nval == "") AppConfig.DNS_DIRECT else nval
                true
            }
            delayTestUrl?.setOnPreferenceChangeListener { _, any ->
                val nval = any as String
                delayTestUrl?.summary = if (nval == "") AppConfig.DelayTestUrl else nval
                true
            }
            mode?.setOnPreferenceChangeListener { _, newValue ->
                updateMode(newValue.toString())
                true
            }
            mode?.dialogLayoutResource = R.layout.preference_with_help_link
            //loglevel.summary = "LogLevel"

        }

        override fun onStart() {
            super.onStart()
            updateMode(settingsStorage.decodeString(AppConfig.PREF_MODE, "VPN"))
            localDns?.isChecked = settingsStorage.getBoolean(AppConfig.PREF_LOCAL_DNS_ENABLED, false)
            fakeDns?.isChecked = settingsStorage.getBoolean(AppConfig.PREF_FAKE_DNS_ENABLED, false)
            localDnsPort?.summary = settingsStorage.decodeString(AppConfig.PREF_LOCAL_DNS_PORT, AppConfig.PORT_LOCAL_DNS)
            vpnDns?.summary  = settingsStorage.decodeString(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN)

            updateMux(settingsStorage.getBoolean(AppConfig.PREF_MUX_ENABLED, false))
            mux?.isChecked = settingsStorage.getBoolean(AppConfig.PREF_MUX_ENABLED, false)
            muxConcurrency?.summary = settingsStorage.decodeString(AppConfig.PREF_MUX_CONCURRENCY, "8")
            muxXudpConcurrency?.summary = settingsStorage.decodeString(AppConfig.PREF_MUX_XUDP_CONCURRENCY, "8")

            updateFragment(settingsStorage.getBoolean(AppConfig.PREF_FRAGMENT_ENABLED, false))
            fragment?.isChecked = settingsStorage.getBoolean(AppConfig.PREF_FRAGMENT_ENABLED, false)
            fragmentPackets?.summary = settingsStorage.decodeString(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
            fragmentLength?.summary = settingsStorage.decodeString(AppConfig.PREF_FRAGMENT_LENGTH, "50-100")
            fragmentInterval?.summary = settingsStorage.decodeString(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20")

            autoUpdateCheck?.isChecked = settingsStorage.getBoolean(AppConfig.SUBSCRIPTION_AUTO_UPDATE, false)
            autoUpdateInterval?.summary = settingsStorage.decodeString(AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL,AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL)
            autoUpdateInterval?.isEnabled = settingsStorage.getBoolean(AppConfig.SUBSCRIPTION_AUTO_UPDATE, false)

            socksPort?.summary = settingsStorage.decodeString(AppConfig.PREF_SOCKS_PORT, AppConfig.PORT_SOCKS)
            httpPort?.summary = settingsStorage.decodeString(AppConfig.PREF_HTTP_PORT, AppConfig.PORT_HTTP)
            remoteDns?.summary = settingsStorage.decodeString(AppConfig.PREF_REMOTE_DNS, AppConfig.DNS_PROXY)
            domesticDns?.summary = settingsStorage.decodeString(AppConfig.PREF_DOMESTIC_DNS, AppConfig.DNS_DIRECT)
            delayTestUrl?.summary = settingsStorage.decodeString(AppConfig.PREF_DELAY_TEST_URL, AppConfig.DelayTestUrl)

            initSharedPreference()
        }

        private fun initSharedPreference() {
            listOf(
                localDnsPort,
                vpnDns,
                muxConcurrency,
                muxXudpConcurrency,
                fragmentLength,
                fragmentInterval,
                autoUpdateInterval,
                socksPort,
                httpPort,
                remoteDns,
                domesticDns,
                delayTestUrl
            ).forEach { key ->
                key?.text = key?.summary.toString()
            }

            listOf(
                AppConfig.PREF_SNIFFING_ENABLED,
            ).forEach { key ->
                findPreference<CheckBoxPreference>(key)?.isChecked =
                    settingsStorage.decodeBool(key, true)
            }

            listOf(
                AppConfig.PREF_ROUTE_ONLY_ENABLED,
                AppConfig.PREF_BYPASS_APPS,
                AppConfig.PREF_SPEED_ENABLED,
                AppConfig.PREF_CONFIRM_REMOVE,
                AppConfig.PREF_START_SCAN_IMMEDIATE,
                AppConfig.PREF_PREFER_IPV6,
                AppConfig.PREF_PROXY_SHARING,
                AppConfig.PREF_ALLOW_INSECURE
            ).forEach { key ->
                findPreference<CheckBoxPreference>(key)?.isChecked =
                    settingsStorage.decodeBool(key, false)
            }

            listOf(
                AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
                AppConfig.PREF_ROUTING_MODE,
                AppConfig.PREF_MUX_XUDP_QUIC,
                AppConfig.PREF_FRAGMENT_PACKETS,
                AppConfig.PREF_LANGUAGE,
                AppConfig.PREF_UI_MODE_NIGHT,
                AppConfig.PREF_LOGLEVEL,
                AppConfig.PREF_MODE
            ).forEach { key ->
                if (settingsStorage.decodeString(key) != null) {
                    findPreference<ListPreference>(key)?.value = settingsStorage.decodeString(key)
                }
            }
        }

        private fun updateMode(mode: String?) {
            val vpn = mode == "VPN"
            perAppProxy?.isEnabled = vpn
            perAppProxy?.isChecked = settingsStorage.getBoolean(AppConfig.PREF_PER_APP_PROXY, false)
            localDns?.isEnabled = vpn
            fakeDns?.isEnabled = vpn
            localDnsPort?.isEnabled = vpn
            vpnDns?.isEnabled = vpn
            if (vpn) {
                updateLocalDns(
                    settingsStorage.getBoolean(
                        AppConfig.PREF_LOCAL_DNS_ENABLED,
                        false
                    )
                )
            }
        }

        private fun updateLocalDns(enabled: Boolean) {
            fakeDns?.isEnabled = enabled
            localDnsPort?.isEnabled = enabled
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
                updateMuxConcurrency(settingsStorage.decodeString(AppConfig.PREF_MUX_CONCURRENCY, "8"))
                updateMuxXudpConcurrency(settingsStorage.decodeString(AppConfig.PREF_MUX_XUDP_CONCURRENCY, "8"))
            }
        }

        private fun updateMuxConcurrency(value: String?) {
            if (value == null) {
            } else {
                val concurrency = value.toIntOrNull() ?: 8
                muxConcurrency?.summary = concurrency.toString()
            }
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
            if (enabled) {
                updateFragmentPackets(settingsStorage.decodeString(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello"))
                updateFragmentLength(settingsStorage.decodeString(AppConfig.PREF_FRAGMENT_LENGTH, "50-100"))
                updateFragmentInterval(settingsStorage.decodeString(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20"))
            }
        }
        private fun updateFragmentPackets(value: String?) {
            fragmentPackets?.summary = value.toString()
        }
        private fun updateFragmentLength(value: String?) {
            fragmentLength?.summary = value.toString()
        }
        private fun updateFragmentInterval(value: String?) {
            fragmentInterval?.summary = value.toString()
        }
    }

    fun onModeHelpClicked(view: View) {
        Utils.openUri(this, AppConfig.v2rayNGWikiMode)
    }
}
