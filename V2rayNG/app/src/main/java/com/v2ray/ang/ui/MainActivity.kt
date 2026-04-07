package com.v2ray.ang.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.ConnectivityManager
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
import android.widget.TextView
import android.graphics.Typeface
import android.view.Gravity
import android.graphics.Color
import android.widget.ScrollView
import android.widget.LinearLayout
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
import com.daggomostudios.simpsonsvpn.CrashHandler
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.handler.ForceUpdateManager
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
    private val DEBUG_CLICK_THRESHOLD = 7
    private var lastDebugClickTime = 0L

    // Bottom Sheet for Logs & Settings
    private var bottomSheetDialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var tvLogsContent: android.widget.TextView? = null
    
    // Simpsons VPN: Controlo de carregamento inicial
    private var isServersLoaded = false
    
    // Simpsons VPN: Constantes de primeiro arranque
    private val PREF_FIRST_RUN = "pref_first_run_v1"

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

        val outerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_neobrutalist_card)
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16)) }
        }

        val title = TextView(context).apply {
            text = "DON'T PANIC! (APP CRASHED)"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(12))
        }

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(300)
            )
            setPadding(0, 0, 0, dpToPx(12))
        }

        val content = TextView(context).apply {
            text = log
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setTextColor(Color.BLACK)
            setBackgroundResource(R.drawable.bg_neobrutalist_card_normal)
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
        }

        val button = TextView(context).apply {
            text = "I UNDERSTAND"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_neobrutalist_card_selected)
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        scroll.addView(content)
        cardContainer.addView(title)
        cardContainer.addView(scroll)
        cardContainer.addView(button)
        outerLayout.addView(cardContainer)

        val dialog = AlertDialog.Builder(context)
            .setView(outerLayout)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        button.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        // Simpsons VPN: Actualizar a UI de forma segura ao voltar para a MainActivity
        try {
            mainViewModel.reloadServerList()
            updateSelectedServerUI()
            
            // Simpsons VPN: Resetar o estado de "A ATUALIZAR..." se ficar preso ao voltar
            if (binding.tvTestState.text == "A ATUALIZAR...") {
                setTestState(if (mainViewModel.isRunning.value == true) "CONECTADO" else "DESCONECTADO")
            }

            // Simpsons VPN: Verificar se a atualização forçada foi solicitada nas definições
            val forceUpdate = MmkvManager.decodeSettingsBool("pref_first_run", false)
            if (forceUpdate) {
                MmkvManager.encodeSettings("pref_first_run", false)
                loadVpnServers(force = true)
            }
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

        binding.fab.setOnClickListener { 
            it.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shake))
            handleFabAction() 
        }
        
        binding.serverSelectionCard.setOnClickListener { 
            it.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shake))
            // Abrir a lista de servidores (Locations) para seleção
            val intent = Intent(this, LocationsActivity::class.java)
            startActivity(intent)
        }

        binding.btnRefreshServers.setOnClickListener {
            it.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shake))
            loadVpnServers(force = true)
        }

        // Header clicável para abrir Logs e Definições
        binding.headerContainer.setOnClickListener {
            it.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shake))
            showMainBottomSheet()
        }

        // Gatilho para o painel de debug (Easter Egg) - Agora via Long Click no Header
        binding.headerContainer.setOnLongClickListener {
            if (AngBuildConfig.DEBUG) {
                DebugPanelFragment().show(supportFragmentManager, DebugPanelFragment.TAG)
                true
            } else false
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

        // Verificar atualização forçada
        checkForceUpdate()
    }

    fun loadVpnServers(force: Boolean = false) {
        val isFirstRun = MmkvManager.decodeSettingsBool(PREF_FIRST_RUN, true)
        if (!force && !isFirstRun) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Simpsons VPN: Permitir atualização mesmo com VPN conectada
                val isVpnConnected = mainViewModel.isRunning.value == true
                
                if (isVpnConnected) {
                    android.util.Log.d("SimpsonsVPN", "VPN conectada, usando proxy local para baixar servidores...")
                }

                // Verificar conectividade
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = cm.activeNetworkInfo
                val isConnected = activeNetwork?.isConnectedOrConnecting == true

                if (!isConnected && isFirstRun) {
                    withContext(Dispatchers.Main) {
                        showNoDataDialog()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    setTestState("A ATUALIZAR...")
                }

                // 1. Baixar e descriptografar servidores em memória (via proxy se VPN conectada)
                val servers = com.daggomostudios.simpsonsvpn.VpnConfigManager.getVpnServers(useProxy = isVpnConnected)
                
                if (servers != null && servers.isNotEmpty()) {
                    // 2. Importar para o Core do v2rayNG
                    com.daggomostudios.simpsonsvpn.VpnConfigManager.importServersToCore(servers)
                    isServersLoaded = true
                    if (isFirstRun) MmkvManager.encodeSettings(PREF_FIRST_RUN, false)
                    
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast("Servidores atualizados com sucesso!(${servers.size} servidores)")
                        setTestState(if (mainViewModel.isRunning.value == true) "CONECTADO" else "DESCONECTADO")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (isFirstRun) {
                            showNoBalanceDialog()
                        } else {
                            toast("Nenhum servidor encontrado. Tente novamente.")
                        }
                        setTestState("FALHA NA ATUALIZAÇÃO")
                    }
                }
            } catch (e: Exception) {
                Log.e("SimpsonsVPN", "Erro ao carregar servidores: ${e.message}")
                withContext(Dispatchers.Main) {
                    toast("Erro ao atualizar: ${e.message}")
                    setTestState("ERRO: ${e.message}")
                }
            }
        }
    }

    private fun showNoDataDialog() {
        showCartoonDialog(
            title = getString(R.string.dialog_no_data_title),
            message = getString(R.string.dialog_no_data_msg),
            buttonText = getString(R.string.btn_ok),
            onButtonClick = { loadVpnServers(force = true) },
            cancelable = false
        )
    }

    private fun showNoBalanceDialog() {
        showCartoonDialog(
            title = getString(R.string.dialog_no_balance_title),
            message = getString(R.string.dialog_no_balance_msg),
            buttonText = getString(R.string.btn_ok)
        )
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun showCartoonDialog(
        title: String,
        message: String,
        buttonText: String,
        onButtonClick: (() -> Unit)? = null,
        cancelable: Boolean = true
    ) {
        val context = this

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_neobrutalist_card)
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16)) }
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(12))
        }

        val messageView = TextView(context).apply {
            text = message
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16))
        }

        val button = TextView(context).apply {
            text = buttonText
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_neobrutalist_card_selected)
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        cardContainer.addView(titleView)
        cardContainer.addView(messageView)
        cardContainer.addView(button)
        layout.addView(cardContainer)

        val dialog = AlertDialog.Builder(context)
            .setView(layout)
            .setCancelable(cancelable)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        button.setOnClickListener {
            dialog.dismiss()
            onButtonClick?.invoke()
        }

        dialog.show()
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
        mainViewModel.vpnLog.observe(this) { log ->
            try { 
                if (tvLogsContent != null) {
                    tvLogsContent?.text = log
                    
                    // Auto-scroll para o fim
                    val scrollView = tvLogsContent?.parent as? android.widget.ScrollView
                    scrollView?.post {
                        scrollView.fullScroll(android.view.View.FOCUS_DOWN)
                    }
                }
            } catch (e: Exception) {}
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun updateSelectedServerUI() {
        try {
            val guid = MmkvManager.getSelectServer()
            if (guid.isNullOrEmpty()) {
                // Simpsons VPN: Corrigido para manter SELECT SERVER se nada for escolhido
                binding.tvServerName.text = getString(R.string.server_select_prompt)
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
            toast(R.string.toast_enable_mobile_data)
            return
        }
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.server_select_prompt)
            return
        }
        V2RayServiceManager.startVService(this)
    }

    private fun showMainBottomSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_main_bottom_sheet, null)
        
        val tvTabLogs = view.findViewById<android.widget.TextView>(R.id.tv_tab_logs)
        val tvTabSettings = view.findViewById<android.widget.TextView>(R.id.tv_tab_settings)
        val scrollLogs = view.findViewById<android.view.View>(R.id.scroll_logs)
        val layoutSettings = view.findViewById<android.view.View>(R.id.layout_settings_content)
        tvLogsContent = view.findViewById<android.widget.TextView>(R.id.tv_logs_content)

        // Restaurar logs se o serviço estiver a correr ou mostrar info do dispositivo
        if (!mainViewModel.vpnLog.value.isNullOrEmpty()) {
            tvLogsContent?.text = mainViewModel.vpnLog.value
        } else {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val deviceInfo = "[$timestamp] ${android.os.Build.MODEL} | ${android.os.Build.VERSION.RELEASE} | ${android.os.Build.VERSION.SDK_INT} | ${android.os.Build.CPU_ABI.uppercase()}"
            
            // Tentar obter IP local
            val localIp = Utils.getIpv4Address() ?: "Unknown"
            val initialLog = "$deviceInfo\n[$timestamp] IP Local: $localIp"
            
            tvLogsContent?.text = initialLog
            mainViewModel.vpnLog.value = initialLog
        }

        tvTabLogs.setOnClickListener {
            tvTabLogs.setBackgroundResource(R.drawable.bg_neobrutalist_card_selected)
            tvTabSettings.setBackgroundResource(R.drawable.bg_neobrutalist_card_normal)
            scrollLogs.visibility = android.view.View.VISIBLE
            layoutSettings.visibility = android.view.View.GONE
        }

        tvTabSettings.setOnClickListener {
            tvTabSettings.setBackgroundResource(R.drawable.bg_neobrutalist_card_selected)
            tvTabLogs.setBackgroundResource(R.drawable.bg_neobrutalist_card_normal)
            layoutSettings.visibility = android.view.View.VISIBLE
            scrollLogs.visibility = android.view.View.GONE
        }

        dialog.setContentView(view)
        bottomSheetDialog = dialog
        dialog.show()
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
            binding.tvTestState.text = content?.uppercase() ?: "DESCONECTADO"
        } catch (e: Exception) {}
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        try {
            if (isLoading) {
                binding.ivPowerIcon.setImageResource(R.drawable.ic_fab_check)
                // Simpsons VPN: Amarelo durante o carregamento/ligação
                binding.fab.setBackgroundResource(R.drawable.bg_neobrutalist_connect_button)
                return
            }
        if (isRunning) {
            binding.ivPowerIcon.setImageResource(R.drawable.ic_stop_24dp)
            binding.statusContainer.setBackgroundResource(R.drawable.bg_neobrutalist_status) 
            setTestState("CONECTADO")
            // Simpsons VPN: Vermelho quando conectado (simboliza Desligar)
            binding.fab.setBackgroundResource(R.drawable.bg_neobrutalist_fab_connected)
        } else {
            binding.ivPowerIcon.setImageResource(R.drawable.ic_play_24dp)
            setTestState("DESCONECTADO")
            // Simpsons VPN: Amarelo quando desligado
            binding.fab.setBackgroundResource(R.drawable.bg_neobrutalist_connect_button)
        }
        } catch (e: Exception) {}
    }

    private fun checkForceUpdate() {
        val isBlocked = ForceUpdateManager.isAppBlocked(this)

        lifecycleScope.launch {
            when (val result = ForceUpdateManager.checkForUpdate()) {
                is com.v2ray.ang.handler.UpdateCheckResult.NewVersion -> {
                    val remoteVersion = result.remoteVersion
                    ForceUpdateManager.saveLastDownloadUrl(this@MainActivity, remoteVersion.downloadUrl)

                    if (ForceUpdateManager.checkAndBlockIfExpired(this@MainActivity, remoteVersion)) {
                        ForceUpdateManager.showBlockedDialog(this@MainActivity, remoteVersion.downloadUrl)
                    } else {
                        val daysRemaining = ForceUpdateManager.getDaysRemaining(remoteVersion)
                        ForceUpdateManager.showUpdateDialog(this@MainActivity, remoteVersion, daysRemaining)
                    }
                }
                is com.v2ray.ang.handler.UpdateCheckResult.UpToDate -> {
                    // App atualizado — limpar estado de bloqueio se existir
                    ForceUpdateManager.clearUpdateState(this@MainActivity)
                }
                is com.v2ray.ang.handler.UpdateCheckResult.Error -> {
                    // Se houve erro na rede e já estava bloqueado, mostrar diálogo de bloqueio
                    if (isBlocked) {
                        val lastUrl = ForceUpdateManager.getLastDownloadUrl(this@MainActivity)
                        ForceUpdateManager.showBlockedDialog(this@MainActivity, lastUrl)
                    }
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = false
}
