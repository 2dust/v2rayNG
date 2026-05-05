package com.v2ray.ang.core.engine

import android.content.Context
import java.io.File

data class SingBoxRuntimeLayout(
    val rootDir: File,
    val binaryDir: File,
    val workingDir: File,
    val logDir: File,
    val configFile: File,
    val logFile: File,
    val binaryFile: File,
) {
    fun ensureDirectories() {
        rootDir.mkdirs()
        binaryDir.mkdirs()
        workingDir.mkdirs()
        logDir.mkdirs()
    }

    companion object {
        private const val ROOT_DIR_NAME = "core-sing-box"
        private const val BINARY_DIR_NAME = "bin"
        private const val WORKING_DIR_NAME = "runtime"
        private const val LOG_DIR_NAME = "logs"
        private const val CONFIG_FILE_NAME = "config.json"
        private const val LOG_FILE_NAME = "sing-box.log"
        private const val BINARY_FILE_NAME = "sing-box"

        fun fromContext(context: Context): SingBoxRuntimeLayout {
            val rootDir = File(context.noBackupFilesDir, ROOT_DIR_NAME)
            val binaryDir = File(rootDir, BINARY_DIR_NAME)
            val workingDir = File(rootDir, WORKING_DIR_NAME)
            val logDir = File(rootDir, LOG_DIR_NAME)
            return SingBoxRuntimeLayout(
                rootDir = rootDir,
                binaryDir = binaryDir,
                workingDir = workingDir,
                logDir = logDir,
                configFile = File(workingDir, CONFIG_FILE_NAME),
                logFile = File(logDir, LOG_FILE_NAME),
                binaryFile = File(binaryDir, BINARY_FILE_NAME),
            )
        }
    }
}
