package com.v2ray.ang.ui

import android.Manifest
import android.content.*
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityHiddifyMainBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.extension.*
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.ui.bottomsheets.AddConfigBottomSheets
import com.v2ray.ang.ui.bottomsheets.BottomSheetPresenter
import com.v2ray.ang.ui.bottomsheets.SettingBottomSheets
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.SpeedtestUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.HiddifyMainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.drakeet.support.toast.ToastCompat
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class HiddifyMainActivity : BaseActivity()/*, NavigationView.OnNavigationItemSelectedListener*/,
    AddConfigBottomSheets.Callback {
    private lateinit var binding: ActivityHiddifyMainBinding
    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val adapter by lazy { HiddifyMainRecyclerAdapter(this) }
    private val subAdapter by lazy { HiddifyMainSubAdapter(this) }
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private var mItemTouchHelper: ItemTouchHelper? = null
    val hiddifyMainViewModel: HiddifyMainViewModel by viewModels()
    private val bottomSheetPresenter = BottomSheetPresenter()

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            importConfigViaSub()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerReceiver(receiver, IntentFilter(AppConfig.BROADCAST_ACTION_UPDATE_UI))

        binding = ActivityHiddifyMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        title = ""
        setSupportActionBar(binding.toolbar.toolbar)

        //val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        //binding.drawerLayout.addDrawerListener(toggle)
        //toggle.syncState()
        //binding.navView.setNavigationItemSelectedListener(this)
        //binding.version.text = "v${BuildConfig.VERSION_NAME} (${SpeedtestUtil.getLibVersion()})"

        setupViewModel()
        copyAssets()
        migrateLegacy()
        init()
    }

    private fun init() {
        binding.importFromClipBoard.click {
            importClipboard()
            importConfigViaSub()
        }

        binding.scanQrCode.click {
            importQRcode(true)
            importConfigViaSub()
        }

        binding.startButtonIcon.click {
            startV2Ray()
        }

        binding.toolbar.setting.click {
            bottomSheetPresenter.show(supportFragmentManager, AddConfigBottomSheets.newInstance())
        }
        
        binding.advanced.click {
            bottomSheetPresenter.show(supportFragmentManager, SettingBottomSheets.newInstance())
        }
    }

    override fun onClipBoard() {
        importClipboard()
        importConfigViaSub()
    }

    override fun onQrCode() {
        importQRcode(true)
        importConfigViaSub()
    }

    private fun setupViewModel() {
        hiddifyMainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        hiddifyMainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        hiddifyMainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (isRunning) {
                updateCircleState("connected")
            } else {
                hiddifyMainViewModel.subscriptionsAddedCheck()
            }
            hideCircle()
        }

        hiddifyMainViewModel.subscriptionsAdded.observe(this) { check ->
            if (!check) {
                updateCircleState("default")
            } else {
                updateCircleState("ready")
            }
        }

        hiddifyMainViewModel.startListenBroadcast()
    }

    private fun copyAssets() {
        val extFolder = Utils.userAssetPath(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geo = arrayOf("geosite.dat", "geoip.dat")
                assets.list("")
                    ?.filter { geo.contains(it) }
                    ?.filter { !File(extFolder, it).exists() }
                    ?.forEach {
                        val target = File(extFolder, it)
                        assets.open(it).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.i(AppConfig.ANG_PACKAGE, "Copied from apk assets folder to ${target.absolutePath}")
                    }
            } catch (e: Exception) {
                Log.e(AppConfig.ANG_PACKAGE, "asset copy failed", e)
            }
        }
    }

    private fun migrateLegacy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.migrateLegacyConfig(this@HiddifyMainActivity)
            if (result != null) {
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.migration_success))
                        hiddifyMainViewModel.reloadServerList()
                    } else {
                        toast(getString(R.string.migration_fail))
                    }
                }
            }
        }
    }

    private fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        updateCircleState("loading")
