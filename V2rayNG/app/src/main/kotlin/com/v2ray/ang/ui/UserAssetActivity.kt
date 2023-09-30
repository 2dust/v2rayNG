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
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivitySubSettingBinding
import com.v2ray.ang.databinding.ItemRecyclerUserAssetBinding
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

    val extDir by lazy { File(Utils.userAssetPath(this)) }
    val geofiles = arrayOf("geosite.dat", "geoip.dat")

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_asset, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_file -> {
            showFileChooser()
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
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val uri = it.data?.data
            if (it.resultCode == RESULT_OK && uri != null) {
                try {
                    copyFile(uri)
                } catch (e: Exception) {
                    toast(R.string.toast_asset_copy_failed)
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
        geofiles.forEach {
            //toast(getString(R.string.msg_downloading_content) + it)
            lifecycleScope.launch(Dispatchers.IO) {
                val result = downloadGeo(it, 60000, httpPort)
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.toast_success) + " " + it)
                        binding.recyclerView.adapter?.notifyDataSetChanged()
                    } else {
                        toast(getString(R.string.toast_failure) + " " + it)
                    }
                }
            }
        }
    }

    private fun downloadGeo(name: String, timeout: Int, httpPort: Int): Boolean {
        val url = AppConfig.geoUrl + name
        val targetTemp = File(extDir, name + "_temp")
        val target = File(extDir, name)
        var conn: HttpURLConnection? = null
        //Log.d(AppConfig.ANG_PACKAGE, url)

        try {
            conn = URL(url).openConnection(
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress("127.0.0.1", httpPort)
                )
            ) as HttpURLConnection
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

    inner class UserAssetAdapter : RecyclerView.Adapter<UserAssetViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAssetViewHolder {
            return UserAssetViewHolder(ItemRecyclerUserAssetBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: UserAssetViewHolder, position: Int) {
            val file = extDir.listFiles()?.getOrNull(position) ?: return
            holder.itemUserAssetBinding.assetName.text = file.name
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
            holder.itemUserAssetBinding.assetProperties.text = "${file.length().toTrafficString()}  •  ${dateFormat.format(Date(file.lastModified()))}"
            if (file.name in geofiles) {
                holder.itemUserAssetBinding.layoutRemove.visibility = GONE
            } else {
                holder.itemUserAssetBinding.layoutRemove.visibility = VISIBLE
            }
            holder.itemUserAssetBinding.layoutRemove.setOnClickListener {
                file.delete()
                binding.recyclerView.adapter?.notifyItemRemoved(position)
            }
        }

        override fun getItemCount(): Int {
            return extDir.listFiles()?.size ?: 0
        }
    }

    class UserAssetViewHolder(val itemUserAssetBinding: ItemRecyclerUserAssetBinding) : RecyclerView.ViewHolder(itemUserAssetBinding.root)
}
