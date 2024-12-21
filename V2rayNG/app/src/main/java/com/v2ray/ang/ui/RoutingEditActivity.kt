package com.v2ray.ang.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityRoutingEditBinding
import com.v2ray.ang.dto.RulesetItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutingEditActivity : BaseActivity() {
    private val binding by lazy { ActivityRoutingEditBinding.inflate(layoutInflater) }
    private val position by lazy { intent.getIntExtra("position", -1) }

    private val outbound_tag: Array<out String> by lazy {
        resources.getStringArray(R.array.outbound_tag)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.routing_settings_rule_title)

        val rulesetItem = SettingsManager.getRoutingRuleset(position)
        if (rulesetItem != null) {
            bindingServer(rulesetItem)
        } else {
            clearServer()
        }
    }

    private fun bindingServer(rulesetItem: RulesetItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(rulesetItem.remarks)
        binding.chkLocked.isChecked = rulesetItem.locked == true
        binding.etDomain.text = Utils.getEditable(rulesetItem.domain?.joinToString(","))
        binding.etIp.text = Utils.getEditable(rulesetItem.ip?.joinToString(","))
        binding.etPort.text = Utils.getEditable(rulesetItem.port)
        binding.etProtocol.text = Utils.getEditable(rulesetItem.protocol?.joinToString(","))
        binding.etNetwork.text = Utils.getEditable(rulesetItem.network)
        val outbound = Utils.arrayFind(outbound_tag, rulesetItem.outboundTag)
        binding.spOutboundTag.setSelection(outbound)

        return true
    }

    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        binding.spOutboundTag.setSelection(0)
        return true
    }

    private fun saveServer(): Boolean {
        val rulesetItem = SettingsManager.getRoutingRuleset(position) ?: RulesetItem()

        rulesetItem.apply {
            remarks = binding.etRemarks.text.toString()
            locked = binding.chkLocked.isChecked
            domain = binding.etDomain.text.toString().takeIf { it.isNotEmpty() }
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ip = binding.etIp.text.toString().takeIf { it.isNotEmpty() }
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            protocol = binding.etProtocol.text.toString().takeIf { it.isNotEmpty() }
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            port = binding.etPort.text.toString().takeIf { it.isNotEmpty() }
            network = binding.etNetwork.text.toString().takeIf { it.isNotEmpty() }
            outboundTag = outbound_tag[binding.spOutboundTag.selectedItemPosition]
        }

        if (rulesetItem.remarks.isNullOrEmpty()) {
            toast(R.string.sub_setting_remarks)
            return false
        }

        SettingsManager.saveRoutingRuleset(position, rulesetItem)
        toast(R.string.toast_success)
        finish()
        return true
    }


    private fun deleteServer(): Boolean {
        if (position >= 0) {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        SettingsManager.removeRoutingRuleset(position)
                        launch(Dispatchers.Main) {
                            finish()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    // do nothing
                }
                .show()
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val del_config = menu.findItem(R.id.del_config)

        if (position < 0) {
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
