package com.v2ray.ang.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityRoutingSettingBinding
import com.v2ray.ang.dto.RulesetItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutingSettingActivity : BaseActivity() {
    private val binding by lazy { ActivityRoutingSettingBinding.inflate(layoutInflater) }

    var rulesets: MutableList<RulesetItem> = mutableListOf()
    private val adapter by lazy { RoutingSettingRecyclerAdapter(this) }
    private var mItemTouchHelper: ItemTouchHelper? = null
    private val routing_domain_strategy: Array<out String> by lazy {
        resources.getStringArray(R.array.routing_domain_strategy)
    }
    private val preset_rulesets: Array<out String> by lazy {
        resources.getStringArray(R.array.preset_rulesets)
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scanQRcodeForRulesets.launch(Intent(this, ScannerActivity::class.java))
        } else {
            toast(R.string.toast_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.routing_settings_title)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        binding.tvDomainStrategySummary.text = getDomainStrategy()
        binding.layoutDomainStrategy.setOnClickListener {
            setDomainStrategy()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_routing_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.add_rule -> startActivity(Intent(this, RoutingEditActivity::class.java)).let { true }
        R.id.import_predefined_rulesets -> importPredefined().let { true }
        R.id.import_rulesets_from_clipboard -> importFromClipboard().let { true }
        R.id.import_rulesets_from_qrcode -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA).let { true }
        R.id.export_rulesets_to_clipboard -> export2Clipboard().let { true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun getDomainStrategy(): String {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: routing_domain_strategy.first()
    }

    private fun setDomainStrategy() {
        android.app.AlertDialog.Builder(this).setItems(routing_domain_strategy.asList().toTypedArray()) { _, i ->
            try {
                val value = routing_domain_strategy[i]
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                binding.tvDomainStrategySummary.text = value
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to set domain strategy", e)
            }
        }.show()
    }

    private fun importPredefined() {
        AlertDialog.Builder(this).setItems(preset_rulesets.asList().toTypedArray()) { _, i ->
            AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    try {
                        lifecycleScope.launch(Dispatchers.IO) {
                            SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, i)
                            launch(Dispatchers.Main) {
                                refreshData()
                                toastSuccess(R.string.toast_success)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do nothing
                }
                .show()
        }.show()
    }

    private fun importFromClipboard() {
        AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val clipboard = try {
                    Utils.getClipboard(this)
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Failed to get clipboard content", e)
                    toastError(R.string.toast_failure)
                    return@setPositiveButton
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(clipboard)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            refreshData()
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do nothing
            }
            .show()
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            toastError(R.string.toast_failure)
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            toastSuccess(R.string.toast_success)
        }
    }

    private val scanQRcodeForRulesets = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importRulesetsFromQRcode(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private fun importRulesetsFromQRcode(qrcode: String?): Boolean {
        AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(qrcode)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            refreshData()
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do nothing
            }
            .show()
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        rulesets.clear()
        rulesets.addAll(MmkvManager.decodeRoutingRulesets() ?: mutableListOf())
        adapter.notifyDataSetChanged()
    }
}