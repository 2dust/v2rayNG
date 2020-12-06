package com.v2ray.ang.ui

import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.preference.*
import android.view.View
import com.v2ray.ang.R
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.SettingsViewModel

class SettingsActivity : BaseActivity() {
    companion object {
        //        const val PREF_BYPASS_MAINLAND = "pref_bypass_mainland"
        //        const val PREF_START_ON_BOOT = "pref_start_on_boot"
        const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
//        const val PREF_MUX_ENAimport libv2ray.Libv2rayBLED = "pref_mux_enabled"
        const val PREF_SPEED_ENABLED = "pref_speed_enabled"
        const val PREF_SNIFFING_ENABLED = "pref_sniffing_enabled"
        const val PREF_PROXY_SHARING = "pref_proxy_sharing_enabled"
        const val PREF_LOCAL_DNS_ENABLED = "pref_local_dns_enabled"
        const val PREF_REMOTE_DNS = "pref_remote_dns"
        const val PREF_DOMESTIC_DNS = "pref_domestic_dns"

//        const val PREF_SOCKS_PORT = "pref_socks_port"
//        const val PREF_HTTP_PORT = "pref_http_port"

        const val PREF_ROUTING_DOMAIN_STRATEGY = "pref_routing_domain_strategy"
        const val PREF_ROUTING_MODE = "pref_routing_mode"
        const val PREF_ROUTING_CUSTOM = "pref_routing_custom"
//        const val PREF_DONATE = "pref_donate"
        //        const val PREF_LICENSES = "pref_licenses"
//        const val PREF_FEEDBACK = "pref_feedback"
//        const val PREF_TG_GROUP = "pref_tg_group"
        //        const val PREF_AUTO_RESTART = "pref_auto_restart"
        const val PREF_FORWARD_IPV6 = "pref_forward_ipv6"
    }

