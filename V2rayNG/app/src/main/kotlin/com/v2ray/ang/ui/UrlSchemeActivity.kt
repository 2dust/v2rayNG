package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLogcatBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.AngConfigManager
import java.net.URLDecoder

class UrlSchemeActivity : BaseActivity() {
    private lateinit var binding: ActivityLogcatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogcatBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        try {
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                            parseUri(it)
                        }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    when (data?.host) {
                        "install-config" -> {
                            val uri: Uri? = intent.data
                            val shareUrl = uri?.getQueryParameter("url") ?: ""
                            parseUri(shareUrl)
                        }

                        "install-sub" -> {
                            val uri: Uri? = intent.data
                            val shareUrl = uri?.getQueryParameter("url") ?: ""
                            parseUri(shareUrl)
                        }

                        else -> {
                            toast(R.string.toast_failure)
                        }
                    }
                }
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseUri(uriString: String?) {
        if (uriString.isNullOrEmpty()) {
            return
        }
        Log.d("UrlScheme", uriString)

        val decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null) {
            val count = AngConfigManager.importBatchConfig(decodedUrl, "", false)
            if (count > 0) {
                toast(R.string.import_subscription_success)
            } else {
                toast(R.string.import_subscription_failure)
            }
        }
    }
}