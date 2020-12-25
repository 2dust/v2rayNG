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
import kotlinx.android.synthetic.main.activity_server3.*

class Server3Activity : BaseActivity() {
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
        resources.getStringArray(R.array.ss_securitys)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server3)

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
        val security = Utils.arrayFind(securitys, vmess.security)
        if (security >= 0) {
            sp_security.setSelection(security)
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
        sp_security.setSelection(0)

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
        vmess.security = securitys[sp_security.selectedItemPosition]

        if (TextUtils.isEmpty(vmess.remarks)) {
            toast(R.string.server_lab_remarks)
            return false
        }
        if (TextUtils.isEmpty(vmess.address)) {
            toast(R.string.server_lab_address3)
            return false
        }
        if (TextUtils.isEmpty(vmess.port.toString()) || vmess.port <= 0) {
            toast(R.string.server_lab_port3)
            return false
        }
        if (TextUtils.isEmpty(vmess.id)) {
            toast(R.string.server_lab_id3)
            return false
        }

        if (AngConfigManager.addShadowsocksServer(vmess, edit_index) == 0) {
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
