package com.v2ray.npv

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.npv.crsgw.data.NpvDatabase
import com.npv.crsgw.data.Server
import com.npv.crsgw.data.ServerDao
import com.npv.crsgw.databinding.NpvActivityMainBinding
import com.npv.crsgw.event.AuthEventBus
import com.npv.crsgw.rest.model.GetHomeDataItemResponse
import com.npv.crsgw.store.UserStore
import com.npv.crsgw.ui.UserViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.CheckUpdateActivity
import com.v2ray.ang.ui.LogcatActivity
import com.v2ray.ang.ui.PerAppProxyActivity
import com.v2ray.ang.ui.UserAssetActivity
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import com.v2ray.npv.NpvBaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NpvMainActivity : NpvBaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val TAG = NpvMainActivity::class.simpleName

    private val binding by lazy { NpvActivityMainBinding.inflate(layoutInflater) }

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    val mainViewModel: MainViewModel by viewModels()

    private val viewModel: UserViewModel by viewModels()

    private lateinit var db: NpvDatabase
    private lateinit var serverDao: ServerDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // 初始化 Room 数据库
        db = NpvDatabase.getInstance(applicationContext)
        serverDao = db.serverDao()

        val navView = binding.navView
        val headerView = navView.getHeaderView(0)
        val userNameView = headerView.findViewById<TextView>(com.npv.crsgw.R.id.user_name)
        val expireTime = headerView.findViewById<TextView>(com.npv.crsgw.R.id.subscription_expire_time)

        lifecycleScope.launch {
            val email = UserStore.getUser()?.username
            userNameView.text = email

            // showLoading()

            viewModel.getHomeData(email.toString())
        }

        binding.startVpnButton.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                V2RayServiceManager.stopVService(this)
            } else if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
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

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

        setupViewModel()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        observeState(
            viewModel.userState,
            onLoading = {
                showLoading()
                        },
            onSuccess = {
                hideLoading()
                // progressBar.visibility = View.GONE
                // textView.text = it.name

                importXrayConfig(it)

                userNameView.text = it.email
                val t = formatExpireDate(it)
                expireTime.text = t

                binding.membershipUsedTraffic.text = getString(com.npv.crsgw.R.string.npv_used_traffic_value_0, it.usedTraffic)
                binding.membershipTotalTraffic.text = getString(com.npv.crsgw.R.string.npv_total_traffic_value_0, it.totalTraffic)
                binding.membershipValidFor.text = getString(com.npv.crsgw.R.string.npv_valid_for_days, it.remainingDays)

                Log.i(TAG, "get home data successfully: ${it.expireAt}")
            },
            onFailure = { _, msg ->
                hideLoading()
                binding.membershipUsedTraffic.text = getString(com.npv.crsgw.R.string.npv_used_traffic_value_0, 0)
                binding.membershipTotalTraffic.text = getString(com.npv.crsgw.R.string.npv_total_traffic_value_0, 0)
                binding.membershipValidFor.text = getString(com.npv.crsgw.R.string.npv_valid_for_days, 0)
                Log.e(TAG, "Failed to get home data: $msg")
            }
        )
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            com.npv.crsgw.R.id.switch_country_region -> toastSuccess("Switch region")
            com.npv.crsgw.R.id.my_ticket -> toastSuccess("提交工单")
            com.npv.crsgw.R.id.referral_rebate -> toastSuccess("referral_rebate")
            com.npv.crsgw.R.id.notification_center -> toastSuccess("notification_center")
            com.npv.crsgw.R.id.logout ->  CoroutineScope(Dispatchers.Main).launch {
                AuthEventBus.notifyAuthExpired()
            }
            com.npv.crsgw.R.id.about -> toastSuccess("about")
            /*
            R.id.per_app_proxy_settings -> startActivity(Intent(this, PerAppProxyActivity::class.java))
            R.id.user_asset_setting -> startActivity(Intent(this, UserAssetActivity::class.java))
            R.id.promotion -> Utils.openUri(this, "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}")
            R.id.logcat -> startActivity(Intent(this, LogcatActivity::class.java))
            R.id.check_for_update -> startActivity(Intent(this, CheckUpdateActivity::class.java))
            R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
            */
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupViewModel() {
        // mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            //adapter.isRunning = isRunning
            if (isRunning) {
                // binding.startVpnButton.setImageResource(R.drawable.ic_stop_24dp)
                binding.startVpnButton.text = getString(com.npv.crsgw.R.string.npv_disconnect)
                binding.startVpnButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_active))
                // setTestState(getString(R.string.connection_connected))
                // binding.layoutTest.isFocusable = true
            } else {
                // binding.fab.setImageResource(R.drawable.ic_play_24dp)
                binding.startVpnButton.text = getString(com.npv.crsgw.R.string.npv_tap_to_connect)
                binding.startVpnButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_inactive))
                // setTestState(getString(R.string.connection_not_connected))
                // binding.layoutTest.isFocusable = false
            }
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        V2RayServiceManager.startVService(this)
    }

    private fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun importXrayConfig(response: GetHomeDataItemResponse) {
        val servers: List<String>? = response.subscriptionLinks
        if (servers == null || servers.isEmpty()) {
            Log.w(TAG, "cannot import config, empty server list")
            return
        }

        val server = servers[0]

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                mainViewModel.removeInvalidServer()
                mainViewModel.removeDuplicateServer()

                var count = 0;
                val currentServer = serverDao.findBySignature(response.signature)
                if (currentServer == null || currentServer.signature != response.signature) {
                    val (number, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                    count = number
                    val guid = MmkvManager.getSelectServer()
                    serverDao.insertServer(Server(0,
                        guid.toString(), response.signature, server, response.email, 2))
                    Log.i(TAG, "Add new subscription link: $server, $guid")
                } else {
                    MmkvManager.setSelectServer(currentServer.guid)
                }
                delay(500L)

                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            // toast(getString(R.string.title_import_config_count, count))
                            Log.i(TAG, "import config: " + getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                        }

                        // countSub > 0 -> initGroupTab()
                        else -> {
                            // toastError(R.string.toast_failure)
                            Log.e(TAG, "Import config failed")
                        }
                    }
                    // binding.pbWaiting.hide()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // toastError(R.string.toast_failure)
                    // binding.pbWaiting.hide()
                    Log.e(TAG, "Import config exception: $e")
                }
                Log.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    fun formatExpireDate(homeData: GetHomeDataItemResponse): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        var result: String? = null
        try {
            LocalDateTime.parse(homeData.expireAt, formatter)
            result = homeData.expireAt
        } catch (e: Exception) {
            Log.e(TAG, "Format expire date failed: $e")
        }

        if (result.isNullOrEmpty()) {
            result = getString(com.npv.crsgw.R.string.npv_purchase_membership)
        } else {
            // result = getString(com.npv.crsgw.R.string.npv_membership_status_normal, result)
        }
        return result
    }
}