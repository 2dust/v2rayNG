package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager

/* ═══════════════════════════════════════════════════════════
   رابط کاربری «گذر» — WebView + موتور v2rayNG
   HTML از assets/index.html لود می‌شود (بدون تغییر) و از طریق
   پل "Android" به موتور Xray وصل می‌شود.
   ═══════════════════════════════════════════════════════════ */

class MainActivity : AppCompatActivity() {

    companion object {
        private val PROTO = Regex(
            "^(vless|vmess|trojan|ss|hysteria2?|hy2|tuic|socks|wireguard)://",
            RegexOption.IGNORE_CASE
        )
    }

    private lateinit var webView: WebView
    private var pendingGuid: String? = null
    private var pendingWebPermission: PermissionRequest? = null
    private var lastCopiedLink: String? = null

    // ── نتیجه پنجره اجازه VPN ──
    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val guid = pendingGuid
        pendingGuid = null
        if (result.resultCode == RESULT_OK && guid != null) doStartCore(guid)
        else pushState("disconnected")
    }

    // ── نتیجه اجازه دوربین (اسکن QR داخل WebView) ──
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingWebPermission
        pendingWebPermission = null
        if (granted && request != null) request.grant(request.resources)
        else request?.deny()
    }

    // ═══════════════════ Lifecycle ═══════════════════

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.setBackgroundColor(0xFF0A0F0D.toInt())
        setContentView(webView)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) = injectShim()
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        val cam = android.Manifest.permission.CAMERA
                        if (ContextCompat.checkSelfPermission(this@MainActivity, cam)
                            == PackageManager.PERMISSION_GRANTED
                        ) request.grant(request.resources)
                        else {
                            pendingWebPermission = request
                            cameraLauncher.launch(cam)
                        }
                    } else request.grant(request.resources)
                }
            }
        }

        webView.addJavascriptInterface(Bridge(), "Android")
        webView.loadUrl("file:///android_asset/index.html")

        handleLinkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLinkIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        pushState(if (isVpnRunning()) "connected" else "disconnected")
    }

    private fun handleLinkIntent(intent: Intent?) {
        val link = intent?.dataString?.trim() ?: return
        if (PROTO.containsMatchIn(link)) {
            lastCopiedLink = link
            toast("کانفیگ دریافت شد — دکمه اتصال را بزن")
        }
    }

    // ═══════════════════ پل JavaScript ↔ اندروید ═══════════════════

    inner class Bridge {

        /** اتصال: آخرین لینک کپی‌شده یا کانفیگ انتخابی موتور */
        @JavascriptInterface
        fun connect() = connectFlow(null)

        /** اتصال با لینک مشخص */
        @JavascriptInterface
        fun connectLink(link: String) = connectFlow(link)

        /** قطع اتصال */
        @JavascriptInterface
        fun disconnect() {
            try {
                V2RayServiceManager.stopVService()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            pushState("disconnected")
        }

        /** آیا VPN در حال اجراست؟ */
        @JavascriptInterface
        fun isConnected(): Boolean = isVpnRunning()

        /** هر لینکی که داخل HTML کپی شود از اینجا می‌آید */
        @JavascriptInterface
        fun onCopied(text: String) {
            val t = text.trim()
            if (PROTO.containsMatchIn(t)) lastCopiedLink = t
        }

        @JavascriptInterface
        fun toast(msg: String) = runOnUiThread {
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ═══════════════════ منطق اتصال ═══════════════════

    private fun connectFlow(explicit: String?) {
        val link = explicit?.trim()?.takeIf { PROTO.containsMatchIn(it) }
            ?: lastCopiedLink?.takeIf { PROTO.containsMatchIn(it) }
            ?: readClipboard()?.trim()?.takeIf { PROTO.containsMatchIn(it) }

        if (link != null && importAndStart(link)) return

        // fallback: کانفیگ‌های موجود در موتور
        val guids = try {
            MmkvManager.decodeServerList()
        } catch (t: Throwable) {
            mutableListOf()
        }
        if (guids.isEmpty()) {
            pushState("disconnected")
            toast("اول یک کانفیگ اضافه کن یا یک لینک کپی کن")
            return
        }
        val selected = try { MmkvManager.getSelectServer() } catch (t: Throwable) { "" }
        val guid = if (!selected.isNullOrEmpty() && guids.contains(selected)) selected else guids.first()
        startWithPermission(guid)
    }

    /** ایمپورت لینک(ها) و اتصال به کانفیگ تازه */
    private fun importAndStart(links: String): Boolean {
        return try {
            val (ok, _) = AngConfigManager.importBatchConfig(links, "", true)
            if (ok <= 0) return false
            val guid = try {
                MmkvManager.decodeServerList().lastOrNull()
            } catch (t: Throwable) {
                null
            }
            if (guid.isNullOrEmpty()) return false
            startWithPermission(guid)
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    private fun startWithPermission(guid: String) {
        try {
            MmkvManager.setSelectServer(guid)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        pushState("connecting")
        try {
            val prepare = VpnService.prepare(this)
            if (prepare == null) doStartCore(guid)
            else {
                pendingGuid = guid
                vpnLauncher.launch(prepare)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            pushState("error")
        }
    }

    private fun doStartCore(guid: String) {
        try {
            // اگر این خط خطا داد، جایگزین: V2RayServiceManager.startV2Ray(this)
            V2RayServiceManager.startVService(this, guid)
            pushState("connecting")
        } catch (t: Throwable) {
            t.printStackTrace()
            pushState("error")
        }
    }

    private fun isVpnRunning(): Boolean = try {
        V2RayServiceManager.v2rayPoint.isRunning
    } catch (t: Throwable) {
        false
    }

    // اگر تابع بالا خطای کامپایل داد، آن را پاک کن و این را فعال کن:
    // private fun isVpnRunning(): Boolean = try {
    //     java.io.File("/proc/net/dev").readLines().any { it.trimStart().startsWith("tun") }
    // } catch (t: Throwable) { false }

    private fun readClipboard(): String? = try {
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.primaryClip?.getItemAt(0)?.text?.toString()
    } catch (t: Throwable) {
        null
    }

    // ═══════════════════ ارسال وضعیت به صفحه ═══════════════════

    private fun pushState(state: String) {
        runOnUiThread {
            if (::webView.isInitialized)
                webView.evaluateJavascript(
                    "window.__setVpnState && window.__setVpnState('$state')", null
                )
        }
    }

    private fun injectShim() {
        webView.evaluateJavascript(SHIM_JS, null)
        pushState(if (isVpnRunning()) "connected" else "disconnected")
    }

    // ═══════════════════ اسکریپت پل (تزریق خودکار — HTML دست‌نخورده می‌ماند) ═══════════════════

    private val SHIM_JS = """
        (function () {
          if (window.__gozarBridge) return;
          window.__gozarBridge = true;

          var cur = '';
          var connectAt = 0;

          var SVG = {
            play: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="6 4 20 12 6 20 6 4"/></svg>',
            stop: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>',
            load: '<svg class="spinning" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>',
            power: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v10"/><path d="M18.4 6.6a9 9 0 1 1-12.77.04"/></svg>'
          };

          var HINTS = {
            disconnected: 'برای اتصال، حلقه یا دکمه پایین صفحه را لمس کن',
            connecting: 'در حال برقراری اتصال…',
            connected: 'اتصال برقرار است — تمام ترافیک از تونل عبور می‌کند',
            error: 'اتصال ناموفق بود — کانفیگ فعال را بررسی کن'
          };

          function coreHtml(state) {
            var icon = state === 'connected' ? SVG.power : (state === 'connecting' ? SVG.load : SVG.play);
            var word = state === 'connected' ? 'وصل است' : (state === 'connecting' ? 'در حال اتصال' : (state === 'error' ? 'خطا' : 'اتصال'));
            var sub = state === 'connected' ? 'VPN فعال است' : 'برای اتصال لمس کن';
            return icon + '<span class="word">' + word + '</span><small>' + sub + '</small>';
          }

          window.__setVpnState = function (state) {
            cur = state;
            if (state === 'connecting') connectAt = Date.now();

            var ring = document.getElementById('ring');
            if (ring) {
              ring.classList.remove('on', 'busy', 'err');
              if (state === 'connected') ring.classList.add('on');
              else if (state === 'connecting') ring.classList.add('busy');
              else if (state === 'error') ring.classList.add('err');
              var core = document.getElementById('ringCore');
              if (core) core.innerHTML = coreHtml(state);
            }

            var fab = document.getElementById('fab');
            if (fab) {
              fab.classList.remove('on', 'busy');
              if (state === 'connected') fab.classList.add('on');
              else if (state === 'connecting') fab.classList.add('busy');
              fab.innerHTML = state === 'connected' ? SVG.stop : (state === 'connecting' ? SVG.load : SVG.play);
            }

            var chip = document.getElementById('statusChip');
            if (chip) {
              chip.classList.remove('on', 'busy', 'err');
              if (state === 'connected') chip.classList.add('on');
              else if (state === 'connecting') chip.classList.add('busy');
              else if (state === 'error') chip.classList.add('err');
              var st = document.getElementById('statusTxt');
              if (st) st.textContent = state === 'connected' ? 'متصل' : (state === 'connecting' ? 'در حال اتصال' : (state === 'error' ? 'خطا' : 'قطع'));
            }

            var hint = document.getElementById('hintTxt');
            if (hint) hint.textContent = HINTS[state] || '';
          };

          document.addEventListener('click', function (e) {
            var t = e.target;
            var el = t && t.closest ? t.closest('[data-conn], #shConnect2') : null;
            if (!el) return;
            e.preventDefault();
            e.stopImmediatePropagation();
            try {
              if (Android.isConnected()) { Android.disconnect(); return; }
              if (el.id === 'shConnect2') {
                var m = document.getElementById('shMeta');
                Android.connectLink(m ? (m.textContent || '') : '');
              } else {
                Android.connect();
              }
            } catch (err) { }
          }, true);

          if (navigator.clipboard && navigator.clipboard.writeText) {
            var orig = navigator.clipboard.writeText.bind(navigator.clipboard);
            navigator.clipboard.writeText = function (txt) {
              try { Android.onCopied(String(txt)); } catch (e) { }
              return orig(txt);
            };
          }

          function poll() {
            var running = false;
            try { running = Android.isConnected(); } catch (e) { }
            if (running && cur !== 'connected') { window.__setVpnState('connected'); return; }
            if (!running && cur === 'connected') { window.__setVpnState('disconnected'); return; }
            if (!running && cur === 'connecting' && Date.now() - connectAt > 15000) { window.__setVpnState('error'); return; }
            if (!running && (cur === '' || cur === 'error')) window.__setVpnState('disconnected');
          }
          setInterval(poll, 1500);
          setTimeout(poll, 400);
        })();
    """.trimIndent()
}
