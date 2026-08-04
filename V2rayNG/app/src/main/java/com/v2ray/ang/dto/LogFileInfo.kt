package com.v2ray.ang.dto

/**
 * A log file on disk, as shown in the log settings.
 */
data class LogFileInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)