    private val settingsViewModel by lazy { ViewModelProviders.of(this).get(SettingsViewModel::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.title_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        settingsViewModel.startListenPreferenceChange()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val perAppProxy by lazy { findPreference(PREF_PER_APP_PROXY) as CheckBoxPreference }
        private val sppedEnabled by lazy { findPreference(PREF_SPEED_ENABLED) as CheckBoxPreference }
        private val sniffingEnabled by lazy { findPreference(PREF_SNIFFING_ENABLED) as CheckBoxPreference }
        private val proxySharing by lazy { findPreference(PREF_PROXY_SHARING) as CheckBoxPreference }
        private val domainStrategy by lazy { findPreference(PREF_ROUTING_DOMAIN_STRATEGY) as ListPreference }
        private val routingMode by lazy { findPreference(PREF_ROUTING_MODE) as ListPreference }

        private val forwardIpv6 by lazy { findPreference(PREF_FORWARD_IPV6) as CheckBoxPreference }
        private val enableLocalDns by lazy { findPreference(PREF_LOCAL_DNS_ENABLED) as CheckBoxPreference }
        private val domesticDns by lazy { findPreference(PREF_DOMESTIC_DNS) as EditTextPreference }
        private val remoteDns by lazy { findPreference(PREF_REMOTE_DNS) as EditTextPreference }

        //        val autoRestart by lazy { findPreference(PREF_AUTO_RESTART) as CheckBoxPreference }


//        val socksPort by lazy { findPreference(PREF_SOCKS_PORT) as EditTextPreference }
//        val httpPort by lazy { findPreference(PREF_HTTP_PORT) as EditTextPreference }

        private val routingCustom: Preference by lazy { findPreference(PREF_ROUTING_CUSTOM) }
//        val donate: Preference by lazy { findPreference(PREF_DONATE) }
        //        val licenses: Preference by lazy { findPreference(PREF_LICENSES) }
//        val feedback: Preference by lazy { findPreference(PREF_FEEDBACK) }
//        val tgGroup: Preference by lazy { findPreference(PREF_TG_GROUP) }

        private val mode by lazy { findPreference(AppConfig.PREF_MODE) as ListPreference }

        private fun restartProxy() {
            Utils.stopVService(requireContext())
            Utils.startVService(requireContext(), AngConfigManager.configs.index)
        }

        private fun isRunning(): Boolean {
            return false //TODO no point of adding logic now since Settings will be changed soon
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.pref_settings)

            perAppProxy.setOnPreferenceClickListener {
                if (isRunning()) {
                    Utils.stopVService(requireContext())
                }
                startActivity(Intent(activity, PerAppProxyActivity::class.java))
                perAppProxy.isChecked = true
                true
            }
            sppedEnabled.setOnPreferenceClickListener {
                if (isRunning())
                    restartProxy()
                true
            }
            sniffingEnabled.setOnPreferenceClickListener {
                if (isRunning())
                    restartProxy()
                true
            }

            proxySharing.setOnPreferenceClickListener {
                if (proxySharing.isChecked)
                    activity?.toast(R.string.toast_warning_pref_proxysharing)
                if (isRunning())
                    restartProxy()
                true
            }

            domainStrategy.setOnPreferenceChangeListener { _, _ ->
                if (isRunning())
                    restartProxy()
                true
            }
            routingMode.setOnPreferenceChangeListener { _, _ ->
                if (isRunning())
                    restartProxy()
                true
            }

            routingCustom.setOnPreferenceClickListener {
                if (isRunning())
                    Utils.stopVService(requireContext())
                startActivity(Intent(activity, RoutingSettingsActivity::class.java))
                false
            }

            forwardIpv6.setOnPreferenceClickListener {
                if (isRunning())
                    restartProxy()
                true
            }

            enableLocalDns.setOnPreferenceClickListener {
                if (isRunning())
                    restartProxy()
                true
            }


            domesticDns.setOnPreferenceChangeListener { _, any ->
                // domesticDns.summary = any as String
                val nval = any as String
                domesticDns.summary = if (nval == "") AppConfig.DNS_DIRECT else nval
                if (isRunning())
                    restartProxy()
                true
            }

            remoteDns.setOnPreferenceChangeListener { _, any ->
                // remoteDns.summary = any as String
                val nval = any as String
                remoteDns.summary = if (nval == "") AppConfig.DNS_AGENT else nval
                if (isRunning())
                    restartProxy()
                true
            }

            mode.setOnPreferenceChangeListener { _, newValue ->
                updatePerAppProxy(newValue.toString())
                true
            }
            mode.dialogLayoutResource = R.layout.preference_with_help_link

//            donate.onClick {
//                startActivity<InappBuyActivity>()
//            }

//            licenses.onClick {
//                val fragment = LicensesDialogFragment.Builder(act)
//                        .setNotices(R.raw.licenses)
//                        .setIncludeOwnLicense(false)
//                        .build()
//                fragment.show((act as AppCompatActivity).supportFragmentManager, null)
//            }
//
//            feedback.onClick {
//                Utils.openUri(activity, "https://github.com/2dust/v2rayNG/issues")
//            }
//            tgGroup.onClick {
//                //                Utils.openUri(activity, "https://t.me/v2rayN")
//                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg:resolve?domain=v2rayN"))
//                try {
//                    startActivity(intent)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    toast(R.string.toast_tg_app_not_found)
//                }
//            }


//            socksPort.setOnPreferenceChangeListener { preference, any ->
//                socksPort.summary = any as String
//                true
//            }
//            httpPort.setOnPreferenceChangeListener { preference, any ->
//                httpPort.summary = any as String
//                true
//            }
        }

        override fun onStart() {
            super.onStart()
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            updatePerAppProxy(defaultSharedPreferences.getString(AppConfig.PREF_MODE, "VPN"))
            remoteDns.summary = defaultSharedPreferences.getString(PREF_REMOTE_DNS, "")
            domesticDns.summary = defaultSharedPreferences.getString(PREF_DOMESTIC_DNS, "")

            if (remoteDns.summary == "") {
                remoteDns.summary = AppConfig.DNS_AGENT
            }

            if ( domesticDns.summary == "") {
                domesticDns.summary = AppConfig.DNS_DIRECT
            }

//            socksPort.summary = defaultSharedPreferences.getString(PREF_SOCKS_PORT, "10808")
//            lanconnPort.summary = defaultSharedPreferences.getString(PREF_HTTP_PORT, "")
        }

        private fun updatePerAppProxy(mode: String?) {
            if (mode == "VPN") {
                perAppProxy.isEnabled = true
                perAppProxy.isChecked = PreferenceManager.getDefaultSharedPreferences(activity)
                        .getBoolean(PREF_PER_APP_PROXY, false)
            } else {
                perAppProxy.isEnabled = false
                perAppProxy.isChecked = false
            }
        }
    }

    fun onModeHelpClicked(view: View) {
        Utils.openUri(this, AppConfig.v2rayNGWikiMode)
    }
}
