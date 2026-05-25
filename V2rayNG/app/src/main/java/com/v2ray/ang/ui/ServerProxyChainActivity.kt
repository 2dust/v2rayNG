package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ActivityServerProxyChainBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.util.Utils

class ServerProxyChainActivity : BaseActivity() {
    private val binding by lazy { ActivityServerProxyChainBinding.inflate(layoutInflater) }
    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy {
        intent.getStringExtra("subscriptionId")
    }
    private lateinit var memberAdapter: ServerProxyChainMemberAdapter

    private var allRemarks: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = EConfigType.PROXYCHAIN.toString())

        loadAvailableRemarks()
        setupRecycler()
        binding.fabAddProxyChainMember.setOnClickListener {
            addMemberRow()
        }

        val config = MmkvManager.decodeServerConfig(editGuid)
        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    private fun loadAvailableRemarks() {
        allRemarks = SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(
                EConfigType.CUSTOM,
                EConfigType.POLICYGROUP,
                EConfigType.PROXYCHAIN,
            )
        )
    }

    private fun setupRecycler() {
        memberAdapter = ServerProxyChainMemberAdapter(
            members = mutableListOf(),
            suggestions = allRemarks,
            adapterListener = ActivityAdapterListener()
        )
        binding.recyclerProxyChainMembers.layoutManager = LinearLayoutManager(this)
        binding.recyclerProxyChainMembers.adapter = memberAdapter
        ItemTouchHelper(SimpleItemTouchHelperCallback(memberAdapter)).attachToRecyclerView(binding.recyclerProxyChainMembers)
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(config.remarks)
        val rows = parseChainMembers(config.proxyChainProfiles)
        memberAdapter.replaceAll(rows)
        if (rows.isEmpty()) {
            memberAdapter.addRow()
        }
        return true
    }

    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        memberAdapter.replaceAll(listOf("", ""))
        return true
    }

    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(binding.etRemarks.text.toString())) {
            toast(R.string.server_lab_remarks)
            return false
        }

        val chainMembers = memberAdapter.getMembers().map { it.trim() }.filter { it.isNotEmpty() }
        if (chainMembers.size != memberAdapter.getMembers().size) {
            toast(R.string.server_proxy_chain_members_unselected)
            return false
        }
        if (chainMembers.size < 2) {
            toast(R.string.server_proxy_chain_members_insufficient)
            return false
        }

        val invalidMembers = chainMembers.filter { member ->
            val profile = SettingsManager.getServerViaRemarks(member)
            profile == null || profile.configType.isComplexType()
        }
        if (invalidMembers.isNotEmpty()) {
            toast(getString(R.string.server_proxy_chain_members_invalid, invalidMembers.joinToString(", ")))
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.PROXYCHAIN)
        config.remarks = binding.etRemarks.text.toString().trim()
        config.proxyChainProfiles = chainMembers.joinToString(",")
        config.description = chainMembers.joinToString(" -> ")

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        MmkvManager.encodeServerConfig(editGuid, config)
        if (isRunning) {
            SettingsChangeManager.makeRestartService()
        }
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            if (editGuid != MmkvManager.getSelectServer()) {
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                    AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            MmkvManager.removeServer(editGuid)
                            finish()
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            // do nothing
                        }
                        .show()
                } else {
                    MmkvManager.removeServer(editGuid)
                    finish()
                }
            } else {
                toast(R.string.toast_action_not_allowed)
            }
        }
        return true
    }

    private fun addMemberRow() {
        if (allRemarks.isEmpty()) {
            toast(R.string.toast_none_data)
            return
        }
        memberAdapter.addRow()
    }

    private fun parseChainMembers(raw: String?): List<String> {
        return raw.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private inner class ActivityAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            // Row selection is handled directly by AutoCompleteTextView in the adapter.
        }

        override fun onRemove(guid: String, position: Int) {
            memberAdapter.removeRow(position)
        }

        override fun onShare(url: String) {
        }

        override fun onRefreshData() {
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)

        val delButton = menu.findItem(R.id.del_config)
        delButton?.isVisible = editGuid.isNotEmpty() && !isRunning

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


