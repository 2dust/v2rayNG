package com.v2ray.ang.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityRoutingEditBinding
import com.v2ray.ang.dto.RulesetItem
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutingEditActivity : BaseActivity() {
    private val binding by lazy { ActivityRoutingEditBinding.inflate(layoutInflater) }
    private val position by lazy { intent.getIntExtra("position", -1) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.routing_settings_rule_title))

        setupOutboundTagInput()

        val rulesetItem = SettingsManager.getRoutingRuleset(position)
        if (rulesetItem != null) {
            bindingServer(rulesetItem)
        } else {
            clearServer()
        }

        SettingsManager.canUseProcessRouting().let { canUse ->
            binding.etProcess.isEnabled = canUse
        }
    }

    /**
     * Sets up the AutoCompleteTextView for outbound tag:
     * suggestions = built-in tags (proxy/direct/block) + all existing profile remarks.
     * The dropdown button triggers showing the full list without typing.
     */
    private fun setupOutboundTagInput() {
        val profileRemarks = MmkvManager.decodeAllServerList()
            .mapNotNull { id -> MmkvManager.decodeServerConfig(id)?.remarks }
            .filter { it.isNotBlank() }

        val suggestions = (BUILTIN_OUTBOUND_TAGS.toList() + profileRemarks).distinct()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suggestions)
        binding.spOutboundTag.setAdapter(adapter)
        // threshold=0 means show all suggestions even before typing; still need focus+request
        binding.spOutboundTag.threshold = 0

        // Dropdown arrow button shows the full suggestion list
        binding.btnOutboundTagDropdown.setOnClickListener {
            binding.spOutboundTag.requestFocus()
            binding.spOutboundTag.showDropDown()
        }
        // Also show on field click when it already has focus
        binding.spOutboundTag.setOnClickListener {
            binding.spOutboundTag.showDropDown()
        }
    }

    private fun bindingServer(rulesetItem: RulesetItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(rulesetItem.remarks)
        binding.chkLocked.isChecked = rulesetItem.locked == true
        binding.etDomain.text = Utils.getEditable(rulesetItem.domain?.joinToString(","))
        binding.etIp.text = Utils.getEditable(rulesetItem.ip?.joinToString(","))
        binding.etProcess.text = Utils.getEditable(rulesetItem.process?.joinToString(","))
        binding.etPort.text = Utils.getEditable(rulesetItem.port)
        binding.etProtocol.text = Utils.getEditable(rulesetItem.protocol?.joinToString(","))
        binding.etNetwork.text = Utils.getEditable(rulesetItem.network)
        // Set text directly; filter won't fire because we're not using setText(filter=true)
        binding.spOutboundTag.setText(rulesetItem.outboundTag, false)
        return true
    }

    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        binding.spOutboundTag.setText(BUILTIN_OUTBOUND_TAGS.first(), false)
        return true
    }

    private fun saveServer(): Boolean {
        val rulesetItem = SettingsManager.getRoutingRuleset(position) ?: RulesetItem()

        rulesetItem.apply {
            remarks = binding.etRemarks.text.toString()
            locked = binding.chkLocked.isChecked
            domain = binding.etDomain.text.toString().nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ip = binding.etIp.text.toString().nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            process = binding.etProcess.text.toString().nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            protocol = binding.etProtocol.text.toString().nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            port = binding.etPort.text.toString().nullIfBlank()
            network = binding.etNetwork.text.toString().nullIfBlank()
            outboundTag = binding.spOutboundTag.text.toString().trim().ifEmpty { TAG_PROXY }
        }

        if (rulesetItem.remarks.isNullOrEmpty()) {
            toast(R.string.sub_setting_remarks)
            return false
        }

        SettingsManager.saveRoutingRuleset(position, rulesetItem)
        toastSuccess(R.string.toast_success)
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
        val delConfig = menu.findItem(R.id.del_config)

        if (position < 0) {
            delConfig?.isVisible = false
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
