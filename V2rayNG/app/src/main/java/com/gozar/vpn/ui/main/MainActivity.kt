package com.gozar.vpn.ui.main

import android.Manifest
import android.content.Intent
import com.gozar.vpn.ui.backup.BackupActivity
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import com.gozar.vpn.AngApplication
import com.gozar.vpn.core.LauncherManager
import com.gozar.vpn.enums.PermissionType
import com.gozar.vpn.handler.AngConfigManager
import com.gozar.vpn.handler.MmkvManager
import com.gozar.vpn.handler.SettingsManager
import com.gozar.vpn.ui.base.HelperBaseComponentActivity
import java.net.InetSocketAddress
import java.net.Socket
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : HelperBaseComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            application,
            MainRepository(application as AngApplication)
        )
    }

    private val pingWorkers = Executors.newFixedThreadPool(2)

    private var gozarWebView: WebView? = null
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null

    @Volatile
    private var pendingCameraRequest: PermissionRequest? = null

    @Volatile
    private var pendingGuid: String? = null

    @Volatile
    private var startRequestedAt = 0L

    @Volatile
    private var lastBridgeError = ""

    private val requestVpnPermission =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val guid = pendingGuid
            if (result.resultCode == RESULT_OK && !guid.isNullOrBlank()) {
                LauncherManager.startService(this, guid)
            } else {
                lastBridgeError = "مجوز اتصال VPN تأیید نشد."
                startRequestedAt = 0L
            }
        }

    private val pickConfigFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val callback = pendingFileChooser
            pendingFileChooser = null
            callback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        }

    // Gozar one-time migration: SAF keeps credentials in a user-selected local file.
    private var pendingMigrationJson: String? = null
    private val exportMigrationDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            var saved = false
            val payload = pendingMigrationJson
            pendingMigrationJson = null
            if (uri != null && payload != null) {
                saved = try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } != null
                } catch (_: Exception) { false }
            }
            gozarWebView?.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('gozar-migration-export'," +
                    "{detail:{ok:" + saved.toString() + "}}))", null
            )
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingCameraRequest
            pendingCameraRequest = null
            if (request == null) return@registerForActivityResult

            if (granted) {
                val allowed = request.resources.filter {
                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                }.toTypedArray()
                if (allowed.isNotEmpty()) request.grant(allowed) else request.deny()
            } else {
                request.deny()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainViewModel.onAction(MainAction.Initialize)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    @Composable
    override fun ScreenContent() {
        BackHandler {
            val web = gozarWebView
            if (web?.canGoBack() == true) {
                web.goBack()
            } else {
                moveTaskToBack(false)
            }
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    gozarWebView = this

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            pendingFileChooser?.onReceiveValue(null)
                            pendingFileChooser = filePathCallback
                            return try {
                                pickConfigFile.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
                                true
                            } catch (_: Exception) {
                                pendingFileChooser = null
                                filePathCallback?.onReceiveValue(null)
                                false
                            }
                        }

                        override fun onPermissionRequest(request: PermissionRequest) {
                            runOnUiThread {
                                // Only allow camera access requested by the QR scanner.
                                val cameraResources = request.resources.filter {
                                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                }.toTypedArray()
                                if (cameraResources.isEmpty()) {
                                    request.deny()
                                    return@runOnUiThread
                                }

                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    request.grant(cameraResources)
                                } else {
                                    pendingCameraRequest?.deny()
                                    pendingCameraRequest = request
                                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            }
                        }

                        override fun onPermissionRequestCanceled(request: PermissionRequest) {
                            if (pendingCameraRequest === request) pendingCameraRequest = null
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val uri = request?.url ?: return null
                            return if (uri.host == "appassets.androidplatform.net") assetLoader.shouldInterceptRequest(uri) else null
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return true
                            return !(
                                uri.scheme == "https" &&
                                uri.host == "appassets.androidplatform.net" &&
                                uri.path?.startsWith("/assets/") == true
                            )
                        }
                    }

                    addJavascriptInterface(
                        GozarBridge(),
                        "GozarAndroid"
                    )

                    loadUrl("https://appassets.androidplatform.net/assets/index.html")
                }
            }
        )
    }

    private inner class GozarBridge {

        /** Open the app's existing ZIP/MMKV export/restore screen, on UI thread. */
        @JavascriptInterface
        fun openNativeBackup(): String {
            runOnUiThread {
                try {
                    startActivity(Intent(this@MainActivity, BackupActivity::class.java))
                } catch (_: Exception) {
                    // A missing native screen must not disturb the running VPN.
                }
            }
            return "pending"
        }

        /** Export the WebView backup using Android's system document picker. */
        @JavascriptInterface
        fun saveMigrationBackup(json: String?): String {
            if (json.isNullOrBlank() || json.length > 5_000_000) return "invalid"
            return try {
                val data = JSONObject(json)
                if (data.optString("app") != "gozar" ||
                    data.optInt("migrationVersion") != 1 ||
                    !data.has("storage")) return "invalid"
                runOnUiThread {
                    pendingMigrationJson = json
                    exportMigrationDocument.launch("Gozar-migration-backup.json")
                }
                "pending"
            } catch (_: Exception) { "invalid" }
        }

        /** Read clipboard only when the user explicitly taps the import option. */
        @JavascriptInterface
        fun readClipboard(): String {
            return try {
                val clipboard =
                    getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                        ?: return ""
                val clip = clipboard.primaryClip ?: return ""
                if (clip.itemCount <= 0) return ""

                clip.getItemAt(0)
                    .coerceToText(this@MainActivity)
                    .toString()
                    .take(2_000_000)
            } catch (_: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun startVpn(json: String?): String {
            if (json.isNullOrBlank() || json.length > 2_000_000) {
                lastBridgeError = "کانفیگ خالی یا نامعتبر است."
                return "invalid"
            }

            return try {
                lastBridgeError = ""

                val before = MmkvManager
                    .decodeAllServerList()
                    .toSet()

                val result = AngConfigManager.importBatchConfig(
                    json,
                    "",
                    true
                )

                if (result.first <= 0) {
                    lastBridgeError = "کانفیگ قابل واردکردن نیست."
                    return "invalid"
                }

                val after = MmkvManager.decodeAllServerList()

                val guid = after.firstOrNull { it !in before }
                    ?: MmkvManager.getSelectServer()
                    ?: return "invalid"

                MmkvManager.setSelectServer(guid)
                pendingGuid = guid
                startRequestedAt = System.currentTimeMillis()

                runOnUiThread {
                    try {
                        if (SettingsManager.isVpnMode()) {
                            val permissionIntent = VpnService.prepare(this@MainActivity)

                            if (permissionIntent != null) {
                                requestVpnPermission.launch(permissionIntent)
                            } else {
                                LauncherManager.startService(
                                    this@MainActivity,
                                    guid
                                )
                            }
                        } else {
                            LauncherManager.startService(
                                this@MainActivity,
                                guid
                            )
                        }
                    } catch (e: Exception) {
                        lastBridgeError =
                            e.message ?: "شروع سرویس VPN ناموفق بود."
                        startRequestedAt = 0L
                    }
                }

                "ok"
            } catch (e: Exception) {
                lastBridgeError =
                    e.message ?: "خطا در واردکردن کانفیگ."
                "invalid"
            }
        }

        @JavascriptInterface
        fun stopVpn() {
            runOnUiThread {
                try {
                    LauncherManager.stopService(this@MainActivity)
                    pendingGuid = null
                    startRequestedAt = 0L
                    lastBridgeError = ""
                } catch (e: Exception) {
                    lastBridgeError =
                        e.message ?: "قطع اتصال ناموفق بود."
                }
            }
        }

        @JavascriptInterface
        fun vpnStatus(): String {
            if (mainViewModel.uiState.value.isRunning) {
                pendingGuid = null
                startRequestedAt = 0L
                lastBridgeError = ""
                return "on"
            }

            if (lastBridgeError.isNotBlank()) {
                return "error"
            }

            val started = startRequestedAt
            if (
                started > 0L &&
                System.currentTimeMillis() - started < 20_000L
            ) {
                return "connecting"
            }

            return "off"
        }

        @JavascriptInterface
        fun setVpnParams(json: String?) {
            // اتصال تنظیمات پیشرفتهٔ تونل به سرویس بومی
            // در مرحلهٔ بعد پیاده‌سازی می‌شود.
        }

        @JavascriptInterface
        fun tcpPingAsync(server: String, port: Int, requestId: String, timeoutMs: Int) {
            if (requestId.length !in 1..80 || !requestId.matches(Regex("[a-zA-Z0-9_]+"))) return
            if (port !in 1..65535 || server.length !in 1..253) return
            val safeTimeout = timeoutMs.coerceIn(500, 10000)
            pingWorkers.execute {
                val started = System.currentTimeMillis()
                val result = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(server, port), safeTimeout)
                    }
                    "ok,${System.currentTimeMillis() - started}"
                } catch (_: Exception) { "error" }
                runOnUiThread {
                    gozarWebView?.evaluateJavascript(
                        "window.gozarPingResult(${JSONObject.quote(requestId)},${JSONObject.quote(result)});",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun tcpPing(server: String, port: Int): String {
            return try {
                val elapsed = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(server, port),
                        2000
                    )
                }
                "ok,${System.currentTimeMillis() - elapsed}"
            } catch (e: Exception) {
                "error,${e.message.orEmpty()}"
            }
        }

        @JavascriptInterface
        fun coreLog(): String {
            return lastBridgeError
        }

        @JavascriptInterface
        fun getApps(): String {
            return try {
                val result = JSONArray()

                packageManager
                    .getInstalledApplications(
                        PackageManager.GET_META_DATA
                    )
                    .forEach { app ->
                        val label = packageManager
                            .getApplicationLabel(app)
                            .toString()

                        result.put(
                            org.json.JSONObject()
                                .put("n", label)
                                .put("p", app.packageName)
                        )
                    }

                result.toString()
            } catch (e: Exception) {
                "[]"
            }
        }
    }

    override fun onDestroy() {
        pingWorkers.shutdownNow()
        pendingCameraRequest?.deny()
        pendingCameraRequest = null
        pendingFileChooser?.onReceiveValue(null)
        pendingFileChooser = null
        gozarWebView?.apply {
            removeJavascriptInterface("GozarAndroid")
            stopLoading()
            destroy()
        }
        gozarWebView = null
        super.onDestroy()
    }
}
