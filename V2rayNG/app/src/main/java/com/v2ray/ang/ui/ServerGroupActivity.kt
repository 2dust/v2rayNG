package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityServerGroupBinding
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
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
        updateTvFocusNavigation()
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

        config.description = "${binding.spPolicyGroupType.selectedItem} - ${binding.spPolicyGroupSubId.selectedItem} - ${config.policyGroupFilter}"

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

        findViewById<View>(R.id.toolbar)?.post {
            updateTvFocusNavigation()
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

    private fun updateTvFocusNavigation() {
        val formViews = listOf(
            binding.etRemarks,
            binding.spPolicyGroupType,
            binding.spPolicyGroupSubId,
            binding.etPolicyGroupFilter
        ).filter { it.visibility == View.VISIBLE && it.isEnabled }

        val toolbarTargets = findToolbarActionTargets()
        val firstToolbarTarget = toolbarTargets.firstOrNull()
        val lastToolbarTarget = toolbarTargets.lastOrNull()

        formViews.forEachIndexed { index, view ->
            ensureViewId(view)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.nextFocusUpId = formViews.getOrNull(index - 1)?.id ?: lastToolbarTarget?.id ?: View.NO_ID
            view.nextFocusDownId = formViews.getOrNull(index + 1)?.id ?: firstToolbarTarget?.id ?: View.NO_ID
        }

        if (formViews.isNotEmpty()) {
            toolbarTargets.forEach { target ->
                ensureViewId(target)
                target.isFocusable = true
                target.isFocusableInTouchMode = true
                target.nextFocusDownId = formViews.first().id
                target.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && event.action == android.view.KeyEvent.ACTION_DOWN) {
                        formViews.first().requestFocus()
                        return@setOnKeyListener true
                    }
                    false
                }
            }
        }
    }

    private fun findToolbarActionTargets(): List<View> {
        val toolbar = findViewById<Toolbar>(R.id.toolbar) ?: return emptyList()
        val targets = mutableListOf<View>()

        fun collect(view: View) {
            if (view.visibility != View.VISIBLE) return

            if (view !== toolbar && view.isClickable && !view.contentDescription.isNullOrBlank()) {
                ensureViewId(view)
                targets.add(view)
            }

            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) {
                    collect(view.getChildAt(index))
                }
            }
        }

        collect(toolbar)
        return targets.distinctBy { it.id }
    }

    private fun ensureViewId(view: View) {
        if (view.id == View.NO_ID) {
            view.id = View.generateViewId()
        }
    }
}
