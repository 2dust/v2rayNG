package com.v2ray.ang.core.engine

import android.content.Context
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.FileOutputStream

class SingBoxBinaryInstaller {
    companion object {
        private const val ASSET_ROOT = "sing-box"
        private const val BINARY_NAME = "sing-box"
        private val SUPPORTED_ASSET_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }

    fun install(context: Context, layout: SingBoxRuntimeLayout): Boolean {
        val assetPath = resolveAssetPath(context) ?: return false

        layout.ensureDirectories()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(layout.binaryFile).use { output ->
                input.copyTo(output)
            }
        }

        if (!layout.binaryFile.setExecutable(true, true)) {
            LogUtil.w(AppConfig.TAG, "Failed to mark sing-box binary executable via setExecutable")
        }
        layout.binaryFile.setReadable(true, true)

        LogUtil.i(AppConfig.TAG, "Installed sing-box binary from apk assets: $assetPath")
        return true
    }

    fun expectedAssetLocations(): List<String> {
        return Build.SUPPORTED_ABIS
            .distinct()
            .filter { SUPPORTED_ASSET_ABIS.contains(it) }
            .map { "$ASSET_ROOT/$it/$BINARY_NAME" }
    }

    private fun resolveAssetPath(context: Context): String? {
        return expectedAssetLocations().firstOrNull { assetExists(context, it) }
    }

    private fun assetExists(context: Context, path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
