package com.v2ray.ang.repository

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Domain model of one asset entry. It lives in the data layer on purpose: the repository used to
 * import the UI contract type, which inverted the dependency direction of the whole feature.
 */
data class AssetFile(
    val guid: String,
    val remarks: String,
    val url: String,
    val locked: Boolean,
    val properties: String
) {
    val isLocalFile: Boolean get() = url == URL_LOCAL_FILE

    companion object {
        /** Marker url used for assets imported from a local file. */
        const val URL_LOCAL_FILE = "file"
    }
}

/**
 * Everything the asset screen needs, read in one IO hop.
 *
 * Returning geo settings and the file list together lets the ViewModel publish a single state
 * update instead of two, which halves the recompositions of the initial load.
 */
data class AssetSnapshot(
    val geoSources: List<String>,
    val geoSource: String,
    val files: List<AssetFile>
)

/** Result of importing a local file, expressed without UI strings. */
enum class AssetImportResult { SUCCESS, DUPLICATE, FAILURE }

/**
 * Data layer of the user-asset feature: MMKV, the external asset directory and geo-file
 * downloads. Every entry point is `suspend` and main-safe; 
 */
open class UserAssetRepository(private val app: Application) : BaseRepository() {

    private val extDir: File get() = File(Utils.userAssetPath(app))

    private val builtInGeoFiles = listOf(
        AppConfig.GEOSITE_DAT,
        AppConfig.GEOIP_DAT,
        AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT
    )

    // ---- read ----

    open suspend fun loadSnapshot(): AssetSnapshot = withIO {
        val source = readGeoSource()
        AssetSnapshot(
            geoSources = AppConfig.GEO_FILES_SOURCES.toList(),
            geoSource = source,
            files = readAssets(source)
        )
    }

