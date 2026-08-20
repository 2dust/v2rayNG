package com.v2ray.ang.ui.userasset

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.handler.CoreDownloadManager
import com.v2ray.ang.handler.CoreFetchResult
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

internal data class AssetFileMetadata(val length: Long, val lastModified: Long)

internal data class UserAssetUiState(
    val assets: List<AssetUrlCache> = emptyList(),
    val fileMetadata: Map<String, AssetFileMetadata> = emptyMap()
)

class UserAssetViewModel(application: Application) : BaseViewModel(application) {
    private val builtInGeoFiles = listOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT, AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT)

    private val _uiState = MutableStateFlow(UserAssetUiState())
    internal val uiState: StateFlow<UserAssetUiState> = _uiState.asStateFlow()
    private var reloadJob: Job? = null

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

    private fun buildAssetList(
        decodedAssets: List<AssetUrlCache>?,
        geoFilesSource: String
    ): List<AssetUrlCache> {
        val savedAssets = decodedAssets ?: emptyList()
        val builtInItems = builtInGeoFiles
            .filter { geoFile -> savedAssets.none { it.assetUrl.remarks == geoFile } }
            .map {
                AssetUrlCache(
                    Utils.getUuid(),
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

    fun downloadGeoFiles(
        extDir: File,
        httpPort: Int,
        proxyUsername: String? = null,
        proxyPassword: String? = null
    ): GeoDownloadResult {
        val snapshot = uiState.value.assets
        var successCount = 0
        val failures = mutableListOf<String>()

        snapshot.forEach { cache ->
            val item = cache.assetUrl
            if (downloadAsset(item, extDir, httpPort, proxyUsername, proxyPassword)) {
                successCount++
            } else {
                failures.add(item.remarks)
            }
        }

        return GeoDownloadResult(successCount, failures.size, failures)
    }

    private fun downloadAsset(
        item: AssetUrlItem,
        extDir: File,
        httpPort: Int,
        proxyUsername: String?,
        proxyPassword: String?
    ): Boolean {
        val targetTemp = File(extDir, item.remarks + "_temp")
        val target = File(extDir, item.remarks)
        val request = UrlContentRequest(
            url = item.url,
            timeout = 15000,
            proxyUsername = proxyUsername,
            proxyPassword = proxyPassword,
        )
        try {
            val downloaded = when (CoreDownloadManager.downloadToFile(app, request, targetTemp)) {
                is CoreFetchResult.Success -> true
                CoreFetchResult.Failed -> false
                CoreFetchResult.Unavailable -> tryHttpDownload(request, targetTemp, 0)
                CoreFetchResult.NotApplicable -> {
                    val portsToTry = if (httpPort == 0) listOf(0) else listOf(httpPort, 0)
                    portsToTry.any { tryHttpDownload(request, targetTemp, it) }
                }
            }
            if (downloaded && targetTemp.renameTo(target)) {
                return true
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to download geo file: ${item.remarks}", e)
        }
        targetTemp.delete()
        return false
    }

    private fun tryHttpDownload(request: UrlContentRequest, targetFile: File, httpPort: Int): Boolean =
        HttpUtil.downloadToFile(request.copy(httpPort = httpPort), targetFile)

    data class GeoDownloadResult(
        val successCount: Int,
        val failureCount: Int,
        val failedAssets: List<String>
    )
}
