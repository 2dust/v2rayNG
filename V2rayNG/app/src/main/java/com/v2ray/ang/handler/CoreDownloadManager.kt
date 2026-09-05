package com.v2ray.ang.handler

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.ResultReceiver
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CoreUrlDownloadRequest
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

internal sealed interface CoreFetchResult<out T> {
    data class Success<T>(val value: T) : CoreFetchResult<T>
    data object NotApplicable : CoreFetchResult<Nothing>
    data object Unavailable : CoreFetchResult<Nothing>
    data object Failed : CoreFetchResult<Nothing>
}

internal object CoreDownloadManager {
    const val RESULT_FAILED = Activity.RESULT_FIRST_USER

    private const val CACHE_DIRECTORY = "core_url_downloads"
    private const val COMPLETION_GRACE_MILLIS = 5_000L
    private const val STALE_FILE_AGE_MILLIS = 60 * 60 * 1_000L

    fun fetchText(
        context: Context,
        request: UrlContentRequest,
        includeDefaultUserAgent: Boolean,
    ): CoreFetchResult<String> = when (val result = fetch(context, request, includeDefaultUserAgent)) {
        is CoreFetchResult.Success -> {
            try {
                CoreFetchResult.Success(result.value.readText())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read core URL response", e)
                CoreFetchResult.Failed
            } finally {
                result.value.delete()
            }
        }

        CoreFetchResult.NotApplicable -> CoreFetchResult.NotApplicable
        CoreFetchResult.Unavailable -> CoreFetchResult.Unavailable
        CoreFetchResult.Failed -> CoreFetchResult.Failed
    }

    fun downloadToFile(
        context: Context,
        request: UrlContentRequest,
        targetFile: File,
    ): CoreFetchResult<Unit> = when (val result = fetch(context, request, false)) {
        is CoreFetchResult.Success -> {
            try {
                result.value.copyTo(targetFile, overwrite = true)
                CoreFetchResult.Success(Unit)
            } catch (e: Exception) {
                targetFile.delete()
                LogUtil.e(AppConfig.TAG, "Failed to store core URL response", e)
                CoreFetchResult.Failed
            } finally {
                result.value.delete()
            }
        }

        CoreFetchResult.NotApplicable -> CoreFetchResult.NotApplicable
        CoreFetchResult.Unavailable -> CoreFetchResult.Unavailable
        CoreFetchResult.Failed -> CoreFetchResult.Failed
    }

    internal fun targetFile(context: Context, requestId: String): File? {
        val id = runCatching { UUID.fromString(requestId).toString() }.getOrNull()
            ?.takeIf { it == requestId } ?: return null
        val directory = File(context.cacheDir, CACHE_DIRECTORY)
        directory.mkdirs()
        if (!directory.isDirectory) return null
        return File(directory, id)
    }

    private fun fetch(
        context: Context,
        request: UrlContentRequest,
        includeDefaultUserAgent: Boolean,
    ): CoreFetchResult<File> {
        if (!shouldUseCore()) return CoreFetchResult.NotApplicable
        val url = request.url ?: return CoreFetchResult.Failed
        val requestId = UUID.randomUUID().toString()
        val targetFile = targetFile(context, requestId) ?: return CoreFetchResult.Failed
        cleanupStaleFiles(targetFile.parentFile)

        val results = ArrayBlockingQueue<Int>(1)
        val resultReceiver = object : ResultReceiver(null) {
            override fun onReceiveResult(code: Int, resultData: Bundle?) {
                results.offer(code)
            }
        }
        val coreRequest = CoreUrlDownloadRequest(
            requestId = requestId,
            url = url,
            headersJson = JsonUtil.toJson(
                HttpUtil.buildRequestHeaders(request, includeDefaultUserAgent)
            ),
            timeoutMillis = request.timeout.toLong(),
        )
        MessageHelper.sendCoreUrlDownloadRequest(context, coreRequest, resultReceiver) { accepted ->
            if (!accepted) results.offer(Activity.RESULT_CANCELED)
        }

        val resultCode = try {
            results.poll(
                request.timeout.toLong().coerceAtLeast(1_000L) + COMPLETION_GRACE_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
        if (resultCode == null) {
            targetFile.delete()
            return CoreFetchResult.Failed
        }

        return when (resultCode) {
            Activity.RESULT_OK -> if (targetFile.isFile) {
                CoreFetchResult.Success(targetFile)
            } else {
                CoreFetchResult.Failed
            }

            Activity.RESULT_CANCELED -> CoreFetchResult.Unavailable
            else -> CoreFetchResult.Failed
        }
    }

    private fun shouldUseCore(): Boolean = SettingsManager.shouldUseCoreForAppRequests()

    private fun cleanupStaleFiles(directory: File?) {
        val staleBefore = System.currentTimeMillis() - STALE_FILE_AGE_MILLIS
        directory?.listFiles()?.forEach { file ->
            if (file.lastModified() < staleBefore) file.delete()
        }
    }
}
