package com.v2ray.ang.ui

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.v2ray.ang.R
import com.v2ray.ang.dto.AngConfig
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.Utils
import kotlinx.android.synthetic.main.activity_server.*

class ServerActivity : BaseActivity() {
    companion object {
        private const val REQUEST_SCAN = 1
    }

    var del_config: MenuItem? = null
    var save_config: MenuItem? = null

    private lateinit var configs: AngConfig
    private var edit_index: Int = -1 //当前编辑的服务器
    private var edit_guid: String = ""
    private var isRunning: Boolean = false
    private val securitys: Array<out String> by lazy {
        resources.getStringArray(R.array.securitys)
    }
    private val networks: Array<out String> by lazy {
        resources.getStringArray(R.array.networks)
    }
    private val headertypes: Array<out String> by lazy {
        resources.getStringArray(R.array.headertypes)
    }
    private val streamsecuritys: Array<out String> by lazy {
        resources.getStringArray(R.array.streamsecuritys)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        configs = AngConfigManager.configs
        edit_index = intent.getIntExtra("position", -1)
        isRunning = intent.getBooleanExtra("isRunning", false)
        title = getString(R.string.title_server)

        if (edit_index >= 0) {
            edit_guid = configs.vmess[edit_index].guid
            bindingServer(configs.vmess[edit_index])
        } else {
            clearServer()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * bingding seleced server config
     */
    fun bindingServer(vmess: AngConfig.VmessBean): Boolean {
        et_remarks.text = Utils.getEditable(vmess.remarks)

        et_address.text = Utils.getEditable(vmess.address)
        et_port.text = Utils.getEditable(vmess.port.toString())
        et_id.text = Utils.getEditable(vmess.id)
        et_alterId.text = Utils.getEditable(vmess.alterId.toString())

        val security = Utils.arrayFind(securitys, vmess.security)
        if (security >= 0) {
            sp_security.setSelection(security)
        }
        val network = Utils.arrayFind(networks, vmess.network)
        if (network >= 0) {
            sp_network.setSelection(network)
        }

        val headerType = Utils.arrayFind(headertypes, vmess.headerType)
        if (headerType >= 0) {
            sp_header_type.setSelection(headerType)
        }
        et_request_host.text = Utils.getEditable(vmess.requestHost)
        et_path.text = Utils.getEditable(vmess.path)

        val streamSecurity = Utils.arrayFind(streamsecuritys, vmess.streamSecurity)
        if (streamSecurity >= 0) {
            sp_stream_security.setSelection(streamSecurity)
        }
        return true
    }

    /**
     * clear or init server config
     */
    fun clearServer(): Boolean {
        et_remarks.text = null
        et_address.text = null
        et_port.text = Utils.getEditable("10086")
        et_id.text = null
        et_alterId.text = Utils.getEditable("64")
        sp_security.setSelection(0)
        sp_network.setSelection(0)

        sp_header_type.setSelection(0)
        et_request_host.text = null
        et_path.text = null
        sp_stream_security.setSelection(0)
        return true
    }

    /**
     * save server config
     */
    fun saveServer(): Boolean {
        val vmess: AngConfig.VmessBean
        if (edit_index >= 0) {
            vmess = configs.vmess[edit_index]
        } else {
            vmess = AngConfig.VmessBean()
        }

        vmess.guid = edit_guid
        vmess.remarks = et_remarks.text.toString()
        vmess.address = et_address.text.toString()
        vmess.port = Utils.parseInt(et_port.text.toString())
        vmess.id = et_id.text.toString()
        vmess.alterId = Utils.parseInt(et_alterId.text.toString())
        vmess.security = securitys[sp_security.selectedItemPosition]
        vmess.network = networks[sp_network.selectedItemPosition]

        vmess.headerType = headertypes[sp_header_type.selectedItemPosition]
        vmess.requestHost = et_request_host.text.toString()
        vmess.path = et_path.text.toString()
        vmess.streamSecurity = streamsecuritys[sp_stream_security.selectedItemPosition]

        if (TextUtils.isEmpty(vmess.remarks)) {
            toast(R.string.server_lab_remarks)
            return false
        }
        if (TextUtils.isEmpty(vmess.address)) {
            toast(R.string.server_lab_address)
            return false
        }
        if (TextUtils.isEmpty(vmess.port.toString()) || vmess.port <= 0) {
            toast(R.string.server_lab_port)
            return false
        }
        if (TextUtils.isEmpty(vmess.id)) {
            toast(R.string.server_lab_id)
            return false
        }
        if (TextUtils.isEmpty(vmess.alterId.toString()) || vmess.alterId < 0) {
            toast(R.string.server_lab_alterid)
            return false
        }

        if (AngConfigManager.addServer(vmess, edit_index) == 0) {
            AngConfigManager.genStoreV2rayConfigIfActive(edit_index)
            toast(R.string.toast_success)
            finish()
            return true
        } else {
            toast(R.string.toast_failure)
            return false
        }
    }

    /**
     * save server config
     */
    fun deleteServer(): Boolean {
        if (edit_index >= 0) {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (AngConfigManager.removeServer(edit_index) == 0) {
                            toast(R.string.toast_success)
                            finish()
                        } else {
                            toast(R.string.toast_failure)
                        }
                    }
                    .show()
        } else {
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        del_config = menu?.findItem(R.id.del_config)
        save_config = menu?.findItem(R.id.save_config)

        if (edit_index >= 0) {
            if (isRunning) {
                if (edit_index == configs.index) {
                    del_config?.isVisible = false
                    save_config?.isVisible = false
                }
            }
        } else {
            del_config?.isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            deleteServer()
            true
        }
        R.id.save_config -> {
            saveServer()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
