package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainGalaxyTunnelBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivityGalaxyTunnel : AppCompatActivity() {

    private lateinit var binding: ActivityMainGalaxyTunnelBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var serverAdapter: ServerAdapterGalaxyTunnel

    private var isConnected = false
    private var isConnecting = false
    private var connectionStartTime: Long = 0
    private var statsUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Galaxy Tunnel theme
        setTheme(R.style.Theme_GalaxyTunnel)
        super.onCreate(savedInstanceState)

        binding = ActivityMainGalaxyTunnelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVPNButton()
        setupServerList()
        setupBottomNav()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = getString(R.string.gt_app_name)
    }

    private fun setupVPNButton() {
        binding.btnVpnToggle.setOnClickListener {
            when {
                isConnected -> disconnectVPN()
                isConnecting -> { /* Do nothing while connecting */ }
                else -> connectVPN()
            }
        }
    }

    private fun connectVPN() {
        // Use original v2rayNG connection logic
        val selectedServer = viewModel.selectedServer.value
        if (selectedServer == null) {
            toast(R.string.gt_error_no_server)
            return
        }

        isConnecting = true
        updateConnectionUI()

        // Call original v2rayNG service
        V2RayServiceManager.startV2Ray(this)

        // Observe connection state
        lifecycleScope.launch {
            delay(2000) // Wait for connection
            if (V2RayServiceManager.v2rayPoint.isRunning) {
                isConnected = true
                isConnecting = false
                connectionStartTime = System.currentTimeMillis()
                startStatsUpdate()
                updateConnectionUI()
            } else {
                isConnecting = false
                updateConnectionUI()
                toast(R.string.gt_error_connection_failed)
            }
        }
    }

    private fun disconnectVPN() {
        isConnecting = true
        updateConnectionUI()

        // Call original v2rayNG service
        V2RayServiceManager.stopV2Ray()

        isConnected = false
        isConnecting = false
        stopStatsUpdate()
        updateConnectionUI()
    }

    private fun updateConnectionUI() {
        when {
            isConnected -> {
                binding.tvStatus.text = getString(R.string.gt_status_connected)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.gt_connected))
                binding.statusIndicator.isSelected = true
                binding.btnVpnToggle.setImageResource(R.drawable.ic_power_on)
                binding.btnVpnToggle.background = ContextCompat.getDrawable(this, R.drawable.gt_circle_button)
                binding.cardStats.visibility = View.VISIBLE
            }
            isConnecting -> {
                binding.tvStatus.text = getString(R.string.gt_status_connecting)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.gt_connecting))
                binding.statusIndicator.isEnabled = false
                binding.btnVpnToggle.isEnabled = false
                binding.btnVpnToggle.alpha = 0.6f
            }
            else -> {
                binding.tvStatus.text = getString(R.string.gt_status_disconnected)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.gt_disconnected))
                binding.statusIndicator.isSelected = false
                binding.btnVpnToggle.setImageResource(R.drawable.ic_power_off)
                binding.btnVpnToggle.isEnabled = true
                binding.btnVpnToggle.alpha = 1.0f
                binding.cardStats.visibility = View.GONE
            }
        }
    }

    private fun setupServerList() {
        serverAdapter = ServerAdapterGalaxyTunnel(
            onSelect = { server, guid ->
                viewModel.selectedServer.value = item.profile
                binding.tvServerName.text = item.profile.remarks
                if (isConnected) {
                    // Reconnect with new server
                    disconnectVPN()
                    connectVPN()
                }
            },
            onEdit = { server, guid ->
                // Open edit dialog
            },
            onDelete = { server, guid ->
                // Show delete confirmation
            },
            onTest = { server, guid ->
                // Test latency
            }
        )

        binding.rvServerList.apply {
            layoutManager = LinearLayoutManager(this@MainActivityGalaxyTunnel)
            adapter = serverAdapter
        }

        binding.btnAddServer.setOnClickListener {
            showConfigImportDialog()
        }
    }

    private fun showConfigImportDialog() {
        val dialog = ConfigImportDialogGalaxyTunnel(this) { configLink ->
            // Use original v2rayNG import logic
            importConfig(configLink)
        }
        dialog.show()
    }

    private fun importConfig(configLink: String) {
        // Call original v2rayNG import function
        when {
            configLink.startsWith(EConfigType.VLESS.protocolScheme) -> {
                // Import VLESS config
            }
            configLink.startsWith(EConfigType.TROJAN.protocolScheme) -> {
                // Import Trojan config
            }
            configLink.startsWith(EConfigType.VMESS.protocolScheme) -> {
                // Import VMess config
            }
            else -> {
                toast(R.string.gt_config_import_error)
                return
            }
        }
        toast(R.string.gt_config_import_success)
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { /* Already on home */ }
                R.id.nav_servers -> { /* Show server list */ }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivityGalaxyTunnel::class.java))
                }
                R.id.nav_logs -> { /* Show logs */ }
            }
            true
        }
    }

    private fun observeViewModel() {
        viewModel.servers.observe(this) { servers ->
            serverAdapter.submitList(servers)
        }

        viewModel.selectedServer.observe(this) { server ->
            server?.let {
                binding.tvServerName.text = it.remarks
            }
        }
    }

    private fun startStatsUpdate() {
        statsUpdateJob = lifecycleScope.launch {
            while (isConnected) {
                updateStats()
                delay(1000)
            }
        }
    }

    private fun stopStatsUpdate() {
        statsUpdateJob?.cancel()
        statsUpdateJob = null
    }

    private fun updateStats() {
        // Get stats from original v2rayNG
        val downloadSpeed = V2RayServiceManager.v2rayPoint.queryStats("socks", "downlink") ?: 0
        val uploadSpeed = V2RayServiceManager.v2rayPoint.queryStats("socks", "uplink") ?: 0

        binding.tvDownloadSpeed.text = Utils.toTrafficUnit(downloadSpeed) + "/s"
        binding.tvUploadSpeed.text = Utils.toTrafficUnit(uploadSpeed) + "/s"

        val duration = System.currentTimeMillis() - connectionStartTime
        binding.tvDuration.text = formatDuration(duration)
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivityGalaxyTunnel::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStatsUpdate()
    }
}