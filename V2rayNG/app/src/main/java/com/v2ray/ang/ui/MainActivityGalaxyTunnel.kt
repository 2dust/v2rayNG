package com.v2ray.ang.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainGalaxyTunnelBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.toTrafficUnit
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Galaxy Tunnel UI - Main Activity
 * Fixed version with proper imports and references
 */
class MainActivityGalaxyTunnel : BaseActivity() {

    private lateinit var binding: ActivityMainGalaxyTunnelBinding
    private val serversCache = ServersCache.getInstance()
    private var isVpnRunning = false
    private var connectionStartTime: Long = 0
    private var telemetryJob: kotlinx.coroutines.Job? = null
    private lateinit var serverAdapter: GalaxyServerAdapter

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainGalaxyTunnelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVpnToggle()
        setupServerList()
        setupImportButton()
        updateUiState()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupVpnToggle() {
        binding.btnVpnToggle.setOnClickListener {
            toggleVpnConnection()
        }
    }

    private fun toggleVpnConnection() {
        if (isVpnRunning) {
            stopVpnService()
        } else {
            val activeServer = serversCache.getActiveServer()
            if (activeServer == null) {
                Toast.makeText(this, "Please select a server first", Toast.LENGTH_SHORT).show()
                return
            }
            prepareVpnService()
        }
    }

    private fun prepareVpnService() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPrepareLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val activeServer = serversCache.getActiveServer() ?: return
        val guid = activeServer.guid

        // Set active server in v2rayNG
        MmkvManager.encodeSettings("activeServerGuid", guid)
        AngConfigManager.setActiveServer(guid)

        // Start V2Ray service
        V2RayServiceManager.startV2Ray(this)

        isVpnRunning = true
        connectionStartTime = SystemClock.elapsedRealtime()
        updateUiState()
        startTelemetryUpdates()

        Toast.makeText(this, "Connected to ${activeServer.remarks}", Toast.LENGTH_SHORT).show()
    }

    private fun stopVpnService() {
        V2RayServiceManager.stopV2Ray(this)
        isVpnRunning = false
        telemetryJob?.cancel()
        updateUiState()
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
    }

    private fun startTelemetryUpdates() {
        telemetryJob?.cancel()
        telemetryJob = lifecycleScope.launch {
            while (isVpnRunning) {
                updateTelemetry()
                delay(1000)
            }
        }
    }

    private fun updateTelemetry() {
        val duration = SystemClock.elapsedRealtime() - connectionStartTime
        binding.tvDuration.text = formatDuration(duration)

        // Get traffic stats from V2RayServiceManager
        val service = V2RayServiceManager.serviceControl?.get()?.getService()
        val rxRate = service?.getRxRate() ?: 0L
        val txRate = service?.getTxRate() ?: 0L

        binding.tvDownloadSpeed.text = rxRate.toTrafficUnit()
        binding.tvUploadSpeed.text = txRate.toTrafficUnit()
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun setupServerList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerServers)
        recyclerView.layoutManager = LinearLayoutManager(this)

        serverAdapter = GalaxyServerAdapter(
            onServerSelected = { server ->
                serversCache.setActiveServer(server.guid)
                updateUiState()
                if (isVpnRunning) {
                    // Restart with new server
                    stopVpnService()
                    prepareVpnService()
                }
            },
            onPingTest = { server ->
                performPingTest(server)
            }
        )
        recyclerView.adapter = serverAdapter

        // Toggle server list visibility
        binding.cardServerToggle.setOnClickListener {
            val isVisible = binding.recyclerServers.visibility == View.VISIBLE
            binding.recyclerServers.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding.imgExpandIcon.setImageResource(
                if (isVisible) R.drawable.ic_expand_more else R.drawable.ic_navigate_next
            )
        }

        loadServerList()
    }

    private fun loadServerList() {
        val servers = serversCache.getAllServers()
        serverAdapter.submitList(servers)
        binding.tvServerCount.text = "(${servers.size})"

        // Update selected server name
        val activeServer = serversCache.getActiveServer()
        binding.tvServerName.text = activeServer?.remarks ?: "No Node Selected"
    }

    private fun performPingTest(server: ServersCache.ServerItem) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                Utils.testConnection(server.serverAddress, server.serverPort)
            }
            // Update server with ping result
            // Note: In real implementation, you'd update the adapter item
            Toast.makeText(this@MainActivityGalaxyTunnel, 
                "${server.remarks}: ${result}ms", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupImportButton() {
        binding.btnImport.setOnClickListener {
            showImportDialog()
        }
    }

    private fun showImportDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "Paste VLESS/Vmess/SS config here"
            setPadding(40, 40, 40, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Import Server Configuration")
            .setView(editText)
            .setPositiveButton("Import") { _, _ ->
                val config = editText.text.toString()
                if (config.isNotBlank()) {
                    importConfiguration(config)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importConfiguration(config: String) {
        val result = AngConfigManager.importBatchConfig(config, "", true)
        if (result > 0) {
            Toast.makeText(this, "Imported $result server(s)", Toast.LENGTH_SHORT).show()
            loadServerList()
        } else {
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUiState() {
        when {
            isVpnRunning -> {
                binding.statusIndicator.setBackgroundResource(R.drawable.circle_status_online)
                binding.tvStatus.text = getString(R.string.galaxy_connected)
                binding.btnVpnToggle.setImageResource(R.drawable.ic_power_on)
                binding.btnVpnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    getColor(R.color.status_online)
                )
                binding.layoutTelemetry.visibility = View.VISIBLE
            }
            else -> {
                binding.statusIndicator.setBackgroundResource(R.drawable.circle_status_offline)
                binding.tvStatus.text = getString(R.string.galaxy_disconnected)
                binding.btnVpnToggle.setImageResource(R.drawable.ic_power_off)
                binding.btnVpnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    getColor(R.color.status_offline)
                )
                binding.layoutTelemetry.visibility = View.GONE
                binding.tvDuration.text = "00:00:00"
                binding.tvDownloadSpeed.text = "0 B/s"
                binding.tvUploadSpeed.text = "0 B/s"
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main_galaxy_tunnel, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_logs -> {
                startActivity(Intent(this, LogcatActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        loadServerList()
        // Check if VPN is still running
        isVpnRunning = V2RayServiceManager.isRunning()
        if (isVpnRunning) {
            startTelemetryUpdates()
        }
        updateUiState()
    }

    override fun onPause() {
        super.onPause()
        telemetryJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        telemetryJob?.cancel()
    }
}
