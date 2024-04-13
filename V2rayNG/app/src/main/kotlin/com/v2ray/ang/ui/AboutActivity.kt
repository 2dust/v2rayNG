package com.v2ray.ang.ui

import android.os.Bundle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityAboutBinding
import com.v2ray.ang.util.SpeedtestUtil
import com.v2ray.ang.util.Utils

class AboutActivity : BaseActivity() {
    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        title = getString(R.string.title_about)

        binding.layoutSoureCcode.setOnClickListener {
            Utils.openUri(this, AppConfig.v2rayNGUrl)
        }

        binding.layoutFeedback.setOnClickListener {
            Utils.openUri(this, AppConfig.v2rayNGIssues)
        }

        binding.layoutTgChannel.setOnClickListener {
            Utils.openUri(this, AppConfig.TgChannelUrl)
        }

        binding.layoutPrivacyPolicy.setOnClickListener {
            Utils.openUri(this, AppConfig.v2rayNGPrivacyPolicy)
        }

        "v${BuildConfig.VERSION_NAME} (${SpeedtestUtil.getLibVersion()})".also {
            binding.tvVersion.text = it
        }


    }


}
