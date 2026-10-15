package com.v2ray.ang.ui.main

import android.Manifest
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import com.v2ray.ang.AngApplication
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import java.net.InetSocketAddress
import java.net.Socket
import org.json.JSONArray

class MainActivity : HelperBaseComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            application,
            MainRepository(application as AngApplication)
        )
    }

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

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    gozarWebView = this

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
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
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return true
                            return !(
                                uri.scheme == "file" &&
                                uri.path?.startsWith("/android_asset/") == true
                            )
                        }
                    }

                    addJavascriptInterface(
                        GozarBridge(),
                        "GozarAndroid"
                    )

                    loadUrl("file:///android_asset/index.html")
                }
            }
        )
    }

    private inner class GozarBridge {

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
