package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityServerGroupBinding
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils

class ServerGroupActivity : BaseActivity() {
    private val binding by lazy { ActivityServerGroupBinding.inflate(layoutInflater) }

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy {
        intent.getStringExtra("subscriptionId")
    }
    private val subIds = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(binding.root)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = EConfigType.POLICYGROUP.toString())

        val config = MmkvManager.decodeServerConfig(editGuid)
        populateSubscriptionSpinner()

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    /**
     * Binding selected server config
     */
    private fun bindingServer(config: ProfileItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(config.remarks)
        binding.etPolicyGroupFilter.text = Utils.getEditable(config.policyGroupFilter)

        val type = config.policyGroupType?.toInt() ?: 0
        binding.spPolicyGroupType.setSelection(type)

        val pos = subIds.indexOf(config.policyGroupSubscriptionId ?: "").let { if (it >= 0) it else 0 }
        binding.spPolicyGroupSubId.setSelection(pos)

        return true
    }

    /**
     * clear or init server config
     */
    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        binding.etPolicyGroupFilter.text = null

        if (subscriptionId.isNotNullEmpty()) {
            val pos = subIds.indexOf(subscriptionId).let { if (it >= 0) it else 0 }
            binding.spPolicyGroupSubId.setSelection(pos)
        }
        return true
    }

    /**
     * save server config
     */
    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(binding.etRemarks.text.toString())) {
            toast(R.string.server_lab_remarks)
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.POLICYGROUP)
        config.remarks = binding.etRemarks.text.toString().trim()
        config.policyGroupFilter = binding.etPolicyGroupFilter.text.toString().trim()

        config.policyGroupType = binding.spPolicyGroupType.selectedItemPosition.toString()

        val selPos = binding.spPolicyGroupSubId.selectedItemPosition
        config.policyGroupSubscriptionId = if (selPos >= 0 && selPos < subIds.size) subIds[selPos] else null

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        config.description =  "${binding.spPolicyGroupType.selectedItem} - ${binding.spPolicyGroupSubId.selectedItem} - ${config.policyGroupFilter}"

        MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
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
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    // do nothing
                }
                .show()
        }
        return true
    }

    private fun populateSubscriptionSpinner() {
        val subs = MmkvManager.decodeSubscriptions()
        val displayList = mutableListOf(getString(R.string.filter_config_all)) //none
        subIds.clear()
        subIds.add("") // index 0 => All
        subs.forEach { sub ->
            val name = when {
                sub.subscription.remarks.isNotBlank() -> sub.subscription.remarks
                else -> sub.guid
            }
            displayList.add(name)
            subIds.add(sub.guid)
        }
        val subAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayList)
        subAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spPolicyGroupSubId.adapter = subAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delButton = menu.findItem(R.id.del_config)
        val saveButton = menu.findItem(R.id.save_config)

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
