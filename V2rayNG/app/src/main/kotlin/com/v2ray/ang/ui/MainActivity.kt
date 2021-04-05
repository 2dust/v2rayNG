package com.v2ray.ang.ui

import android.Manifest
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import me.drakeet.support.toast.ToastCompat
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        private const val REQUEST_CODE_VPN_PREPARE = 0
        private const val REQUEST_SCAN = 1
        private const val REQUEST_FILE_CHOOSER = 2
        private const val REQUEST_SCAN_URL = 3
    }

    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private var mItemTouchHelper: ItemTouchHelper? = null
    val mainViewModel: MainViewModel by lazy { ViewModelProviders.of(this).get(MainViewModel::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = getString(R.string.title_server)
        setSupportActionBar(toolbar)

        fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
            } else if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    startActivityForResult(intent, REQUEST_CODE_VPN_PREPARE)
                }
            } else {
                startV2Ray()
            }
        }
        layout_test.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                tv_test_state.text = getString(R.string.connection_test_testing)
                mainViewModel.testCurrentServerRealPing()
            } else {
//                tv_test_state.text = getString(R.string.connection_test_fail)
            }
        }

        recycler_view.setHasFixedSize(true)
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(recycler_view)


        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        nav_view.setNavigationItemSelectedListener(this)
        version.text = "v${BuildConfig.VERSION_NAME} (${Libv2ray.checkVersionX()})"

        setupViewModelObserver()
        migrateLegacy()
    }

    private fun setupViewModelObserver() {
        mainViewModel.updateListAction.observe(this, {
            val index = it ?: return@observe
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        })
        mainViewModel.updateTestResultAction.observe(this, { tv_test_state.text = it })
        mainViewModel.isRunning.observe(this, {
            val isRunning = it ?: return@observe
            adapter.isRunning = isRunning
            if (isRunning) {
                fab.setImageResource(R.drawable.ic_v)
                tv_test_state.text = getString(R.string.connection_connected)
                layout_test.isFocusable = true
            } else {
                fab.setImageResource(R.drawable.ic_v_idle)
                tv_test_state.text = getString(R.string.connection_not_connected)
                layout_test.isFocusable = false
            }
            hideCircle()
        })
        mainViewModel.startListenBroadcast()
    }

    private fun migrateLegacy() {
        GlobalScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.migrateLegacyConfig(this@MainActivity)
            if (result != null) {
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.migration_success))
                        mainViewModel.reloadServerList()
                    } else {
                        toast(getString(R.string.migration_fail))
                    }
                }
            }
        }
    }

    fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        showCircle()
//        toast(R.string.toast_services_start)
        V2RayServiceManager.startV2Ray(this)
        hideCircle()
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_VPN_PREPARE ->
                if (resultCode == RESULT_OK) {
                    startV2Ray()
                }
            REQUEST_SCAN ->
                if (resultCode == RESULT_OK) {
                    importBatchConfig(data?.getStringExtra("SCAN_RESULT"))
                }
            REQUEST_FILE_CHOOSER -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    readContentFromUri(uri)
                }
            }
            REQUEST_SCAN_URL ->
                if (resultCode == RESULT_OK) {
                    importConfigCustomUrl(data?.getStringExtra("SCAN_RESULT"))
                }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode(REQUEST_SCAN)
            true
        }
        R.id.import_clipboard -> {
            importClipboard()
            true
        }
        R.id.import_manually_vmess -> {
            startActivity(Intent().putExtra("createConfigType", EConfigType.VMESS.value).
            setClass(this, ServerActivity::class.java))
            true
        }
        R.id.import_manually_ss -> {
            startActivity(Intent().putExtra("createConfigType", EConfigType.SHADOWSOCKS.value).
            setClass(this, ServerActivity::class.java))
            true
        }
        R.id.import_manually_socks -> {
            startActivity(Intent().putExtra("createConfigType", EConfigType.SOCKS.value).
            setClass(this, ServerActivity::class.java))
            true
        }
        R.id.import_config_custom_clipboard -> {
            importConfigCustomClipboard()
            true
        }
        R.id.import_config_custom_local -> {
            importConfigCustomLocal()
            true
        }
        R.id.import_config_custom_url -> {
            importConfigCustomUrlClipboard()
            true
        }
        R.id.import_config_custom_url_scan -> {
            importQRcode(REQUEST_SCAN_URL)
            true
        }

