package com.v2ray.ang.handler

import android.content.Context
import android.os.Build
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object UpdateCheckerManager {
    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
            val url = if (includePreRelease) {
                AppConfig.APP_API_URL
            } else {
                AppConfig.APP_API_URL.concatUrl("latest")
            }

            var response = HttpUtil.getUrlContent(url, 5000)
            if (response.isNullOrEmpty()) {
                val httpPort = SettingsManager.getHttpPort()
                response = HttpUtil.getUrlContent(url, 5000, httpPort) ?: throw IllegalStateException("Failed to get response")
            }

            val latestRelease = if (includePreRelease) {
                JsonUtil.fromJson(response, Array<GitHubRelease>::class.java)
                    .firstOrNull()
                    ?: throw IllegalStateException("No pre-release found")
            } else {
                JsonUtil.fromJson(response, GitHubRelease::class.java)
            }

            val latestVersion = latestRelease.tagName.removePrefix("v")
            Log.i(AppConfig.TAG, "Found new version: $latestVersion (current: ${BuildConfig.VERSION_NAME})")

            return@withContext if (compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
                val downloadUrl = getDownloadUrl(latestRelease, Build.SUPPORTED_ABIS[0])
                CheckUpdateResult(
                    hasUpdate = true,
                    latestVersion = latestVersion,
                    releaseNotes = latestRelease.body,
                    downloadUrl = downloadUrl,
                    isPreRelease = latestRelease.prerelease
                )
            } else {
                CheckUpdateResult(hasUpdate = false)
            }
    }

    suspend fun downloadApk(context: Context, downloadUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val httpPort = SettingsManager.getHttpPort()
            val connection = HttpUtil.createProxyConnection(downloadUrl, httpPort, 10000, 10000, true)
                ?: throw IllegalStateException("Failed to create connection")

            try {
                val apkFile = File(context.cacheDir, "update.apk")
                Log.i(AppConfig.TAG, "Downloading APK to: ${apkFile.absolutePath}")

                FileOutputStream(apkFile).use { outputStream ->
                    connection.inputStream.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.i(AppConfig.TAG, "APK download completed")
                return@withContext apkFile
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to download APK: ${e.message}")
                return@withContext null
            } finally {
                try {
                    connection.disconnect()
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Error closing connection: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to initiate download: ${e.message}")
            return@withContext null
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val v1 = version1.split(".")
        val v2 = version2.split(".")

        for (i in 0 until maxOf(v1.size, v2.size)) {
            val num1 = if (i < v1.size) v1[i].toInt() else 0
            val num2 = if (i < v2.size) v2[i].toInt() else 0
            if (num1 != num2) return num1 - num2
        }
        return 0
    }

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String {
        val fDroid = "fdroid"

        val assetsByAbi = release.assets.filter {
            (it.name.contains(abi, true))
        }

        val asset = if (BuildConfig.APPLICATION_ID.contains(fDroid, ignoreCase = true)) {
            assetsByAbi.firstOrNull { it.name.contains(fDroid) }
        } else {
            assetsByAbi.firstOrNull { !it.name.contains(fDroid) }
        }

        return asset?.browserDownloadUrl
            ?: throw IllegalStateException("No compatible APK found")
    }
}
