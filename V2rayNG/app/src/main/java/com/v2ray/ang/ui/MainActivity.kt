package com.v2ray.ang.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity for Simpsons VPN
 * Redesigned with Neobrutalist style and animations.
 */
class MainActivity : HelperBaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        // Ocultar Toolbar original para usar o cabeçalho flutuante Neobrutalista
        setSupportActionBar(null)

        // Iniciar Animações
        startCloudAnimations()
        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.button_pulse)
        binding.fab.startAnimation(pulseAnim)

        // setup viewpager and tablayout (Ocultos mas mantidos para lógica)
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.adapter = groupPagerAdapter
        binding.viewPager.isUserInputEnabled = true

        binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding.navView.setNavigationItemSelectedListener(this)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        binding.fab.setOnClickListener { handleFabAction() }
        binding.serverSelectionCard.setOnClickListener { 
            // Simular clique na aba de servidores ou abrir diálogo de importação
            toast("Select Server")
        }

        setupGroupTab()
        setupViewModel()
        mainViewModel.reloadServerList()

        // Carregar servidores automaticamente do GitHub
        loadVpnServers()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) { }
    }

    private fun loadVpnServers() {
        lifecycleScope.launch {
            try {
                // Exibir estado de carregamento na UI
                setTestState("UPDATING SERVERS...")
                
                // 1. Baixar e descriptografar servidores em memória
                val servers = com.daggomostudios.simpsonsvpn.VpnConfigManager.getVpnServers()
                
                // 2. Importar para o Core do v2rayNG
                com.daggomostudios.simpsonsvpn.VpnConfigManager.importServersToCore(servers)
                
                // 3. Atualizar a UI
                mainViewModel.reloadServerList()
                setupGroupTab()
                
                setTestState("SERVERS UPDATED")
                delay(2000)
                setTestState(if (mainViewModel.isRunning.value == true) "CONNECTED" else "DISCONNECTED")
                
            } catch (e: Exception) {
                Log.e("SimpsonsVPN", "Erro ao carregar servidores: ${e.message}")
                withContext(Dispatchers.Main) {
                    toast("Falha ao atualizar servidores. Verifique sua conexão.")
                    setTestState(if (mainViewModel.isRunning.value == true) "CONNECTED" else "DISCONNECTED")
                }
            }
        }
    }

    private fun startCloudAnimations() {
        val cloudAnim = AnimationUtils.loadAnimation(this, R.anim.cloud_float)
        binding.cloud1.startAnimation(cloudAnim)
        
        val cloudAnim2 = AnimationUtils.loadAnimation(this, R.anim.cloud_float)
        cloudAnim2.startOffset = 5000
        binding.cloud2.startAnimation(cloudAnim2)
        
        val cloudAnim3 = AnimationUtils.loadAnimation(this, R.anim.cloud_float)
        cloudAnim3.startOffset = 10000
        binding.cloud3.startAnimation(cloudAnim3)
    }

    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupPagerAdapter.update(groups)
        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
            groupPagerAdapter.groups.getOrNull(position)?.let {
                tab.text = it.remarks
                tab.tag = it.id
            }
        }.also { it.attach() }
    }

    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        V2RayServiceManager.startVService(this)
    }

    /**
     * Restarts V2Ray service. Used by GroupServerFragment.
     */
    public fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    /**
     * Imports configuration via subscription. Used by GroupServerFragment.
     */
    public fun importConfigViaSub() {
        mainViewModel.importConfigViaSub(this)
    }

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content?.uppercase() ?: "DISCONNECTED"
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        if (isLoading) {
            binding.ivPowerIcon.setImageResource(R.drawable.ic_fab_check)
            return
        }
        if (isRunning) {
            binding.ivPowerIcon.setImageResource(R.drawable.ic_stop_24dp)
            binding.statusContainer.setBackgroundResource(R.drawable.bg_neobrutalist_status) 
            setTestState("CONNECTED")
        } else {
            binding.ivPowerIcon.setImageResource(R.drawable.ic_play_24dp)
            setTestState("DISCONNECTED")
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = false
}
