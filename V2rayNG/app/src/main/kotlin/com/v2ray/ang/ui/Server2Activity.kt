package com.v2ray.ang.ui

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.google.gson.Gson
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.dto.AngConfig
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.Utils
import kotlinx.android.synthetic.main.activity_server2.*
import java.lang.Exception

class Server2Activity : BaseActivity() {
    companion object {
        private const val REQUEST_SCAN = 1
    }

    var del_config: MenuItem? = null
    var save_config: MenuItem? = null

    private lateinit var configs: AngConfig
    private var edit_index: Int = -1 //当前编辑的服务器
    private var edit_guid: String = ""
    private var isRunning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server2)

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
        tv_content.text = Editable.Factory.getInstance().newEditable(defaultDPreference.getPrefString(AppConfig.ANG_CONFIG + edit_guid, ""))
        return true
    }

    /**
     * clear or init server config
     */
    fun clearServer(): Boolean {
        et_remarks.text = null
        return true
    }

    /**
     * save server config
     */
    fun saveServer(): Boolean {
        val vmess = configs.vmess[edit_index]

        vmess.remarks = et_remarks.text.toString()

        if (TextUtils.isEmpty(vmess.remarks)) {
            toast(R.string.server_lab_remarks)
            return false
        }

        try {
            Gson().fromJson<Object>(tv_content.text.toString(), Object::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            toast(R.string.toast_malformed_josn)
            return false
        }

        if (AngConfigManager.addCustomServer(vmess, edit_index) == 0) {
            //update config
            defaultDPreference.setPrefString(AppConfig.ANG_CONFIG + edit_guid, tv_content.text.toString())
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
