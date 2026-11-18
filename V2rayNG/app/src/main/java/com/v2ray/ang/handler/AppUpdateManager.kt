package com.v2ray.ang.handler

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * In-app update: finds out whether a newer release should be announced, downloads its APK with
 * progress, keeps a copy in the public Downloads folder and hands the file to the system installer.
 */
object AppUpdateManager {
    private const val UPDATES_DIR = "updates"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val DIRECT_TIMEOUT_MILLIS = 8_000
    private const val PROXY_TIMEOUT_MILLIS = 15_000

    /**
     * Automatic check for the announcement. Honors the pre-release switch of the "Check for update"
     * screen, the check interval and the version the user postponed.
     *
     * @return the release to announce, or null when there is nothing to announce (up to date, not
     * due yet, or postponed). Throws when the check itself fails; callers ignore that silently.
     */
    suspend fun checkForAnnouncement(): CheckUpdateResult? {
        val now = System.currentTimeMillis()
        val lastCheck = MmkvManager.decodeSettingsLong(AppConfig.PREF_UPDATE_LAST_CHECK, 0L)
        if (!UpdateAnnouncementPolicy.isCheckDue(now, lastCheck)) return null

        val includePreRelease = MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)
        val result = UpdateCheckerManager.checkForUpdate(includePreRelease)
        MmkvManager.encodeSettings(AppConfig.PREF_UPDATE_LAST_CHECK, now)
        if (!result.hasUpdate) return null

        val dismissed = MmkvManager.decodeSettingsString(AppConfig.PREF_UPDATE_DISMISSED_VERSION)
        return result.takeIf { UpdateAnnouncementPolicy.shouldAnnounce(it.latestVersion.orEmpty(), dismissed) }
    }

    /** Remembers that the user chose "Later" for [version]; only a newer release is announced after that. */
    fun postpone(version: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UPDATE_DISMISSED_VERSION, version)
    }

    /**
     * Downloads the APK of [result] into the app cache, first directly and then through the local
     * proxy (the same order the update check uses, because GitHub can be unreachable without it).
     * Cancelling the calling coroutine stops the transfer.
     *
     * @throws IOException when the file could not be downloaded.
     */
    suspend fun download(
        context: Context,
        result: CheckUpdateResult,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = result.downloadUrl ?: throw IOException("No download URL")
        val dir = File(context.cacheDir, UPDATES_DIR)
        dir.deleteRecursively() // old downloads are no longer needed
        dir.mkdirs()
        val file = File(dir, result.assetName ?: "update_${result.latestVersion}.apk")

        val coroutineContext = currentCoroutineContext()
        val isCancelled = { !coroutineContext.isActive }

        var ok = HttpUtil.downloadToFile(
            UrlContentRequest(url = url, timeout = DIRECT_TIMEOUT_MILLIS),
            file, onProgress, isCancelled
        )
        if (!ok && !isCancelled()) {
            ok = HttpUtil.downloadToFile(
                UrlContentRequest(
                    url = url,
                    timeout = PROXY_TIMEOUT_MILLIS,
                    httpPort = SettingsManager.getHttpPort(),
                    proxyUsername = SettingsManager.getSocksUsername(),
                    proxyPassword = SettingsManager.getSocksPassword()
                ),
                file, onProgress, isCancelled
            )
        }
        if (!ok) {
            file.delete()
            if (isCancelled()) throw kotlinx.coroutines.CancellationException("Update download cancelled")
            throw IOException("Failed to download $url")
        }
        file
    }

    /**
     * Keeps a copy of the downloaded APK where the user can find it. On Android 10+ it goes to the
     * public Downloads folder through MediaStore; older versions would need a storage permission for
     * that, so the copy goes to the app's own Downloads folder instead.
     *
     * @return a short location to show to the user, or null when the copy failed.
     */
    suspend fun saveToDownloads(context: Context, file: File): String? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, APK_MIME_TYPE)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null
                val written = resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    true
                } ?: false
                if (!written) {
                    resolver.delete(uri, null, null)
                    return@withContext null
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "${Environment.DIRECTORY_DOWNLOADS}/${file.name}"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@withContext null
                dir.mkdirs()
                val target = File(dir, file.name)
                file.copyTo(target, overwrite = true)
                target.absolutePath
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to save the update to Downloads", e)
            null
        }
    }

    /** True when the app may hand an APK to the installer (Android 8+ asks for this per app). */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** The system screen where the user allows this app to install other apps. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Intent that opens [file] (an APK in the app cache) with the system package installer. */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
