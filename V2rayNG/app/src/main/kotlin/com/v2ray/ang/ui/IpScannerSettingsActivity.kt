package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.Menu
import android.widget.TextView
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityIpScannerSettingsBinding
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.V2rayConfigUtil

class IpScannerSettingsActivity : BaseActivity() {
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val ipScannerStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_IP_SCANNER, MMKV.MULTI_PROCESS_MODE) }
    private lateinit var binding: ActivityIpScannerSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }

        binding = ActivityIpScannerSettingsBinding.inflate(layoutInflater)
        binding.spCdnProvider.setSelection(ipScannerStorage.decodeInt(MmkvManager.KEY_IP_SCANNER_CDN_PROVIDER, 0))
        binding.etMaxIps.setText(ipScannerStorage.decodeString(MmkvManager.KEY_IP_SCANNER_MAX_IPS, "5"))
        binding.etMaxLatency.setText(ipScannerStorage.decodeString(MmkvManager.KEY_IP_SCANNER_MAX_LATENCY, "700"))

        val view = binding.root
        setContentView(view)

        title = getString(R.string.title_ip_scanner)
    }

    override fun onResume() {
        super.onResume()
        val guid = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER) ?: return
        val config = Gson().fromJson(V2rayConfigUtil.getV2rayConfig(application, guid).content, V2rayConfig::class.java)
        binding.etSelectedConfig.setText(config.remarks)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_ip_scanner_settings, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.start_scan -> {
            ipScannerStorage?.encode(MmkvManager.KEY_IP_SCANNER_CDN_PROVIDER, binding.spCdnProvider.selectedItemPosition)
            ipScannerStorage?.encode(MmkvManager.KEY_IP_SCANNER_MAX_IPS, binding.etMaxIps.text.toString())
            ipScannerStorage?.encode(MmkvManager.KEY_IP_SCANNER_MAX_LATENCY, binding.etMaxLatency.text.toString())

            val cdnProviders = resources.getStringArray(R.array.cdn_providers)
            val startIntent = Intent(this, IpScannerActivity::class.java)
            startIntent.putExtra("maxIps", binding.etMaxIps.text.toString())
            startIntent.putExtra("maxLatency", binding.etMaxLatency.text.toString())
            startIntent.putExtra("cdn", cdnProviders[binding.spCdnProvider.selectedItemPosition ?: 0])
            startActivity(startIntent)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
