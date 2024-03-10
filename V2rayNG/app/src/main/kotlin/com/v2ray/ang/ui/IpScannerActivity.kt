package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityIpScannerBinding
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.V2rayConfigUtil
import com.v2ray.ang.viewmodel.IpScannerViewModel

class IpScannerActivity : BaseActivity() {
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private lateinit var binding: ActivityIpScannerBinding
    private val adapter by lazy { IpScannerRecyclerAdapter(this) }
    private val ipScannerViewModel: IpScannerViewModel by viewModels()
    private var maxIps: Int = 0
    private var maxLatency: Long = 0L
    private var cdn: String = ""
    private var optMenu: Menu? = null
    var cleanIps: MutableList<Pair<String, Long>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        binding = ActivityIpScannerBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        title = getString(R.string.title_clean_ips)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        maxIps = intent.getStringExtra("maxIps").toString().toInt()
        maxLatency = intent.getStringExtra("maxLatency").toString().toLong()
        cdn = intent.getStringExtra("cdn").toString()

        setupViewModel()
    }

    private fun setupViewModel() {
        ipScannerViewModel.insertListAction.observe(this) { index ->
            cleanIps = ipScannerViewModel.cleanIps
            if (ipScannerViewModel.stopScan.value == false && index >= 0) {
                adapter.notifyItemInserted(index)
            }
        }

        ipScannerViewModel.removeListAction.observe(this) { index ->
            cleanIps = ipScannerViewModel.cleanIps
            if (ipScannerViewModel.stopScan.value == false && index >= 0) {
                adapter.notifyItemRemoved(index)
            }
        }

        ipScannerViewModel.updateListAction.observe(this) { index ->
            cleanIps = ipScannerViewModel.cleanIps
            if (ipScannerViewModel.stopScan.value == false && index >= 0) {
                adapter.notifyItemChanged(index)
                val totalCleanIps = cleanIps.filter { it.second in 1..maxLatency }.size
                if (totalCleanIps >= maxIps) {
                    cancelScan()
                }
            } else if (index < 0) {
                adapter.notifyDataSetChanged()
            }
        }

        ipScannerViewModel.startListenBroadcast()
    }

    override fun onResume() {
        super.onResume()

        startScan()
    }

    private fun startScan() {
        val guid = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER) ?: ""
        val config = V2rayConfigUtil.getV2rayConfig(application, guid)
        val jsonConfig: V2rayConfig = Gson().fromJson(config.content, V2rayConfig::class.java)
        if (!config.status || TextUtils.isEmpty(jsonConfig.getProxyOutbound()?.settings?.vnext?.get(0)?.address)) {
            toast(R.string.ip_scanner_no_valid_config_is_selected)
            return
        }

        val cidrList: Array<String>
        if (TextUtils.equals(cdn, "Cloudflare")) {
            cidrList = resources.getStringArray(R.array.cloudflare_cidr_list)
        } else {
            return
        }

        ipScannerViewModel.findCleanIps(cidrList, jsonConfig, maxIps, maxLatency)
    }

    private fun cancelScan() {
        ipScannerViewModel.stopScan.value = true
        MessageUtil.sendMsg2TestService(application, AppConfig.MSG_MEASURE_IP_CANCEL, "")
        optMenu?.findItem(R.id.cancel_scan)?.isVisible = false
        optMenu?.findItem(R.id.copy_all)?.isVisible = true
    }

    private fun copyAllIps() {
        if (cleanIps.size > 0) {
            Utils.setClipboard(this, cleanIps.joinToString("\n") { it.first })
            toast(R.string.toast_success)
        } else {
            toast(R.string.toast_failure)
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            cancelScan()
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        optMenu = menu
        menuInflater.inflate(R.menu.action_ip_scanner, menu)
        menu.findItem(R.id.copy_all)?.isVisible = false

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.cancel_scan -> {
            cancelScan()
            true
        }
        R.id.copy_all -> {
            copyAllIps()
            true
        }
        else -> {
            cancelScan()
            super.onOptionsItemSelected(item)
        }
    }
}