    open suspend fun setGeoSource(value: String) {
        withIO {
            withContext(NonCancellable) {
                MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, value)
            }
        }
    }

    private fun readGeoSource(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES)
            ?: AppConfig.GEO_FILES_SOURCES.first()

    /**
     * Built-in geo files are merged in front of the saved ones. They use a stable synthetic
     * id (instead of a fresh UUID per reload) so LazyColumn keys stay valid across refreshes.
     */
    private fun readAssets(source: String): List<AssetFile> {
        val saved = MmkvManager.decodeAssetUrls()
        val builtIn = builtInGeoFiles
            .filter { name -> saved.none { it.assetUrl.remarks == name } }
            .map { name ->
                val builtInUrl = String.format(AppConfig.GITHUB_DOWNLOAD_URL, source).concatUrl(name)
                AssetUrlCache(
                    guid = "$BUILT_IN_PREFIX$name",
                    assetUrl = AssetUrlItem(name, builtInUrl, locked = true)
                )
            }
        val files = extDir.listFiles()?.associateBy { it.name }.orEmpty()

        return (builtIn + saved).map { cache ->
            // geoip-only-cn-private.dat always comes from its own repository.
            val url = if (cache.assetUrl.remarks == AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT) {
                AppConfig.GEOIP_ONLY_CN_PRIVATE_URL
            } else {
                cache.assetUrl.url
            }
            val file = files[cache.assetUrl.remarks]
            AssetFile(
                guid = cache.guid,
                remarks = cache.assetUrl.remarks,
                url = url,
                locked = cache.assetUrl.locked == true,
                properties = formatProperties(file)
            )
        }
    }

    /** Formats the size/date line, or falls back to the localized "not found" message. */
    private fun formatProperties(file: File?): String = if (file != null) {
        "${file.length().toTrafficString()}    ${Utils.formatTimestamp(file.lastModified())}"
    } else {
        app.getString(R.string.msg_file_not_found)
    }

    // ---- write ----

    /** Copies the picked document into the asset directory and registers it. */
    open suspend fun importFile(uri: Uri): AssetImportResult = withIO {
        val name = cursorName(uri) ?: uri.toString()
        if (MmkvManager.decodeAssetUrls().any { it.assetUrl.remarks == name }) {
            return@withIO AssetImportResult.DUPLICATE
        }
        val assetId = Utils.getUuid()
        runCatching {
            withContext(NonCancellable) {
                MmkvManager.encodeAsset(assetId, AssetUrlItem(name, AssetFile.URL_LOCAL_FILE))
                app.contentResolver.openInputStream(uri).use { input ->
                    File(extDir, name).outputStream().use { output -> input?.copyTo(output) }
                }
            }
            AssetImportResult.SUCCESS
        }.getOrElse { e ->
            LogUtil.e(AppConfig.TAG, "Failed to import asset file", e)
            withContext(NonCancellable) { MmkvManager.removeAssetUrl(assetId) }
            AssetImportResult.FAILURE
        }
    }

    /**
     * List-screen delete: drops the file from disk *and* the entry from storage, then restores the
     * bundled defaults, mirroring the legacy behaviour.
     */
    open suspend fun removeAssetWithFile(guid: String, remarks: String) {
        withIO {
            withContext(NonCancellable) {
                extDir.listFiles()?.firstOrNull { it.name == remarks }?.delete()
                MmkvManager.removeAssetUrl(guid)
                SettingsManager.initAssets(app, app.assets)
            }
        }
    }

    /** Editor delete: forgets the source only; an already downloaded file stays usable. */
    open suspend fun removeAssetUrl(assetId: String) {
        withIO { withContext(NonCancellable) { MmkvManager.removeAssetUrl(assetId) } }
    }

    // ---- download ----

    /**
     * Downloads every listed asset, one at a time so the job stays cancellable and can
     * report progress.
     *
     * @return the number of successfully downloaded files.
     */
    open suspend fun downloadAll(onProgress: suspend (done: Int, total: Int) -> Unit): Int = withIO {
        val items = readAssets(readGeoSource())
        val httpPort = SettingsManager.getHttpPort()
        val username = SettingsManager.getSocksUsername()
        val password = SettingsManager.getSocksPassword()
        var success = 0
        items.forEachIndexed { index, assetFile ->
            currentCoroutineContext().ensureActive()
            onProgress(index + 1, items.size)
            val ports = if (httpPort == 0) listOf(0) else listOf(httpPort, 0)
            if (ports.any { port -> download(assetFile, port, username, password) }) success++
        }
        success
    }

    private fun download(
        assetFile: AssetFile,
        httpPort: Int,
        proxyUsername: String?,
        proxyPassword: String?
    ): Boolean = try {
        val temp = File(extDir, "${assetFile.remarks}_temp")
        val target = File(extDir, assetFile.remarks)
        val request = UrlContentRequest(
            url = assetFile.url,
            timeout = 15000,
            httpPort = httpPort,
            proxyUsername = proxyUsername,
            proxyPassword = proxyPassword
        )
        if (HttpUtil.downloadToFile(request, temp)) {
            temp.renameTo(target)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to download geo file: ${assetFile.remarks}", e)
        false
    }

    // ---- editor support ----

    open suspend fun loadAsset(assetId: String): AssetUrlItem? =
        withIO { MmkvManager.decodeAsset(assetId) }

    open suspend fun isRemarkDuplicated(remarks: String, assetId: String): Boolean = withIO {
        MmkvManager.decodeAssetUrls().any { it.assetUrl.remarks == remarks && it.guid != assetId }
    }

    /** Saves the asset; when the remark changed, the stale file is dropped. */
    open suspend fun saveAsset(assetId: String, remarks: String, url: String): String = withIO {
        val existing = MmkvManager.decodeAsset(assetId)
        val id = if (existing != null) assetId else Utils.getUuid()
        withContext(NonCancellable) {
            if (existing != null) {
                runCatching { extDir.resolve(existing.remarks).takeIf { it.exists() }?.delete() }
                    .onFailure { LogUtil.e(AppConfig.TAG, "Failed to delete stale asset file", it) }
            }
            val item = existing ?: AssetUrlItem()
            item.remarks = remarks
            item.url = url
            MmkvManager.encodeAsset(id, item)
        }
        id
    }

    private fun cursorName(uri: Uri): String? = try {
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to get cursor name", e)
        null
    }

    private companion object {
        const val BUILT_IN_PREFIX = "builtin:"
    }
}
