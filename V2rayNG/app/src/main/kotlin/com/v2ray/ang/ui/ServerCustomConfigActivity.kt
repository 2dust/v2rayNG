package com.v2ray.ang.ui

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import kotlinx.android.synthetic.main.activity_server_custom_config.*
import me.drakeet.support.toast.ToastCompat

class ServerCustomConfigActivity : BaseActivity() {

    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }
    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_custom_config)
        title = getString(R.string.title_server)

        val config = MmkvManager.decodeServerConfig(editGuid)
        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * bingding seleced server config
     */
    private fun bindingServer(config: ServerConfig): Boolean {
        et_remarks.text = Utils.getEditable(config.remarks)
        val raw = serverRawStorage?.decodeString(editGuid)
        if (raw.isNullOrBlank()) {
            tv_content.text = Utils.getEditable(config.fullConfig?.toPrettyPrinting().orEmpty())
        } else {
            tv_content.text = Utils.getEditable(raw)
        }
        return true
    }

    /**
     * clear or init server config
     */
    private fun clearServer(): Boolean {
        et_remarks.text = null
        return true
    }

    /**
     * save server config
     */
    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(et_remarks.text.toString())) {
            toast(R.string.server_lab_remarks)
            return false
        }

        val v2rayConfig = try {
            Gson().fromJson(tv_content.text.toString(), V2rayConfig::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            ToastCompat.makeText(this, "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ServerConfig.create(EConfigType.CUSTOM)
        config.remarks = et_remarks.text.toString().trim()
        config.fullConfig = v2rayConfig

        MmkvManager.encodeServerConfig(editGuid, config)
        serverRawStorage?.encode(editGuid, tv_content.text.toString())
        toast(R.string.toast_success)
        finish()
        return true
    }

    /**
     * save server config
     */
    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        MmkvManager.removeServer(editGuid)
                        finish()
                    }
                    .show()
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delButton = menu?.findItem(R.id.del_config)
        val saveButton = menu?.findItem(R.id.save_config)

        if (editGuid.isNotEmpty()) {
            if (isRunning) {
                delButton?.isVisible = false
                saveButton?.isVisible = false
            }
        } else {
            delButton?.isVisible = false
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
