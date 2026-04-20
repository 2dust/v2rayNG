package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AssetUrlCache
import com.v2ray.ang.dto.AssetUrlItem
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection

class UserAssetViewModel : ViewModel() {
    private val assets = mutableListOf<AssetUrlCache>()
    private val builtInGeoFiles = listOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT, AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT)

    val itemCount: Int
        get() = assets.size

    fun getAssets(): List<AssetUrlCache> = assets.toList()

    fun getAsset(position: Int): AssetUrlCache? = assets.getOrNull(position)

    fun reload(geoFilesSource: String) {
        val decoded = MmkvManager.decodeAssetUrls()
        assets.clear()
        assets.addAll(buildAssetList(decoded, geoFilesSource))
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

    fun downloadGeoFiles(extDir: File, httpPort: Int): GeoDownloadResult {
        val snapshot = getAssets()
        var successCount = 0
        val failures = mutableListOf<String>()

        snapshot.forEach { cache ->
            val item = cache.assetUrl
            val portsToTry = if (httpPort == 0) listOf(0) else listOf(httpPort, 0)
            if (portsToTry.any { tryDownload(item, extDir, it) }) {
                successCount++
            } else {
                failures.add(item.remarks)
            }
        }

        return GeoDownloadResult(successCount, failures.size, failures)
    }

    private fun tryDownload(item: AssetUrlItem, extDir: File, httpPort: Int): Boolean {
        val targetTemp = File(extDir, item.remarks + "_temp")
        val target = File(extDir, item.remarks)
        val conn = HttpUtil.createProxyConnection(item.url, httpPort, 15000, 15000, needStream = true) ?: return false
        try {
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { inputStream ->
                    FileOutputStream(targetTemp).use { output ->
                        inputStream.copyTo(output)
                    }
                }
                targetTemp.renameTo(target)
                return true
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to download geo file: ${item.remarks}", e)
        } finally {
            conn.disconnect()
        }
        return false
    }

    data class GeoDownloadResult(
        val successCount: Int,
        val failureCount: Int,
        val failedAssets: List<String>
    )
}
