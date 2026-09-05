package com.v2ray.ang.repository

import android.app.Application
import android.net.Uri
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.WebDavManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.ZipUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

open class BackupRepository(private val app: Application) : BaseRepository() {

    private val workDir: File get() = File(app.cacheDir, WORK_DIR_NAME)
    private val appName: String get() = app.getString(R.string.app_name)
    private val sequence = AtomicLong()

    // ===== WebDAV settings =====

    open suspend fun loadWebDav(): WebDavConfig? = withIO { MmkvManager.decodeWebDavConfig() }

    open suspend fun saveWebDav(config: WebDavConfig) = withIO {
        MmkvManager.encodeWebDavConfig(config)
    }

    // ===== naming =====

    open suspend fun defaultFileName(): String = withIO { "${appName}_${timestamp()}$ZIP_SUFFIX" }

    // ===== scratch folder =====

    open suspend fun cleanWorkDir() = withIO {
        workDir.deleteRecursively()
        Unit
    }

    open suspend fun discard(archive: File) = withIO {
        archive.delete()
        Unit
    }

    // ===== pack / unpack =====

    /** @return the archive, or null when there was nothing to dump or zipping failed. */
    open suspend fun packToCache(): File? = runIO(null) {
        val dir = prepareWorkDir()
        val folder = "${appName}_${timestamp()}"
        val dumpDir = File(dir, folder)
        val zip = File(dir, "$folder$ZIP_SUFFIX")
        try {
            when {
                MMKV.backupAllToDirectory(dumpDir.absolutePath) <= 0 -> {
                    LogUtil.w(AppConfig.TAG, "Backup aborted: MMKV reported no store to dump")
                    zip.delete()
                    null
                }

                !ZipUtil.zipFromFolder(dumpDir.absolutePath, zip.absolutePath) -> {
                    LogUtil.w(AppConfig.TAG, "Backup aborted: zipping ${dumpDir.name} failed")
                    zip.delete()
                    null
                }

                else -> zip
            }
        } finally {
            dumpDir.deleteRecursively()
        }
    }

    open suspend fun exportTo(zip: File, target: Uri): Boolean = runIO(false) {
        val output = app.contentResolver.openOutputStream(target) ?: return@runIO false
        output.use { sink -> zip.inputStream().use { it.copyTo(sink) } }
        true
    }

    open suspend fun importToCache(source: Uri): File? = runIO(null) {
        val target = File(prepareWorkDir(), "$IMPORT_PREFIX${unique()}$ZIP_SUFFIX")
        var ok = false
        try {
            val input = app.contentResolver.openInputStream(source)
            if (input != null) {
                input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
                ok = true
            }
        } finally {
            if (!ok) target.delete()
        }
        target.takeIf { ok }
    }

    open suspend fun restore(zip: File): Boolean = runIO(false) {
        val target = File(prepareWorkDir(), "$UNPACK_PREFIX${unique()}")
        try {
            if (!ZipUtil.unzipToFolder(zip, target.absolutePath)) {
                LogUtil.w(AppConfig.TAG, "Restore aborted: ${zip.name} is not a readable archive")
                return@runIO false
            }
            if (MMKV.restoreAllFromDirectory(target.absolutePath) <= 0) {
                LogUtil.w(AppConfig.TAG, "Restore aborted: archive carried no MMKV store")
                return@runIO false
            }
            SettingsChangeManager.makeSetupGroupTab()
            SettingsChangeManager.makeRestartService()
            SettingsManager.initApp(app)
            true
        } finally {
            target.deleteRecursively()
        }
    }

    // ===== WebDAV transfer =====

    open suspend fun uploadBackup(config: WebDavConfig, zip: File): Boolean = runIO(false) {
        WebDavManager.init(config)
        WebDavManager.uploadFile(zip, AppConfig.WEBDAV_BACKUP_FILE_NAME)
    }

    open suspend fun downloadBackup(config: WebDavConfig): File? = runIO(null) {
        val target = File(prepareWorkDir(), "$DOWNLOAD_PREFIX${unique()}$ZIP_SUFFIX")
        var ok = false
        try {
            WebDavManager.init(config)
            ok = WebDavManager.downloadFile(AppConfig.WEBDAV_BACKUP_FILE_NAME, target)
        } finally {
            if (!ok) target.delete()
        }
        target.takeIf { ok }
    }

    // ===== cleanup =====

    /** @return removed count, or null when the store could not offer a complete view. */
    open suspend fun cleanupProfiles(): Int? = withIO { MmkvManager.removeOrphanedServerProfiles() }

    // ===== helpers =====

    private fun prepareWorkDir(): File = workDir.apply { mkdirs() }

    /** [Locale.US] on purpose: a localised calendar would emit non-ASCII digits into file names. */
    private fun timestamp(): String =
        SimpleDateFormat(STAMP_FORMAT, Locale.US).format(System.currentTimeMillis())

    /** Collision-free suffix for scratch files created within the same second. */
    private fun unique(): String = "${System.currentTimeMillis()}_${sequence.incrementAndGet()}"

    private companion object {
        const val WORK_DIR_NAME = "backup"
        const val ZIP_SUFFIX = ".zip"
        const val IMPORT_PREFIX = "restore_"
        const val UNPACK_PREFIX = "unpack_"
        const val DOWNLOAD_PREFIX = "webdav_"
        const val STAMP_FORMAT = "yyyy-MM-dd-HH-mm-ss"
    }
}
