package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.LogFileInfo
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.LogUtil
import java.io.File

/**
 * Owns the core log files: where they live, whether the core writes them,
 * and how they are listed for the log settings screen.
 */
object LogFileManager {

    const val ACCESS_LOG = "access_log.txt"
    const val CORE_LOG = "core_log.txt"

    /** Only the tail is read back; a runaway log must not be loaded whole. */
    private const val MAX_READ_BYTES = 512L * 1024L

    fun logDir(context: Context): File = File(context.filesDir, AppConfig.LOG_DIR)

    fun isFileLoggingEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CORE_LOG_TO_FILE, false)

    /**
     * Points the core log at files when file logging is enabled, clears the paths otherwise.
     *
     * @param log The log section of the config being built.
     * @param context The context.
     */
    fun applyFileLogging(log: V2rayConfig.LogBean, context: Context) {
        if (!isFileLoggingEnabled()) {
            log.access = null
            log.error = null
            return
        }

        val dir = logDir(context)
        if (!dir.exists() && !dir.mkdirs()) {
            LogUtil.w(AppConfig.TAG, "Failed to create log directory ${dir.absolutePath}")
            log.access = null
            log.error = null
            return
        }

        log.access = File(dir, ACCESS_LOG).absolutePath
        log.error = File(dir, CORE_LOG).absolutePath
    }

    /**
     * Lists the log files currently on disk, newest first.
     *
     * @param context The context.
     * @return The log files, or an empty list when nothing has been written yet.
     */
    fun listLogFiles(context: Context): List<LogFileInfo> {
        val files = logDir(context).listFiles()?.filter { it.isFile } ?: return emptyList()
        return files
            .map { LogFileInfo(it.name, it.absolutePath, it.length(), it.lastModified()) }
            .sortedByDescending { it.lastModified }
    }

    /**
     * Reads the tail of a log file.
     *
     * @param path The absolute file path.
     * @return The lines of the file, oldest first.
     */
    fun readLogFile(path: String): List<String> {
        val file = File(path)
        if (!file.isFile) return emptyList()

        return try {
            val length = file.length()
            if (length <= MAX_READ_BYTES) {
                file.readLines()
            } else {
                file.inputStream().use { stream ->
                    stream.skip(length - MAX_READ_BYTES)
                    // The first line is likely cut in half by the skip
                    stream.bufferedReader().readLines().drop(1)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read log file $path", e)
            emptyList()
        }
    }

    /**
     * Empties a log file, keeping it in place so the running core can keep writing.
     *
     * @param path The absolute file path.
     * @return True when the file was emptied.
     */
    fun clearLogFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.isFile) return false
            file.writeText("")
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to clear log file $path", e)
            false
        }
    }
}
