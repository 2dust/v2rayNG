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
import kotlinx.android.synthetic.main.activity_sub_edit.*

class SubEditActivity : BaseActivity() {

    var del_config: MenuItem? = null
    var save_config: MenuItem? = null

    private lateinit var configs: AngConfig
    private var edit_index: Int = -1 //当前编辑的

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_edit)

        configs = AngConfigManager.configs
        edit_index = intent.getIntExtra("position", -1)

        title = getString(R.string.title_sub_setting)

        if (edit_index >= 0) {
            bindingServer(configs.subItem[edit_index])
        } else {
            clearServer()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * bingding seleced server config
     */
    fun bindingServer(subItem: AngConfig.SubItemBean): Boolean {
        et_remarks.text = Utils.getEditable(subItem.remarks)
        et_url.text = Utils.getEditable(subItem.url)

        return true
    }

    /**
     * clear or init server config
     */
    fun clearServer(): Boolean {
        et_remarks.text = null
        et_url.text = null

        return true
    }

    /**
     * save server config
     */
    fun saveServer(): Boolean {
        val subItem: AngConfig.SubItemBean
        if (edit_index >= 0) {
            subItem = configs.subItem[edit_index]
        } else {
            subItem = AngConfig.SubItemBean()
        }

        subItem.remarks = et_remarks.text.toString()
        subItem.url = et_url.text.toString()

        if (TextUtils.isEmpty(subItem.remarks)) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        if (TextUtils.isEmpty(subItem.url)) {
            toast(R.string.sub_setting_url)
            return false
        }

        if (AngConfigManager.addSubItem(subItem, edit_index) == 0) {
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
                        if (AngConfigManager.removeSubItem(edit_index) == 0) {
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
