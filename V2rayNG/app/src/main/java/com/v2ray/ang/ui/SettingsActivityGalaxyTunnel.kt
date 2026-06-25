package com.v2ray.ang.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivitySettingsGalaxyTunnelBinding

class SettingsActivityGalaxyTunnel : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsGalaxyTunnelBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_GalaxyTunnel)
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsGalaxyTunnelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        setupToolbar()
        setupThemeSetting()
        setupLanguageSetting()
        setupAutoConnect()
        setupKillSwitch()
        setupShare()
        setupVersion()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = getString(R.string.gt_settings_title)

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupThemeSetting() {
        val currentTheme = prefs.getString("theme", "dark") ?: "dark"
        binding.tvThemeValue.text = when (currentTheme) {
            "light" -> getString(R.string.gt_settings_theme_light)
            "system" -> getString(R.string.gt_settings_theme_system)
            else -> getString(R.string.gt_settings_theme_dark)
        }

        // Click to show theme picker
    }

    private fun setupLanguageSetting() {
        val currentLang = prefs.getString("language", "en") ?: "en"
        binding.tvLanguageValue.text = when (currentLang) {
            "my" -> getString(R.string.gt_settings_language_my)
            "zh" -> getString(R.string.gt_settings_language_zh)
            else -> getString(R.string.gt_settings_language_en)
        }

        // Click to show language picker
    }

    private fun setupAutoConnect() {
        binding.switchAutoConnect.isChecked = prefs.getBoolean("auto_connect", false)
        binding.switchAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_connect", isChecked).apply()
        }
    }

    private fun setupKillSwitch() {
        binding.switchKillSwitch.isChecked = prefs.getBoolean("kill_switch", false)
        binding.switchKillSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("kill_switch", isChecked).apply()
        }
    }

    private fun setupShare() {
        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out Galaxy Tunnel VPN!")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.gt_settings_share)))
        }
    }

    private fun setupVersion() {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        binding.tvVersion.text = versionName
    }
}