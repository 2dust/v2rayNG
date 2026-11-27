package com.v2ray.ang.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class AssetFileMetadata(val length: Long, val lastModified: Long)

internal data class UserAssetUiState(
    val assets: List<AssetUrlCache> = emptyList(),
    val fileMetadata: Map<String, AssetFileMetadata> = emptyMap(),
    val downloadingItem: String? = null
)

class UserAssetViewModel(application: Application) : BaseViewModel(application) {
    private val builtInGeoFiles = listOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT, AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT)

    private val _uiState = MutableStateFlow(UserAssetUiState())
    internal val uiState: StateFlow<UserAssetUiState> = _uiState.asStateFlow()

    private val _geoFilesSource = MutableStateFlow(loadGeoFilesSource())
    val geoFilesSource: StateFlow<String> = _geoFilesSource.asStateFlow()

    private var reloadJob: Job? = null

    private fun loadGeoFilesSource(): String {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) ?: AppConfig.GEO_FILES_SOURCES.first()
    }

    fun setGeoFilesSource(source: String, extDir: File) {
        MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, source)
        _geoFilesSource.value = source
        reload(source, extDir)
    }

    fun reload(extDir: File): Job {
        return reload(_geoFilesSource.value, extDir)
    }

    fun reload(geoFilesSource: String, extDir: File): Job {
        reloadJob?.cancel()
        return viewModelScope.launch(Dispatchers.IO) {
            val snapshot = buildAssetList(MmkvManager.decodeAssetUrls(), geoFilesSource)
            val files = extDir.listFiles().orEmpty().associateBy { it.name }
            val metadata = snapshot.mapNotNull { asset ->
                files[asset.assetUrl.remarks]?.let { file ->
                    asset.guid to AssetFileMetadata(file.length(), file.lastModified())
                }
            }.toMap()
            ensureActive()
            _uiState.value = UserAssetUiState(snapshot, metadata)
        }.also { reloadJob = it }
    }

    fun initAssets(extDir: File) {
        viewModelScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(app, app.assets)
            reload(_geoFilesSource.value, extDir)
        }
    }

    fun removeAsset(guid: String, name: String, extDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            extDir.listFiles()?.find { it.name == name }?.delete()
            MmkvManager.removeAssetUrl(guid)
            initAssets(extDir)
        }
    }

    fun downloadGeoFilesAndRefresh(extDir: File) {
        toast(R.string.msg_downloading_content)
        launchLoading {
            val currentSource = _geoFilesSource.value
            reload(currentSource, extDir).join()

            val proxyUsername = SettingsManager.getSocksUsername()
            val proxyPassword = SettingsManager.getSocksPassword()
            val httpPort = SettingsManager.getHttpPort()

            val result = withContext(Dispatchers.IO) {
                downloadGeoFiles(extDir, httpPort, proxyUsername, proxyPassword)
            }

            if (result.successCount > 0) {
                toast(
                    getQuantityString(
                        R.plurals.title_update_asset_count,
                        result.successCount,
                        result.successCount
                    )
                )
            } else {
                toastError(R.string.toast_failure)
            }

            reload(currentSource, extDir).join()
        }
    }

    internal fun buildAssetList(
        decodedAssets: List<AssetUrlCache>?,
        geoFilesSource: String
    ): List<AssetUrlCache> {
        val savedAssets = decodedAssets ?: emptyList()
        val builtInItems = builtInGeoFiles
            .filter { geoFile -> savedAssets.none { it.assetUrl.remarks == geoFile } }
            .map {
                AssetUrlCache(
                    // Built-in rows have no persisted GUID; keep their UI identity across reloads.
                    "builtin:$it",
                    AssetUrlItem(
                        it,
                        String.format(AppConfig.GITHUB_DOWNLOAD_URL, geoFilesSource).concatUrl(it),
                        locked = true
                    )
                )
            }
        // Force update URL for geoip-only-cn-private.dat
        return (builtInItems + savedAssets).map { cache ->
            if (cache.assetUrl.remarks == AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT) {
                cache.copy(
                    assetUrl = cache.assetUrl.copy(
                        url = AppConfig.GEOIP_ONLY_CN_PRIVATE_URL
                    )
                )
            } else {
                cache
            }
        }
    }

    suspend fun downloadGeoFiles(
        extDir: File,
        httpPort: Int,
        proxyUsername: String? = null,
        proxyPassword: String? = null,
        onProgress: (suspend (String) -> Unit)? = null
    ): GeoDownloadResult {
        val snapshot = uiState.value.assets
        var successCount = 0
        val failures = mutableListOf<String>()

        try {
            snapshot.forEach { cache ->
                val item = cache.assetUrl
                _uiState.value = _uiState.value.copy(downloadingItem = item.remarks)
                onProgress?.invoke(item.remarks)
                val portsToTry = if (httpPort == 0) listOf(0) else listOf(httpPort, 0)
                if (portsToTry.any { tryDownload(item, extDir, it, proxyUsername, proxyPassword) }) {
                    successCount++
                } else {
                    failures.add(item.remarks)
                }
            }
        } finally {
            _uiState.value = _uiState.value.copy(downloadingItem = null)
        }

        return GeoDownloadResult(successCount, failures.size, failures)
    }

    private fun tryDownload(
        item: AssetUrlItem,
        extDir: File,
        httpPort: Int,
        proxyUsername: String? = null,
        proxyPassword: String? = null
    ): Boolean {
        val targetTemp = File(extDir, item.remarks + "_temp")
        val target = File(extDir, item.remarks)
        try {
            if (
                HttpUtil.downloadToFile(
                    UrlContentRequest(
                        url = item.url,
                        timeout = 15000,
                        httpPort = httpPort,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword
                    ),
                    targetTemp
                )
            ) {
                if (!replaceAssetFile(targetTemp, target)) {
                    LogUtil.e(AppConfig.TAG, "Failed to replace geo file: ${item.remarks}")
                    return false
                }
                return true
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to download geo file: ${item.remarks}", e)
        }
        return false
    }

    internal fun replaceAssetFile(temp: File, target: File): Boolean {
        if (!temp.renameTo(target)) {
            temp.delete()
            return false
        }
        return true
    }

    data class GeoDownloadResult(
        val successCount: Int,
        val failureCount: Int,
        val failedAssets: List<String>
    )
}