//        R.id.sub_setting -> {
//            startActivity<SubSettingActivity>()
//            true
//        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        R.id.export_all -> {
            if (AngConfigManager.shareNonCustomConfigsToClipboard(this, mainViewModel.serverList) == 0) {
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
            true
        }

        R.id.ping_all -> {
            mainViewModel.testAllTcping()
            true
        }

//        R.id.settings -> {
//            startActivity<SettingsActivity>("isRunning" to isRunning)
//            true
//        }
//        R.id.logcat -> {
//            startActivity<LogcatActivity>()
//            true
//        }
        else -> super.onOptionsItemSelected(item)
    }


    /**
     * import config from qrcode
     */
    fun importQRcode(requestCode: Int): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        startActivityForResult(Intent(this, ScannerActivity::class.java), requestCode)
                    else
                        toast(R.string.toast_permission_denied)
                }
//        }
        return true
    }

    /**
     * import config from clipboard
     */
    fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importBatchConfig(server: String?, subid: String = "") {
        var count = AngConfigManager.importBatchConfig(server, subid)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server!!), subid)
        }
        if (count > 0) {
            toast(R.string.toast_success)
            mainViewModel.reloadServerList()
        } else {
            toast(R.string.toast_failure)
        }
    }

    fun importConfigCustomClipboard()
            : Boolean {
        try {
            val configText = Utils.getClipboard(this)
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from local config file
     */
    fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importConfigCustomUrlClipboard()
            : Boolean {
        try {
            val url = Utils.getClipboard(this)
            if (TextUtils.isEmpty(url)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            return importConfigCustomUrl(url)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from url
     */
    fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            GlobalScope.launch(Dispatchers.IO) {
                val configText = try {
                    URL(url).readText()
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
                launch(Dispatchers.Main) {
                    importCustomizeConfig(configText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    fun importConfigViaSub()
            : Boolean {
        try {
            toast(R.string.title_sub_update)
            MmkvManager.decodeSubscriptions().forEach {
                if (TextUtils.isEmpty(it.first)
                        || TextUtils.isEmpty(it.second.remarks)
                        || TextUtils.isEmpty(it.second.url)
                ) {
                    return@forEach
                }
                val url = it.second.url
                if (!Utils.isValidUrl(url)) {
                    return@forEach
                }
                Log.d("Main", url)
                GlobalScope.launch(Dispatchers.IO) {
                    val configText = try {
                        URL(url).readText()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ""
                    }
                    launch(Dispatchers.Main) {
                        importBatchConfig(Utils.decode(configText), it.first)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.title_file_chooser)),
                    REQUEST_FILE_CHOOSER)
        } catch (ex: android.content.ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        RxPermissions(this)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe {
                    if (it) {
                        try {
                            contentResolver.openInputStream(uri).use { input ->
                                importCustomizeConfig(input?.bufferedReader()?.readText())
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else
                        toast(R.string.toast_permission_denied)
                }
    }

    /**
     * import customize config
     */
    fun importCustomizeConfig(server: String?) {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                toast(R.string.toast_none_data)
                return
            }
            mainViewModel.appendCustomConfigServer(server)
            toast(R.string.toast_success)
            adapter.notifyItemInserted(mainViewModel.serverList.lastIndex)
        } catch (e: Exception) {
            ToastCompat.makeText(this, "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }
    }

//    val mConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName?) {
//        }
//
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
//        }
//    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun showCircle() {
        fabProgressCircle?.show()
    }

    fun hideCircle() {
        try {
            Observable.timer(300, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        if (fabProgressCircle.isShown) {
                            fabProgressCircle.hide()
                        }
                    }
        } catch (e: Exception) {
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            //R.id.server_profile -> activityClass = MainActivity::class.java
            R.id.sub_setting -> {
                startActivity(Intent(this, SubSettingActivity::class.java))
            }
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)
                        .putExtra("isRunning", mainViewModel.isRunning.value == true))
            }
            R.id.feedback -> {
                Utils.openUri(this, AppConfig.v2rayNGIssues)
            }
            R.id.promotion -> {
                Utils.openUri(this, AppConfig.promotionUrl)
            }
            R.id.donate -> {
//                startActivity<InappBuyActivity>()
            }
            R.id.logcat -> {
                startActivity(Intent(this, LogcatActivity::class.java))
            }
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }
}
