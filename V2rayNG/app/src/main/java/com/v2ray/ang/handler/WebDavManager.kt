package com.v2ray.ang.handler

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

object WebDavManager {
    private var cfg: WebDavConfig? = null
    private var client: OkHttpClient? = null

    /**
     * Initialize the WebDAV manager with a configuration and build an OkHttp client.
     *
     * @param config WebDavConfig containing baseUrl, credentials, remoteBasePath and timeoutSeconds.
     */
    fun init(config: WebDavConfig) {
        cfg = config
        client = OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Upload a local file to a remote relative path under the configured remoteBasePath.
     * The provided `remoteRelativePath` should be relative (e.g. "backup_ng.zip").
     * The method will attempt to create parent directories via MKCOL before PUT.
     *
     * @param localFile File to upload.
     * @param remoteRelativePath Remote path relative to configured remoteBasePath.
     * @return true if upload succeeded (HTTP 2xx), false otherwise.
     */
    suspend fun uploadFile(localFile: File, remoteRelativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cl = client ?: return@withContext false
            val remote = buildRemoteUrl(remoteRelativePath)

            // Ensure parent directories exist
            val dirPath = remote.substringBeforeLast('/')
            if (dirPath != remote) {
                ensureRemoteDirs(dirPath)
            }

            // Determine content type based on file extension
            val mediaType = when (localFile.extension.lowercase()) {
                "zip" -> "application/zip"
                "json" -> "application/json"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }.toMediaTypeOrNull()

            val body = localFile.asRequestBody(mediaType)
            val req = applyAuth(Request.Builder().url(remote).put(body)).build()
            cl.newCall(req).execute().use { resp ->
                val success = resp.isSuccessful
                if (success) {
                    Log.i(AppConfig.TAG, "WebDAV upload success: $remoteRelativePath")
                } else {
                    Log.e(AppConfig.TAG, "WebDAV upload failed: $remoteRelativePath (HTTP ${resp.code})")
                }
                return@withContext success
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "WebDAV upload exception: $remoteRelativePath", e)
            return@withContext false
        }
    }

    /**
     * Download a remote file (relative to configured remoteBasePath) into a local file.
     *
     * @param remoteRelativePath Remote path relative to configured remoteBasePath.
     * @param destFile Local destination file to write to.
     * @return true if download and write succeeded, false otherwise.
     */
    suspend fun downloadFile(remoteRelativePath: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val cl = client ?: return@withContext false
            val remote = buildRemoteUrl(remoteRelativePath)
            val req = applyAuth(Request.Builder().url(remote).get()).build()
            cl.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(AppConfig.TAG, "WebDAV download failed: $remoteRelativePath (HTTP ${resp.code})")
                    return@withContext false
                }

                resp.body.byteStream().use { input ->
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { fos ->
                        input.copyTo(fos)
                    }
                }

                Log.i(AppConfig.TAG, "WebDAV download success: $remoteRelativePath")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "WebDAV download exception: $remoteRelativePath", e)
            return@withContext false
        }
    }

    /**
     * Build a full remote URL by combining the configured base URL, the configured
     * remote base path and a relative path provided by the caller.
     *
     * Example: baseUrl="https://example.com/remote.php/dav", remoteBasePath="backups",
     * remoteRelativePath="backup_ng.zip" => "https://example.com/remote.php/dav/backups/backup_ng.zip"
     *
     * @param remoteRelativePath A path relative to the configured remoteBasePath (no leading slash required).
     * @return Full URL string used for HTTP operations.
     */
    private fun buildRemoteUrl(remoteRelativePath: String): String {
        val base = cfg?.baseUrl?.trimEnd('/') ?: ""
        val basePath = cfg?.remoteBasePath?.trim('/') ?: ""
        val rel = remoteRelativePath.trimStart('/')
        return if (basePath.isEmpty()) "$base/$rel" else "$base/$basePath/$rel"
    }

    /**
     * Apply HTTP Basic authentication headers to the given request builder when
     * username is configured in `cfg`.
     *
     * @param builder OkHttp Request.Builder to modify.
     * @return The same builder instance with Authorization header applied if credentials exist.
     */
    private fun applyAuth(builder: Request.Builder): Request.Builder {
        val username = cfg?.username
        val password = cfg?.password
        if (!username.isNullOrEmpty()) {
            builder.header("Authorization", Credentials.basic(username, password ?: ""))
        }
        return builder
    }

    /**
     * Ensure that each directory segment in the given directory URL exists on the
     * WebDAV server. This issues MKCOL requests for each segment in a best-effort
     * manner and ignores errors for segments that already exist.
     *
     * @param dirUrl Absolute URL to the directory that should exist (e.g. https://.../backups)
     */
    private fun ensureRemoteDirs(dirUrl: String) {
        try {
            val cl = client ?: return
            val url = URL(dirUrl)
            val segments = url.path.split("/").filter { it.isNotEmpty() }
            var accum = ""
            for (seg in segments) {
                accum += "/$seg"
                val mkUrl = URL(url.protocol, url.host, if (url.port == -1) -1 else url.port, accum).toString()
                try {
                    val req = applyAuth(Request.Builder().url(mkUrl).method("MKCOL", null)).build()
                    cl.newCall(req).execute().use { resp ->
                        // 201 Created or 405 Method Not Allowed (already exists) are acceptable
                        if (resp.code != 201 && resp.code != 405 && resp.code != 409) {
                            Log.w(AppConfig.TAG, "WebDAV MKCOL $mkUrl returned ${resp.code}")
                        }
                    }
                } catch (ignored: Exception) {
                    // best-effort, continue
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "WebDAV ensureRemoteDirs error", e)
        }
    }
}