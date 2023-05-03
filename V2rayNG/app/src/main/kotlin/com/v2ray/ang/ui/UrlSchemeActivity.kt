package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.zxing.WriterException
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLogcatBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UrlSchemeActivity : BaseActivity() {
    private lateinit var binding: ActivityLogcatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogcatBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        var shareUrl: String = ""
        var name=""
        try {
            intent?.apply {
                when (action) {
                    Intent.ACTION_SEND -> {
                        if ("text/plain" == type) {
                            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                                shareUrl = it
                            }
                        }
                    }
                    Intent.ACTION_VIEW -> {
                        val uri: Uri? = intent.data
                        shareUrl = uri?.getQueryParameter("url")?:uri?.toString()!!
                        name=uri?.fragment?:""
                    }
                }
            }
            toast(shareUrl)
            val subid=if (shareUrl.startsWith("http"))"" else "default"
            val count = AngConfigManager.importBatchConfig(shareUrl, subid, subid=="default", selectSub = true)
            if (count > 0) {
//                toast(R.string.toast_success)
                val intent = Intent(AppConfig.BROADCAST_ACTION_UPDATE_UI)
                sendBroadcast(intent);


            } else {
                toast(R.string.toast_failure)
            }
            //todo @hiddify1
            startActivity(Intent(this, HiddifyMainActivity::class.java))  //todo: check to open main or hiddifyMain
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



}