//        toast(R.string.toast_services_start)
        V2RayServiceManager.startV2Ray(this)
        hideCircle()
    }

    private fun restartV2Ray() {
        if (hiddifyMainViewModel.isRunning.value == true) {
            Utils.stopVService(this)
        }
        Observable.timer(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                startV2Ray()
            }
    }

    public override fun onResume() {
        super.onResume()
        hiddifyMainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        //menuInflater.inflate(R.menu.menu_main, menu)
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode(true)
            importConfigViaSub()
            true
        }
        R.id.import_clipboard -> {
            importClipboard()
            importConfigViaSub()
            true
        }
        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }
        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }
        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }
        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }
        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
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
            importQRcode(false)
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
            if (AngConfigManager.shareNonCustomConfigsToClipboard(this, hiddifyMainViewModel.serverList) == 0) {
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
            true
        }

        R.id.ping_all -> {
            hiddifyMainViewModel.testAllTcping()
            true
        }

        R.id.real_ping_all -> {
            hiddifyMainViewModel.testAllRealPing()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeAllServer()
                    hiddifyMainViewModel.reloadServerList()
                }
                .show()
            true
        }
        R.id.del_duplicate_config-> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    hiddifyMainViewModel.removeDuplicateServer()
                }
                .show()
            true
        }
        R.id.del_invalid_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeInvalidServer()
                    hiddifyMainViewModel.reloadServerList()
                }
                .show()
            true
        }
        R.id.sort_by_test_results -> {
            MmkvManager.sortByTestResults()
            hiddifyMainViewModel.reloadServerList()
            true
        }
        R.id.filter_config -> {
            hiddifyMainViewModel.filterConfig(this)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType : Int) {
        startActivity(
            Intent()
                .putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", hiddifyMainViewModel.subscriptionId)
                .setClass(this, ServerActivity::class.java)
        )
    }

    /**
     * import config from qrcode
     */
    fun importQRcode(forConfig: Boolean): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(this)
            .request(Manifest.permission.CAMERA)
            .subscribe {
                if (it)
                    if (forConfig)
                        scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
                    else
                        scanQRCodeForUrlToCustomConfig.launch(Intent(this, ScannerActivity::class.java))
                else
                    toast(R.string.toast_permission_denied)
            }
