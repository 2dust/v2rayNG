package com.v2ray.ang.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.Log
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
import com.v2ray.ang.databinding.ActivityMainBinding
import com.daggomostudios.simpsonsvpn.CrashHandler
import android.widget.TextView
import android.graphics.Typeface
import android.view.Gravity
import android.graphics.Color
import android.widget.ScrollView
import android.widget.LinearLayout
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
import com.daggomostudios.simpsonsvpn.DebugPanelFragment
import com.daggomostudios.simpsonsvpn.DebugViewModel
import com.daggomostudios.simpsonsvpn.DebugInfo
import com.daggomostudios.simpsonsvpn.VpnConfigManager
import com.v2ray.ang.BuildConfig as AngBuildConfig

/**
 * MainActivity for Simpsons VPN
 * Redesigned with Neobrutalist style and animations.
 */
class MainActivity : HelperBaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private val debugViewModel: DebugViewModel by viewModels()
    // groupPagerAdapter e tabMediator removidos para estabilidade neobrutalista
    private var debugClickCount = 0
    private val DEBUG_CLICK_THRESHOLD = 5
    private var isServersLoaded = false

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simpsons VPN: Inicializar o caçador de crashes
        CrashHandler.init(this)
        
        setContentView(binding.root)
        
        // Ocultar Toolbar original para usar o cabeçalho flutuante Neobrutalista
        setSupportActionBar(null)

        // Iniciar Animações
        startCloudAnimations()
        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.button_pulse)
        binding.fab.startAnimation(pulseAnim)
        
        startMainActivityLogic()
        
        // Verificar se houve um crash na última vez
        checkLastCrash()
    }

    private fun checkLastCrash() {
        val crashLog = CrashHandler.getLastCrash()
        if (!crashLog.isNullOrEmpty()) {
            showCrashAlert(crashLog)
            CrashHandler.clearLastCrash()
        }
    }

    private fun showCrashAlert(log: String) {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(ContextCompat.getColor(context, R.color.simpsons_yellow))
        }

        val title = TextView(context).apply {
            text = "DON'T PANIC! (APP CRASHED)"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                800
            )
        }

        val content = TextView(context).apply {
            text = log
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setTextColor(Color.BLACK)
            setBackgroundResource(R.drawable.bg_neobrutalist_card)
            setPadding(20, 20, 20, 20)
        }

        scroll.addView(content)
        layout.addView(title)
        layout.addView(scroll)

        AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton("I UNDERSTAND") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Simpsons VPN: Actualizar a UI de forma segura ao voltar para a MainActivity
        try {
            mainViewModel.reloadServerList()
            updateSelectedServerUI()
        } catch (e: Exception) {
            Log.e("SimpsonsVPN", "Erro ao retomar MainActivity: ${e.message}")
        }
    }

    private fun startMainActivityLogic() {
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
            // Abrir a lista de servidores (Locations) para seleção
            val intent = Intent(this, LocationsActivity::class.java)
            startActivity(intent)
        }

        // Gatilho para o painel de debug (Easter Egg)
        binding.headerContainer.setOnClickListener { // Usando o header_container como gatilho
            if (AngBuildConfig.DEBUG) {
                debugClickCount++
                if (debugClickCount >= DEBUG_CLICK_THRESHOLD) {
                    DebugPanelFragment().show(supportFragmentManager, DebugPanelFragment.TAG)
                    debugClickCount = 0 // Resetar contador após abrir
                }
            }
        }

        setupViewModel()
        mainViewModel.reloadServerList()
        updateSelectedServerUI()

        // Carregar servidores automaticamente do GitHub apenas na primeira vez
        if (!isServersLoaded) {
            loadVpnServers()
            isServersLoaded = true
        }

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) { }
    }

    fun loadVpnServers() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    debugViewModel.reset()
                    setTestState("UPDATING SERVERS...")
                    debugViewModel.updateStatus("UPDATING SERVERS...")
                }

                VpnConfigManager.debugUpdateCallback = { info ->
                    debugViewModel.debugInfo.postValue(info)
                }

                // 1. Baixar e descriptografar servidores em memória
                val servers = VpnConfigManager.getVpnServers()
                
                // 2. Importar para o Core do v2rayNG
                VpnConfigManager.importServersToCore(servers)
                
                withContext(Dispatchers.Main) {
                    // 3. Atualizar a UI
                    mainViewModel.reloadServerList()
                    
                    setTestState("SERVERS UPDATED")
                    debugViewModel.updateStatus("SERVERS UPDATED")
                    delay(2000)
                    setTestState(if (mainViewModel.isRunning.value == true) "CONNECTED" else "DISCONNECTED")
                    debugViewModel.updateStatus(if (mainViewModel.isRunning.value == true) "CONNECTED" else "DISCONNECTED")
                }
            } catch (e: Exception) {
                Log.e("SimpsonsVPN", "Erro ao carregar servidores: ${e.message}")
                withContext(Dispatchers.Main) {
                    // Evitar toast se a actividade estiver a ser destruída
                    if (!isFinishing) {
                        toast("Falha ao atualizar servidores. Verifique sua conexão.")
                        setTestState(if (mainViewModel.isRunning.value == true) "CONNECTED" else "DISCONNECTED")
                        debugViewModel.updateError(e.message ?: "Erro desconhecido")
                        debugViewModel.updateStatus("ERROR")
                    }
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
        // Simpsons VPN: Observadores simplificados e protegidos contra crashes de UI
        mainViewModel.updateTestResultAction.observe(this) { 
            try { setTestState(it) } catch (e: Exception) {}
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            try { applyRunningState(false, isRunning) } catch (e: Exception) {}
        }
        mainViewModel.updateListAction.observe(this) {
            // Este observador pode disparar enquanto a LocationsActivity está aberta.
            // Apenas actualizamos se a MainActivity estiver visível.
            try { updateSelectedServerUI() } catch (e: Exception) {}
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun updateSelectedServerUI() {
        try {
            val guid = MmkvManager.getSelectServer()
            if (guid.isNullOrEmpty()) {
                binding.tvServerName.text = "SELECT SERVER"
                binding.tvServerPing.text = "Ping: --"
            } else {
                val config = MmkvManager.decodeServerConfig(guid)
                binding.tvServerName.text = config?.remarks ?: "UNKNOWN SERVER"
                val aff = MmkvManager.decodeServerAffiliationInfo(guid)
                binding.tvServerPing.text = if (aff != null && aff.testDelayMillis > 0) {
                    "Ping: ${aff.testDelayMillis}ms"
                } else {
                    "Ping: --"
                }
            }
        } catch (e: Exception) {
            Log.e("SimpsonsVPN", "Erro ao atualizar UI do servidor: ${e.message}")
        }
    }

    // setupGroupTab removido para evitar conflitos de fragmentos na MainActivity neobrutalista

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
        if (!Utils.isMobileDataEnabled(this)) {
            toast("Please enable Mobile Data to connect")
            return
        }
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        V2RayServiceManager.startVService(this)
    }

    // Simpsons VPN: O restartV2Ray agora é feito via MessageUtil no fragmento
    // para evitar crashes de casting e problemas de ciclo de vida.

    /**
     * Imports configuration via subscription. Used by GroupServerFragment.
     */
    public fun importConfigViaSub() {
        mainViewModel.importConfigViaSub(this)
    }

    private fun setTestState(content: String?) {
        try {
            binding.tvTestState.text = content?.uppercase() ?: "DISCONNECTED"
        } catch (e: Exception) {}
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        try {
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
        } catch (e: Exception) {}
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = false
}
