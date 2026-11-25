package com.v2ray.ang.ui.userasset

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

internal data class AssetFileMetadata(val length: Long, val lastModified: Long)

internal data class AssetDownloadProgress(val downloaded: Long = 0L, val total: Long = -1L) {
    val fraction: Float?
        get() = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
}

internal data class UserAssetUiState(
    val assets: List<AssetUrlCache> = emptyList(),
    val fileMetadata: Map<String, AssetFileMetadata> = emptyMap()
)

class UserAssetViewModel(application: Application) : BaseViewModel(application) {
    private val builtInGeoFiles = listOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT, AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT)

    private val _uiState = MutableStateFlow(UserAssetUiState())
    internal val uiState: StateFlow<UserAssetUiState> = _uiState.asStateFlow()
    private var reloadJob: Job? = null

    private val _isDownloading = MutableStateFlow(false)
    internal val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, AssetDownloadProgress>>(emptyMap())
    internal val downloadProgress: StateFlow<Map<String, AssetDownloadProgress>> = _downloadProgress.asStateFlow()

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

    fun downloadGeoFiles(
        extDir: File,
        httpPort: Int,
        proxyUsername: String? = null,
        proxyPassword: String? = null
    ): GeoDownloadResult {
        _isDownloading.value = true
        try {
            val snapshot = uiState.value.assets
            var successCount = 0
            val failures = mutableListOf<String>()

            snapshot.forEach { cache ->
                val item = cache.assetUrl
                val portsToTry = if (httpPort == 0) listOf(0) else listOf(httpPort, 0)
                val succeeded = try {
                    portsToTry.any { tryDownload(cache.guid, item, extDir, it, proxyUsername, proxyPassword) }
                } finally {
                    removeProgress(cache.guid)
                }
                if (succeeded) {
                    successCount++
                } else {
                    failures.add(item.remarks)
                }
            }

            return GeoDownloadResult(successCount, failures.size, failures)
        } finally {
            _isDownloading.value = false
        }
    }

    private fun updateProgress(guid: String, progress: AssetDownloadProgress) {
        _downloadProgress.update { it + (guid to progress) }
    }

    private fun removeProgress(guid: String) {
        _downloadProgress.update { it - guid }
    }

    private fun tryDownload(
        guid: String,
        item: AssetUrlItem,
        extDir: File,
        httpPort: Int,
        proxyUsername: String? = null,
        proxyPassword: String? = null
    ): Boolean {
        val targetTemp = File(extDir, item.remarks + "_temp")
        val target = File(extDir, item.remarks)
        var lastPercent = -1
        var lastEmitTime = 0L
        try {
            if (item.url != "file") {
                updateProgress(guid, AssetDownloadProgress())
            }
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
                ) { downloaded, total ->
                    val now = System.currentTimeMillis()
                    val percent = if (total > 0L) (downloaded * 100 / total).toInt() else -1
                    val changed = if (total > 0L) percent != lastPercent else now - lastEmitTime >= 200L
                    if (changed) {
                        lastPercent = percent
                        lastEmitTime = now
                        updateProgress(guid, AssetDownloadProgress(downloaded, total))
                    }
                }
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