//        }
        return true
    }

    private val scanQRCodeForConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private val scanQRCodeForUrlToCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importConfigCustomUrl(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    /**
     * import config from clipboard
     */
    private fun importClipboard()
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
    private fun importBatchConfig(server: String?, subid: String = "") {
        return importBatchConfig(Utils.Response(null,server),subid)
    }

    private fun importBatchConfig(response: Utils.Response?, subid: String = "") {
        var server=response?.content
        val subid2 = if(subid.isNullOrEmpty()){
            hiddifyMainViewModel.subscriptionId
        }else{
            subid
        }
        if( !subid.isNullOrEmpty() && response?.headers!=null) {
            val json = subStorage?.decodeString(subid)
            if (!json.isNullOrBlank()) {
                var sub = Gson().fromJson(json, SubscriptionItem::class.java)
                var userinfo=response.headers.getOrDefault("Subscription-Userinfo",null)?.firstOrNull()
                var homepage=response.headers.getOrDefault("Profile-Web-Page-Url",null)?.firstOrNull()
                var content_disposition=response.headers.getOrDefault("Content-Disposition",null)?.firstOrNull()
                var profile_update_interval=response.headers.getOrDefault("Profile-Update-Interval",null)?.firstOrNull()
                if (!content_disposition.isNullOrEmpty()){
                    val regex = "filename=\"([^\"]+)\"".toRegex()
                    val matchResult = regex.find(content_disposition)
                    sub.remarks = matchResult?.groupValues?.getOrNull(1)?:sub.remarks
                }else if (!response.url.isNullOrEmpty()){
                    var uri= Uri.parse(response.url)
                    sub.remarks = Utils.getQueryParameterValueCaseInsensitive(uri,"Name")?:sub.remarks
                }
                if (!homepage.isNullOrEmpty()){
                    sub.home_link=homepage
                }
                if (!userinfo.isNullOrEmpty()){
                    fun get(regex: String): String? {
                        return regex.toRegex().findAll(userinfo).mapNotNull {
                            if (it.groupValues.size > 1) it.groupValues[1] else null
                        }.firstOrNull();
                    }

                    sub.used = 0
                    get("upload=([0-9]+)")?.apply {
                        sub.used += toLong()
                    }
                    get("download=([0-9]+)")?.apply {
                        sub.used += toLong()
                    }
                    sub.total = get("total=([0-9]+)")?.toLong() ?: 0

                    sub.expire=get("expire=([0-9]+)")?.toLong() ?: 0
                }

                subStorage?.encode(subid, Gson().toJson(sub))
            }
        }
        val append = subid.isNullOrEmpty()

        var count = AngConfigManager.importBatchConfig(server, subid2, append)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server!!), subid2, append)
        }
        if (count > 0) {


            toast(R.string.toast_success)
            hiddifyMainViewModel.reloadServerList()
        } else {

            toast(R.string.toast_failure)
        }
    }

    private fun importConfigCustomClipboard()
            : Boolean {
        try {
            val configText = Utils.getClipboard(this)
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(Utils.Response(null,configText))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from local config file
     */
    private fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun importConfigCustomUrlClipboard()
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
    private fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val configText = try {
                    Utils.getUrlContentWithCustomUserAgent(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Utils.Response(null,"")
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
                if (!it.second.enabled) {
                    return@forEach
                }
                val url = Utils.idnToASCII(it.second.url)
                if (!Utils.isValidUrl(url)) {
                    return@forEach
                }
                Log.d(AppConfig.ANG_PACKAGE, url)
                lifecycleScope.launch(Dispatchers.IO) {
                    val configText = try {
                        Utils.getUrlContentWithCustomUserAgent(url)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            toast("\"" + it.second.remarks + "\" " + getString(R.string.toast_failure))
                        }
                        return@launch
                    }
                    launch(Dispatchers.Main) {
                        importBatchConfig(configText, it.first)
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            hiddifyMainViewModel. testAllRealPing()
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
            chooseFileForCustomConfig.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFileForCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            readContentFromUri(uri)
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
    private fun importCustomizeConfig(response: String?) {
        return importCustomizeConfig(Utils.Response(null,response))

    }

    private fun importCustomizeConfig(response: Utils.Response) {
        val server=response?.content
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                toast(R.string.toast_none_data)
                return
            }
            hiddifyMainViewModel.appendCustomConfigServer(response)
            hiddifyMainViewModel.reloadServerList()
            toast(R.string.toast_success)
            //adapter.notifyItemInserted(hiddifyMainViewModel.serverList.lastIndex)
        } catch (e: Exception) {
            ToastCompat.makeText(this, "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }
    }

    private fun setTestState(content: String?) {
        //binding.tvTestState.text = content
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

    fun updateCircleState(state: String) {
        when(state) {
            "loading" -> {
                binding.importButtons.gone()
                binding.startButton.background = ContextCompat.getDrawable(this, R.drawable.ic_circle_connecting)
                binding.startButtonIcon.imageTintList = ColorStateList.valueOf(this.getColorEx(R.color.colorYellow))
                binding.connectState.text = getString(R.string.connecting)
                binding.connectState.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorYellow)))
                binding.advanced.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorBlack)))
                binding.advanced.iconTint = ColorStateList.valueOf(this.getColorEx(R.color.colorBlack))
            }
            "connected" -> {
                binding.importButtons.gone()
                binding.startButton.background = ContextCompat.getDrawable(this, R.drawable.ic_circle_connect)
                binding.startButtonIcon.imageTintList = ColorStateList.valueOf(this.getColorEx(R.color.colorGreen))
                binding.connectState.text = getString(R.string.connected)
                binding.connectState.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorGreen)))
                binding.advanced.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorBlack)))
                binding.advanced.iconTint = ColorStateList.valueOf(this.getColorEx(R.color.colorBlack))
            }
            "ready" -> {
                binding.importButtons.gone()
                binding.startButton.background = ContextCompat.getDrawable(this, R.drawable.ic_circle_ready)
                binding.startButtonIcon.imageTintList = ColorStateList.valueOf(this.getColorEx(R.color.colorPrimary2))
                binding.connectState.text = getString(R.string.tab_to_connect)
                binding.connectState.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorPrimary2)))
                binding.advanced.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorBlack)))
                binding.advanced.iconTint = ColorStateList.valueOf(this.getColorEx(R.color.colorBlack))
            }
            else -> {
                binding.importButtons.show()
                binding.startButton.background = ContextCompat.getDrawable(this, R.drawable.ic_circle_default)
                binding.startButtonIcon.imageTintList = ColorStateList.valueOf(this.getColorEx(R.color.colorDisable))
                binding.connectState.text = getString(R.string.default_layout_description)
                binding.connectState.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorPrimary2)))
                binding.advanced.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorBorder)))
                binding.advanced.iconTint = ColorStateList.valueOf(this.getColorEx(R.color.colorBorder))
            }
        }
    }

    fun hideCircle() {
        try {
            Observable.timer(300, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    try {
//                            if (binding.fabProgressCircle.isShown) {
//                                binding.fabProgressCircle.hide()
//                            }
                    } catch (e: Exception) {
                        Log.w(AppConfig.ANG_PACKAGE, e)
                    }
                }
        } catch (e: Exception) {
            Log.d(AppConfig.ANG_PACKAGE, e.toString())
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    /*override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            //R.id.server_profile -> activityClass = MainActivity::class.java
            R.id.sub_setting -> {
                startActivity(Intent(this, SubSettingActivity::class.java))
            }
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)
                    .putExtra("isRunning", hiddifyMainViewModel.isRunning.value == true))
            }
            R.id.user_asset_setting -> {
                startActivity(Intent(this, UserAssetActivity::class.java))
            }
            R.id.feedback -> {
                Utils.openUri(this, AppConfig.v2rayNGIssues)
            }
            R.id.promotion -> {
                Utils.openUri(this, "${Utils.decode(AppConfig.promotionUrl)}")
            }
            R.id.logcat -> {
                startActivity(Intent(this, LogcatActivity::class.java))
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }*/
}
