package com.v2ray.ang.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivitySubSettingBinding
import com.v2ray.ang.databinding.ItemRecyclerUserAssetBinding
import com.v2ray.ang.dto.AssetUrlItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.text.DateFormat
import java.util.*

class UserAssetActivity : BaseActivity() {
    private lateinit var binding: ActivitySubSettingBinding
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val assetStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_ASSET, MMKV.MULTI_PROCESS_MODE) }

    val extDir by lazy { File(Utils.userAssetPath(this)) }
    val builtInGeoFiles = arrayOf("geosite.dat", "geoip.dat")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubSettingBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        title = getString(R.string.title_user_asset_setting)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = UserAssetAdapter()
    }

    override fun onResume() {
        super.onResume()
        binding.recyclerView.adapter?.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_asset, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_file -> {
            showFileChooser()
            true
        }

        R.id.add_url -> {
            val intent = Intent(this, UserAssetUrlActivity::class.java)
            startActivity(intent)
            true
        }
        R.id.download_file -> {
            downloadGeoFiles()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun showFileChooser() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        RxPermissions(this)
            .request(permission)
            .subscribe {
            if (it) {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)

                try {
                    chooseFile.launch(
                        Intent.createChooser(
                            intent,
                            getString(R.string.title_file_chooser)
                        )
                    )
                } catch (ex: android.content.ActivityNotFoundException) {
                    toast(R.string.toast_require_file_manager)
                }
            } else
                toast(R.string.toast_permission_denied)
        }
    }

    private val chooseFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { it ->
            val uri = it.data?.data
            if (it.resultCode == RESULT_OK && uri != null) {
                val assetId = Utils.getUuid()
                try {
                    val assetItem = AssetUrlItem(
                        getCursorName(uri) ?: uri.toString(),
                        "file"
                    )

                    // check remarks unique
                    val assetList = MmkvManager.decodeAssetUrls()
                    if (assetList.any { it.second.remarks == assetItem.remarks && it.first != assetId }) {
                        toast(R.string.msg_remark_is_duplicate)
                        return@registerForActivityResult
                    }
                    assetStorage?.encode(assetId, Gson().toJson(assetItem))
                    copyFile(uri)
                } catch (e: Exception) {
                    toast(R.string.toast_asset_copy_failed)
                    MmkvManager.removeAssetUrl(assetId)
                }
            }
        }

    private fun copyFile(uri: Uri): String {
        val targetFile = File(extDir, getCursorName(uri) ?: uri.toString())
        contentResolver.openInputStream(uri).use { inputStream ->
            targetFile.outputStream().use { fileOut ->
                inputStream?.copyTo(fileOut)
                toast(R.string.toast_success)
                binding.recyclerView.adapter?.notifyDataSetChanged()
            }
        }
        return targetFile.path
    }

    private fun getCursorName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.let { cursor ->
            cursor.run {
                if (moveToFirst()) getString(getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            }.also { cursor.close() }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun downloadGeoFiles() {
        val httpPort = Utils.parseInt(settingsStorage?.decodeString(AppConfig.PREF_HTTP_PORT), AppConfig.PORT_HTTP.toInt())

        toast(R.string.msg_downloading_content)
        var assets = MmkvManager.decodeAssetUrls()
        assets = addBuiltInGeoItems(assets)

        assets.forEach {
            //toast(getString(R.string.msg_downloading_content) + it)
            lifecycleScope.launch(Dispatchers.IO) {
                var result = downloadGeo(it.second, 60000, httpPort)
                if (!result) {
                    result = downloadGeo(it.second, 60000, 0)
                }
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.toast_success) + " " + it.second.remarks)
                        binding.recyclerView.adapter?.notifyDataSetChanged()
                    } else {
                        toast(getString(R.string.toast_failure) + " " + it.second.remarks)
                    }
                }
            }
        }
    }

    private fun downloadGeo(item: AssetUrlItem, timeout: Int, httpPort: Int): Boolean {
        val targetTemp = File(extDir, item.remarks + "_temp")
        val target = File(extDir, item.remarks)
        var conn: HttpURLConnection? = null
        //Log.d(AppConfig.ANG_PACKAGE, url)

        try {
            conn = if (httpPort == 0) {
                URL(item.url).openConnection() as HttpURLConnection
            } else {
                URL(item.url).openConnection(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress("127.0.0.1", httpPort)
                    )
                ) as HttpURLConnection
            }
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            val inputStream = conn.inputStream
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                FileOutputStream(targetTemp).use { output ->
                    inputStream.copyTo(output)
                }

                targetTemp.renameTo(target)
            }
            return true
        } catch (e: Exception) {
            Log.e(AppConfig.ANG_PACKAGE, Log.getStackTraceString(e))
            return false
        } finally {
            conn?.disconnect()
        }
    }
    private fun addBuiltInGeoItems(assets: List<Pair<String, AssetUrlItem>>): List<Pair<String, AssetUrlItem>> {
        val list = mutableListOf<Pair<String, AssetUrlItem>>()
        builtInGeoFiles
            .filter { geoFile -> assets.none { it.second.remarks == geoFile } }
            .forEach { 
                list.add(Utils.getUuid() to AssetUrlItem(
                    it,
                    AppConfig.geoUrl + it
                ))
            }

        return list + assets
    }

    inner class UserAssetAdapter : RecyclerView.Adapter<UserAssetViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAssetViewHolder {
            return UserAssetViewHolder(
                ItemRecyclerUserAssetBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false)
            )
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: UserAssetViewHolder, position: Int) {
            var assets = MmkvManager.decodeAssetUrls();
            assets = addBuiltInGeoItems(assets);
            val item = assets.getOrNull(position) ?: return
//            file with name == item.second.remarks
            val file = extDir.listFiles()?.find { it.name == item.second.remarks }

            holder.itemUserAssetBinding.assetName.text = item.second.remarks

            if (file != null) {
                val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                holder.itemUserAssetBinding.assetProperties.text =
                    "${file.length().toTrafficString()}  •  ${dateFormat.format(Date(file.lastModified()))}"
            } else {
                holder.itemUserAssetBinding.assetProperties.text = getString(R.string.msg_file_not_found)
            }

            if (item.second.remarks in builtInGeoFiles && item.second.url == AppConfig.geoUrl + item.second.remarks) {
                holder.itemUserAssetBinding.layoutEdit.visibility = GONE
                holder.itemUserAssetBinding.layoutRemove.visibility = GONE
            } else {
                holder.itemUserAssetBinding.layoutEdit.visibility = item.second.url.let { if (it == "file") GONE else VISIBLE }
                holder.itemUserAssetBinding.layoutRemove.visibility = VISIBLE
            }

            holder.itemUserAssetBinding.layoutEdit.setOnClickListener {
                val intent = Intent(this@UserAssetActivity, UserAssetUrlActivity::class.java)
                intent.putExtra("assetId", item.first)
                startActivity(intent)
            }
            holder.itemUserAssetBinding.layoutRemove.setOnClickListener {
                file?.delete()
                MmkvManager.removeAssetUrl(item.first)
                binding.recyclerView.adapter?.notifyItemRemoved(position)
            }
        }

        override fun getItemCount(): Int {
            var assets = MmkvManager.decodeAssetUrls();
            assets = addBuiltInGeoItems(assets);
            return assets.size
        }
    }

    class UserAssetViewHolder(val itemUserAssetBinding: ItemRecyclerUserAssetBinding) :
        RecyclerView.ViewHolder(itemUserAssetBinding.root)
}
