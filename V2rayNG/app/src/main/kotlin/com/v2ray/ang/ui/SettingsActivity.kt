package com.v2ray.ang.ui

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.InappBuyActivity
import com.v2ray.ang.R
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.extension.onClick
import com.v2ray.ang.util.Utils
import org.jetbrains.anko.act
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import libv2ray.Libv2ray

class SettingsActivity : BaseActivity() {
    companion object {
        //        const val PREF_BYPASS_MAINLAND = "pref_bypass_mainland"
        //        const val PREF_START_ON_BOOT = "pref_start_on_boot"
        const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
//        const val PREF_MUX_ENAimport libv2ray.Libv2rayBLED = "pref_mux_enabled"
        const val PREF_SPEED_ENABLED = "pref_speed_enabled"
        const val PREF_SNIFFING_ENABLED = "pref_sniffing_enabled"
        const val PREF_LOCAL_DNS_ENABLED = "pref_local_dns_enabled"
        const val PREF_REMOTE_DNS = "pref_remote_dns"
        const val PREF_DOMESTIC_DNS = "pref_domestic_dns"

//        const val PREF_SOCKS_PORT = "pref_socks_port"
//        const val PREF_LANCONN_PORT = "pref_lanconn_port"

        const val PREF_ROUTING_DOMAIN_STRATEGY = "pref_routing_domain_strategy"
        const val PREF_ROUTING_MODE = "pref_routing_mode"
        const val PREF_ROUTING_CUSTOM = "pref_routing_custom"
//        const val PREF_DONATE = "pref_donate"
        //        const val PREF_LICENSES = "pref_licenses"
//        const val PREF_FEEDBACK = "pref_feedback"
//        const val PREF_TG_GROUP = "pref_tg_group"
        const val PREF_VERSION = "pref_version"
        //        const val PREF_AUTO_RESTART = "pref_auto_restart"
        const val PREF_FORWARD_IPV6 = "pref_forward_ipv6"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.title_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
        val perAppProxy by lazy { findPreference(PREF_PER_APP_PROXY) as CheckBoxPreference }
        //        val autoRestart by lazy { findPreference(PREF_AUTO_RESTART) as CheckBoxPreference }
        val remoteDns by lazy { findPreference(PREF_REMOTE_DNS) as EditTextPreference }
        val domesticDns by lazy { findPreference(PREF_DOMESTIC_DNS) as EditTextPreference }

        val enableLocalDns by lazy { findPreference(PREF_LOCAL_DNS_ENABLED) as CheckBoxPreference }
        val forwardIpv6 by lazy { findPreference(PREF_FORWARD_IPV6) as CheckBoxPreference }

//        val socksPort by lazy { findPreference(PREF_SOCKS_PORT) as EditTextPreference }
//        val lanconnPort by lazy { findPreference(PREF_LANCONN_PORT) as EditTextPreference }

        val routingCustom: Preference by lazy { findPreference(PREF_ROUTING_CUSTOM) }
//        val donate: Preference by lazy { findPreference(PREF_DONATE) }
        //        val licenses: Preference by lazy { findPreference(PREF_LICENSES) }
//        val feedback: Preference by lazy { findPreference(PREF_FEEDBACK) }
//        val tgGroup: Preference by lazy { findPreference(PREF_TG_GROUP) }
        val version: Preference by lazy { findPreference(PREF_VERSION) }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_settings)

            routingCustom.onClick {
                startActivity<RoutingSettingsActivity>()
            }

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

            perAppProxy.setOnPreferenceClickListener {
                startActivity<PerAppProxyActivity>()
                perAppProxy.isChecked = true
                false
            }

            remoteDns.setOnPreferenceChangeListener { preference, any ->
                // remoteDns.summary = any as String
                val nval = any as String
                remoteDns.summary = if (nval == "") AppConfig.DNS_AGENT else nval
                true
            }
            domesticDns.setOnPreferenceChangeListener { preference, any ->
                // domesticDns.summary = any as String
                val nval = any as String
                domesticDns.summary = if (nval == "") AppConfig.DNS_DIRECT else nval
                true
            }
//            socksPort.setOnPreferenceChangeListener { preference, any ->
//                socksPort.summary = any as String
//                true
//            }
//            lanconnPort.setOnPreferenceChangeListener { preference, any ->
//                lanconnPort.summary = any as String
//                true
//            }

            version.summary = "${BuildConfig.VERSION_NAME} (${Libv2ray.checkVersionX()})"
        }

        override fun onStart() {
            super.onStart()

            perAppProxy.isChecked = defaultSharedPreferences.getBoolean(PREF_PER_APP_PROXY, false)
            remoteDns.summary = defaultSharedPreferences.getString(PREF_REMOTE_DNS, "")
            domesticDns.summary = defaultSharedPreferences.getString(PREF_DOMESTIC_DNS, "")

            if (remoteDns.summary == "") {
                remoteDns.summary = AppConfig.DNS_AGENT
            }

            if ( domesticDns.summary == "") {
                domesticDns.summary = AppConfig.DNS_DIRECT
            }

//            socksPort.summary = defaultSharedPreferences.getString(PREF_SOCKS_PORT, "10808")
//            lanconnPort.summary = defaultSharedPreferences.getString(PREF_LANCONN_PORT, "")

            defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onStop() {
            super.onStop()
            defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            when (key) {
//                PREF_AUTO_RESTART ->
//                    act.defaultDPreference.setPrefBoolean(key, sharedPreferences.getBoolean(key, false))

                PREF_PER_APP_PROXY ->
                    act.defaultDPreference.setPrefBoolean(key, sharedPreferences.getBoolean(key, false))
            }
        }
    }

}