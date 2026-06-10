package com.v2ray.ang.ui

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // WebView ကို သတ်မှတ်ပြီး HTML ဖိုင် လှမ်းဖွင့်ခြင်း
        val myWebView: WebView = findViewById(R.id.my_webview)
        myWebView.settings.javaScriptEnabled = true
        myWebView.settings.domStorageEnabled = true
        myWebView.loadUrl("file:///android_asset/index.html")
    }
}
