package com.v2ray.ang.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityAboutBinding
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.ZipUtil
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class AboutActivity : BaseActivity() {

    private val binding by lazy { ActivityAboutBinding.inflate(layoutInflater) }
    private val extDir by lazy { File(Utils.backupPath(this)) }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                try {
                    showFileChooser()
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Failed to show file chooser", e)
                }
            } else {
                toast(R.string.toast_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.title_about)

        binding.tvBackupSummary.text = this.getString(R.string.summary_configuration_backup, extDir)

        binding.layoutBackup.setOnClickListener {
            val ret = backupConfiguration(extDir.absolutePath)
            if (ret.first) {
                toastSuccess(R.string.toast_success)
            } else {
                toastError(R.string.toast_failure)
            }
        }

        binding.layoutShare.setOnClickListener {
            val ret = backupConfiguration(cacheDir.absolutePath)
            if (ret.first) {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("application/zip")
                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(
                                Intent.EXTRA_STREAM,
                                FileProvider.getUriForFile(
                                    this, BuildConfig.APPLICATION_ID + ".cache", File(ret.second)
                                )
                            ), getString(R.string.title_configuration_share)
                    )
                )
            } else {
                toastError(R.string.toast_failure)
            }
        }

        binding.layoutRestore.setOnClickListener {
            val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

            if (ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try {
                    showFileChooser()
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Failed to show file chooser", e)
                }
            } else {
                requestPermissionLauncher.launch(permission)
            }
        }

        //If it is the Google Play version, not be displayed within 1 days after update
//        if (Utils.isGoogleFlavor()) {
//            val lastUpdateTime = AppManagerUtil.getLastUpdateTime(this)
//            val currentTime = System.currentTimeMillis()
//            if ((currentTime - lastUpdateTime) < 1 * 24 * 60 * 60 * 1000L) {
//                binding.layoutCheckUpdate.visibility = View.GONE
//            }
//        }
        binding.layoutCheckUpdate.setOnClickListener {
            checkForUpdates(binding.checkPreRelease.isChecked)
        }

        binding.checkPreRelease.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, isChecked)
        }
        binding.checkPreRelease.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)

        binding.layoutSoureCcode.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_URL)
        }

        binding.layoutFeedback.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_ISSUES_URL)
        }

        binding.layoutOssLicenses.setOnClickListener {
            val webView = android.webkit.WebView(this)
            webView.loadUrl("file:///android_asset/open_source_licenses.html")
            android.app.AlertDialog.Builder(this)
                .setTitle("Open source licenses")
                .setView(webView)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        binding.layoutTgChannel.setOnClickListener {
            Utils.openUri(this, AppConfig.TG_CHANNEL_URL)
        }

        binding.layoutPrivacyPolicy.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_PRIVACY_POLICY)
        }

        "v${BuildConfig.VERSION_NAME} (${SpeedtestManager.getLibVersion()})".also {
            binding.tvVersion.text = it
        }
    }

    private fun backupConfiguration(outputZipFilePos: String): Pair<Boolean, String> {
        val dateFormated = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val folderName = "${getString(R.string.app_name)}_${dateFormated}"
        val backupDir = this.cacheDir.absolutePath + "/$folderName"
        val outputZipFilePath = "$outputZipFilePos/$folderName.zip"

        val count = MMKV.backupAllToDirectory(backupDir)
        if (count <= 0) {
            return Pair(false, "")
        }

        if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) {
            return Pair(true, outputZipFilePath)
        } else {
            return Pair(false, "")
        }
    }

    private fun restoreConfiguration(zipFile: File): Boolean {
        val backupDir = this.cacheDir.absolutePath + "/${System.currentTimeMillis()}"

        if (!ZipUtil.unzipToFolder(zipFile, backupDir)) {
            return false
        }

        val count = MMKV.restoreAllFromDirectory(backupDir)
        return count > 0
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        try {
            chooseFile.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: android.content.ActivityNotFoundException) {
            Log.e(AppConfig.TAG, "File chooser activity not found", ex)
            toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                try {
                    val targetFile =
                        File(this.cacheDir.absolutePath, "${System.currentTimeMillis()}.zip")
                    contentResolver.openInputStream(uri).use { input ->
                        targetFile.outputStream().use { fileOut ->
                            input?.copyTo(fileOut)
                        }
                    }
                    if (restoreConfiguration(targetFile)) {
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Error during file restore", e)
                    toastError(R.string.toast_failure)
                }
            }
        }

    private fun checkForUpdates(includePreRelease: Boolean) {
        lifecycleScope.launch {
            val result = UpdateCheckerManager.checkForUpdate(includePreRelease)
            if (result.hasUpdate) {
                showUpdateDialog(result)
            } else {
                toast(R.string.update_already_latest_version)
            }
        }
    }

    private fun showUpdateDialog(result: CheckUpdateResult) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_new_version_found, result.latestVersion))
            .setMessage(result.releaseNotes)
            .setPositiveButton(R.string.update_now) { _, _ ->
                result.downloadUrl?.let {
                    Utils.openUri(this, it)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}