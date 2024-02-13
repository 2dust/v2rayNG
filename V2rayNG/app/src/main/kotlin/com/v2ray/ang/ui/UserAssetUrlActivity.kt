package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityUserAssetUrlBinding
import com.v2ray.ang.dto.AssetUrlItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import java.io.File

class UserAssetUrlActivity : BaseActivity() {
    private lateinit var binding: ActivityUserAssetUrlBinding

    var del_config: MenuItem? = null
    var save_config: MenuItem? = null

    val extDir by lazy { File(Utils.userAssetPath(this)) }
    private val assetStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_ASSET, MMKV.MULTI_PROCESS_MODE) }
    private val editAssetId by lazy { intent.getStringExtra("assetId").orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserAssetUrlBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        title = getString(R.string.title_user_asset_add_url)

        val json = assetStorage?.decodeString(editAssetId)
        if (!json.isNullOrBlank()) {
            bindingAsset(Gson().fromJson(json, AssetUrlItem::class.java))
        } else {
            clearAsset()
        }
    }

    /**
     * bingding seleced asset config
     */
    private fun bindingAsset(assetItem: AssetUrlItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(assetItem.remarks)
        binding.etUrl.text = Utils.getEditable(assetItem.url)
        return true
    }

    /**
     * clear or init asset config
     */
    private fun clearAsset(): Boolean {
        binding.etRemarks.text = null
        binding.etUrl.text = null
        return true
    }

    /**
     * save asset config
     */
    private fun saveServer(): Boolean {
        val assetItem: AssetUrlItem
        val json = assetStorage?.decodeString(editAssetId)
        var assetId = editAssetId
        if (!json.isNullOrBlank()) {
            assetItem = Gson().fromJson(json, AssetUrlItem::class.java)

            // remove file associated with the asset
            val file = extDir.resolve(assetItem.remarks)
            if (file.exists()) {
                file.delete()
            }
        } else {
            assetId = Utils.getUuid()
            assetItem = AssetUrlItem()
        }

        assetItem.remarks = binding.etRemarks.text.toString()
        assetItem.url = binding.etUrl.text.toString()

        // check remarks unique
        val assetList = MmkvManager.decodeAssetUrls()
        if (assetList.any { it.second.remarks == assetItem.remarks && it.first != assetId }) {
            toast(R.string.msg_remark_is_duplicate)
            return false
        }


        if (TextUtils.isEmpty(assetItem.remarks)) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        if (TextUtils.isEmpty(assetItem.url)) {
            toast(R.string.title_url)
            return false
        }

        assetStorage?.encode(assetId, Gson().toJson(assetItem))
        toast(R.string.toast_success)
        finish()
        return true
    }

    /**
     * save server config
     */
    private fun deleteServer(): Boolean {
        if (editAssetId.isNotEmpty()) {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeAssetUrl(editAssetId)
                    finish()
                }
                .show()
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        del_config = menu.findItem(R.id.del_config)
        save_config = menu.findItem(R.id.save_config)

        if (editAssetId.isEmpty()) {
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