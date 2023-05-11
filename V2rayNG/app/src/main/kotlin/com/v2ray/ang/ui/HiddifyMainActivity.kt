package com.v2ray.ang.ui

import android.Manifest
import android.content.*
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson

import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityHiddifyMainBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.extension.*
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.ui.bottomsheets.AddConfigBottomSheets
import com.v2ray.ang.ui.bottomsheets.BottomSheetPresenter
import com.v2ray.ang.ui.bottomsheets.ProfilesBottomSheets
import com.v2ray.ang.ui.bottomsheets.SettingBottomSheets
import com.v2ray.ang.util.*
import com.v2ray.ang.viewmodel.HiddifyMainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.drakeet.support.toast.ToastCompat
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit


class HiddifyMainActivity : BaseActivity(), /*NavigationView.OnNavigationItemSelectedListener,*/
    AddConfigBottomSheets.Callback, ProfilesBottomSheets.Callback,SettingBottomSheets.Callback {
    private lateinit var binding: ActivityHiddifyMainBinding
    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val adapter by lazy { HiddifyMainRecyclerAdapter(this) }
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private var connect_mode=HiddifyUtils.getMode();//1=smart 2=loadbalance 3=manual
    private var mItemTouchHelper: ItemTouchHelper? = null
    val hiddifyMainViewModel: HiddifyMainViewModel by viewModels()
    private val bottomSheetPresenter = BottomSheetPresenter()

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            importConfigViaSub(HiddifyUtils.getSelectedSubId())
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


        init()

        showLangDialog()
        if (hiddifyMainViewModel.serverList.isNotEmpty() &&Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RxPermissions(this)
                .request(Manifest.permission.POST_NOTIFICATIONS)
                .subscribe {
//                    if (!it)
//                        toast(R.string.toast_permission_denied)
                }
        }

    }

    private fun showGooglePlayReview() {
        if (settingsStorage?.containsKey(AppConfig.PREF_REVIEW_TIME)==true) {
            return
        }

        settingsStorage?.encode(AppConfig.PREF_REVIEW_TIME, System.currentTimeMillis())
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { request ->
            if (request.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = request.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    // The flow has finished. The API does not indicate whether the user
                    // reviewed or not, or even whether the review dialog was shown. Thus, no
                    // matter the result, we continue our app flow.
                }
            } else {
                // There was some problem, continue regardless of the result.
            }
        }
    }

    fun showLangDialog(){
        if (settingsStorage?.containsKey(AppConfig.PREF_LANGUAGE)==true) {
            showCountryDialog()
            return
        }
        MaterialAlertDialogBuilder(this,R.style.AppTheme_ThemeOverlay_MaterialComponents_MaterialAlertDialog)
            .setTitle(R.string.title_language)
            .setCancelable(false)
            .setItems(R.array.language_select) { dialog, which ->
                val lang = resources.getStringArray(R.array.language_select_value)[which]
                settingsStorage?.encode(AppConfig.PREF_LANGUAGE, lang)
                dialog.dismiss()
                val locale=Utils.getLocale(this);
                Locale.setDefault(locale)


                val resources = baseContext.resources
                val configuration = resources.configuration
                configuration.locale = locale
                configuration.setLayoutDirection(locale)

                resources.updateConfiguration(configuration, resources.displayMetrics)
//                setContentView(R.layout.activity_hiddify_main);
//                recreate()
                finish();
                startActivity(intent);

//
//                  restartActivity();

            }
            .show()
    }
    fun showCountryDialog(){
        if (settingsStorage?.containsKey(AppConfig.PREF_COUNTRY)==true) {
            return
        }
        MaterialAlertDialogBuilder(this,R.style.AppTheme_ThemeOverlay_MaterialComponents_MaterialAlertDialog)
            .setTitle(R.string.title_country)
            .setCancelable(false)

            .setItems(R.array.country_select) { dialog, which ->
                val country = resources.getStringArray(R.array.country_select_value)[which]
                HiddifyUtils.setCountry(country)
                dialog.dismiss()
            }
            .show()
    }
    private fun init() {
        binding.pingLayout.click {
            binding.ping.text="..."
            hiddifyMainViewModel.testCurrentServerRealPing()
        }

        binding.importFromClipBoard.click {
            importClipboard()
            importConfigViaSub(HiddifyUtils.getSelectedSubId())
        }

        binding.scanQrCode.click {
            importQRcode(true)
            importConfigViaSub(HiddifyUtils.getSelectedSubId())
        }

        binding.startButtonIcon.click {

            if (hiddifyMainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
            } else if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
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

        binding.toolbar.setting.click {
            bottomSheetPresenter.show(supportFragmentManager, AddConfigBottomSheets.newInstance())
        }

        binding.toolbar.test.click {
            open_old_v2ray()
        }
        
        binding.advanced.click {
            var current=hiddifyMainViewModel.currentSubscription()
            connect_mode=HiddifyUtils.getMode()
            bottomSheetPresenter.show(supportFragmentManager, SettingBottomSheets.newInstance(connect_mode))
        }

    }
    fun open_old_v2ray(){
        runOnUiThread{
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
    override fun onClipBoard() {
        importClipboard()
        importConfigViaSub(HiddifyUtils.getSelectedSubId())
    }

    override fun onQrCode() {
        importQRcode(true)
        importConfigViaSub(HiddifyUtils.getSelectedSubId())
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
                showGooglePlayReview()
                hiddifyMainViewModel.testCurrentServerRealPing()//hiddify
            } else {
                updateCircleState("disconnected")
                hiddifyMainViewModel.subscriptionsAddedCheck()
            }
            hideCircle()
        }

        hiddifyMainViewModel.subscriptionsAdded.observe(this) { check ->
            if (!check) {
                updateCircleState("default")
            } else {
                val enableSubscription =hiddifyMainViewModel.currentSubscription()

                enableSubscription?.let { subscription ->
                    binding.profileName.text = subscription.second.remarks

                    binding.time.text = HiddifyUtils.timeToRelativeDate(
                        subscription.second.expire, subscription.second.total,
                        subscription.second.used, this
                    )
                    binding.time.showGone(subscription.second.expire > (0).toLong())

                    binding.consumerTrafficValue.text = HiddifyUtils.toTotalUsedGig(
                        subscription.second.total,
                        subscription.second.used,
                        this
                    )
                    binding.consumerTrafficValue.showGone(subscription.second.total > (0).toLong())
                    binding.consumerTraffic.showGone(subscription.second.total > (0).toLong())

                    binding.progress.progress = (subscription.second.used / 1000000000).toInt()
                    binding.progress.max = (subscription.second.total / 1000000000).toInt()
                    binding.progress.showGone(subscription.second.total > (0).toLong())

                    binding.show.click {
                        if (subscription.second.home_link.isNullOrEmpty())
                            return@click
                        Utils.openUri(this,subscription.second.home_link)

                    }
                    binding.show.showGone(!subscription.second.home_link.isNullOrEmpty())


                    binding.profileName.click {
                        bottomSheetPresenter.show(
                            supportFragmentManager,
                            ProfilesBottomSheets.newInstance()
                        )
                    }
                    binding.addProfile.click {
                        bottomSheetPresenter.show(
                            supportFragmentManager,
                            AddConfigBottomSheets.newInstance()
                        )
                    }
                    binding.updateSubscription.click {
                        importConfigViaSub(HiddifyUtils.getSelectedSubId())
                    }
                    binding.updateSubscription.showGone(subscription.second.url.startsWith("http"))
                    binding.profileBox.show()
                } ?: also {
                    binding.profileBox.gone()
                }
                updateCircleState("ready")
            }
        }
        hiddifyMainViewModel.startListenBroadcast()
    }





    private fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            toast(R.string.no_server_selected)
            return
        }
        updateCircleState("loading")
//        toast(R.string.toast_services_start)
        val enableSubscription =hiddifyMainViewModel.currentSubscription()
        MmkvManager.sortByTestResults()
        enableSubscription?.let { subscription ->
            if (connect_mode != 3) {
                HiddifyUtils.setMode(connect_mode)
            }
        }

        V2RayServiceManager.startV2Ray(this)
        hideCircle()
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
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
        onSelectSub(HiddifyUtils.getSelectedSubId(),false)
//        HiddifyUtils.setMode(connect_mode)
        hiddifyMainViewModel.reloadServerList()
        hiddifyMainViewModel.reloadSubscriptionsState()
        if (V2RayServiceManager.v2rayPoint.isRunning) {
            updateCircleState("connected")
            hiddifyMainViewModel.testCurrentServerRealPing()
        }

        hiddifyMainViewModel.startListenBroadcast()




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
            importConfigViaSub(HiddifyUtils.getSelectedSubId())
            true
        }
        R.id.import_clipboard -> {
            importClipboard()
            importConfigViaSub(HiddifyUtils.getSelectedSubId())
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
            importConfigViaSub(HiddifyUtils.getSelectedSubId())
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
                    hiddifyMainViewModel.reloadSubscriptionsState()
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
                    hiddifyMainViewModel.reloadSubscriptionsState()
                }
                .show()
            true
        }
        R.id.sort_by_test_results -> {
            MmkvManager.sortByTestResults()
            hiddifyMainViewModel.reloadServerList()
            hiddifyMainViewModel.reloadSubscriptionsState()
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
    private fun importQRcode(forConfig: Boolean): Boolean {
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
            importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"), selectSub = true)
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
    private fun importClipboard(): Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            showAlarmIfnotSublink(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
    private fun importBatchConfig(
        server: String?,
        subid: String = "",
        selectSub: Boolean,
        append: Boolean = false
    ) {

        return importBatchConfig(Utils.Response(null,server),subid,append,selectSub)
    }

    private fun importBatchConfig(
        response: Utils.Response?,
        subid: String = "",
        selectSub: Boolean,
        append: Boolean = false
    ) {
        var server=response?.content
        val subid2 = if(subid.isNullOrEmpty()){
            if (server?.startsWith("http") == true)"" else "default"
        }else{
            subid
        }
        HiddifyUtils.extract_package_info_from_response(response,subid)

        val append = append||subid.isNullOrEmpty() || subid=="default"
        var count = AngConfigManager.importBatchConfig(server, subid2, append, selectSub = selectSub)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server!!), subid2, append, selectSub = selectSub)
        }
        if (count > 0) {
            if(selectSub) {
                HiddifyUtils.setMode(connect_mode)
                onSelectSub(subid)
            }
            hiddifyMainViewModel.testAllRealPing()
            toast(R.string.toast_success)
            hiddifyMainViewModel.reloadServerList()
            hiddifyMainViewModel.reloadSubscriptionsState()
        } else {
            toast(R.string.toast_failure)
        }
    }

    private fun importConfigCustomClipboard(): Boolean {
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
    fun importConfigViaSub(subid: String?=null) : Boolean {
        try {
//            toast(R.string.title_sub_update)
            MmkvManager.decodeSubscriptions().forEach {
                if (subid!=null&&it.first!=subid)return@forEach
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
                        importBatchConfig(configText, it.first,false)
                        hiddifyMainViewModel.testAllRealPing()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast(R.string.title_sub_update_failed)
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
            hiddifyMainViewModel.reloadSubscriptionsState()
            toast(R.string.toast_success)
            //adapter.notifyItemInserted(hiddifyMainViewModel.serverList.lastIndex)
        } catch (e: Exception) {
            ToastCompat.makeText(this, "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }
    }

    private fun setTestState(content: Pair<Long,String>?) {
        //binding.tvTestState.text = content
        if (content==null)return

        var text=if (content!!.first>=0)content!!.first.toString()+" ms" else getString(R.string.toast_failure)
        if (Utils.getLocale(this).toString().startsWith("fa"))
            text=text.toPersianDigit()
        binding.ping.text=text
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
        binding.pingLayout.showHide(state=="connected")
        binding.ping.text="..."
        when(state) {
            "loading" -> {
                binding.importButtons.gone()
                binding.startButton.background = ContextCompat.getDrawable(this, R.drawable.ic_circle_connecting)

                binding.startButtonIcon.imageTintList = ColorStateList.valueOf(this.getColorEx(R.color.colorYellow))
                binding.connectState.text = getString(R.string.connecting)
                binding.connectState.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorYellow)))
                binding.advanced.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorText)))
                binding.advanced.iconTint = ColorStateList.valueOf(this.getColorEx(R.color.colorText))
                binding.advanced.isEnabled = true
            }
            "connected" -> {
                binding.importButtons.gone()

                binding.startButton.background = ContextCompat.getDrawable(this, R.drawable.ic_circle_connect)
                binding.startButtonIcon.imageTintList = ColorStateList.valueOf(this.getColorEx(R.color.colorGreen))
                binding.connectState.text = getString(R.string.connected)
                binding.connectState.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorGreen)))
                binding.advanced.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorText)))
                binding.advanced.iconTint = ColorStateList.valueOf(this.getColorEx(R.color.colorText))
                binding.advanced.isEnabled = true
            }
            "ready" -> {
                binding.importButtons.gone()
                binding.startButton.background = ContextCompat.getDrawable(this, R.drawable.ic_circle_ready)
//                binding.startButton.backgroundTintList= ColorStateList.valueOf(this.getColorEx(R.color.colorPrimary2))
//                binding.startButton.backgroundTintBlendMode=BlendMode.MULTIPLY;
                binding.startButtonIcon.imageTintList = ColorStateList.valueOf(this.getColorEx(R.color.colorPrimary2))



                binding.connectState.text = getString(R.string.tab_to_connect)
                binding.connectState.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorPrimary2)))
//                binding.advanced.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorText)))
//                binding.advanced.iconTint = ColorStateList.valueOf(this.getColorEx(R.color.colorText))
                binding.advanced.isEnabled = true
            }
            else -> {
                binding.importButtons.show()
                binding.startButton.background = ContextCompat.getDrawable(this, R.drawable.ic_circle_default)
                binding.startButtonIcon.imageTintList = ColorStateList.valueOf(this.getColorEx(R.color.colorDisable))
                binding.connectState.text = getString(R.string.default_layout_description)
                binding.connectState.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorPrimary2)))
//                binding.advanced.setTextColor(ColorStateList.valueOf(this.getColorEx(R.color.colorBorder)))
//                binding.advanced.iconTint = ColorStateList.valueOf(this.getColorEx(R.color.colorBorder))
                binding.advanced.isEnabled = false
                binding.profileBox.gone()
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
    }
*/
    override fun onAddProfile() {
        bottomSheetPresenter.show(supportFragmentManager, AddConfigBottomSheets.newInstance())
    }

    override fun onImportQrCode() {
        importQRcode(true)
        importConfigViaSub(HiddifyUtils.getSelectedSubId())
    }

    override fun onSelectSub(subid: String) {
        onSelectSub(subid,true)
    }

    override fun onUpdateSubList() {
        importConfigViaSub()
    }

    fun onSelectSub(subid: String,do_ping:Boolean=true) {
        if (HiddifyUtils.getSelectedSubId()!=subid) {
            HiddifyUtils.setSelectedSub(subid)
            HiddifyUtils.setMode(connect_mode)
        }

        hiddifyMainViewModel.subscriptionId = subid


        hiddifyMainViewModel.reloadServerList()
        hiddifyMainViewModel.reloadSubscriptionsState()

        val enableSubscription =hiddifyMainViewModel.currentSubscription()
        if (enableSubscription?.second?.needUpdate() == true){
            importConfigViaSub(HiddifyUtils.getSelectedSubId())
        }else if (do_ping){
            hiddifyMainViewModel.testAllRealPing()
        }

    }

    override fun onRemoveSelectSub(subid: String) {
        if (subid=="default")return
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                MmkvManager.removeSubscription(subid)
                if (subid==HiddifyUtils.getSelectedSubId())
                    HiddifyUtils.setSelectedSub("default")
                hiddifyMainViewModel.reloadServerList()
                hiddifyMainViewModel.reloadSubscriptionsState()
            }
            .show()

    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onModeChange(mode: Int) {
        connect_mode=mode;
        if (mode==3){
            bottomSheetPresenter.dismiss()
            open_old_v2ray()
        }else{
            restartV2Ray()
        }
    }

    override fun onPerAppProxyModeChange(mode: HiddifyUtils.PerAppProxyMode) {
        HiddifyUtils.setPerAppProxyMode(mode)
        restartV2Ray()
    }


    fun showAlarmIfnotSublink(content1: String) {
        if (content1.isNullOrEmpty()){
            toast(R.string.title_sub_update_failed)
            return
        }
        var content=if(content1.startsWith("hiddify"))Uri.parse(content1.trim()).getQueryParameter("url")?:"" else content1.trim()
        if (content.startsWith("http")){
            var subid=MmkvManager.importUrlAsSubscription(content)
            onSelectSub(subid)
//            importConfigViaSub(subid)
            return
        }
        val subscriptions = MmkvManager.decodeSubscriptions().filter { it.second.enabled &&!Utils.isValidUrl(it.second.url) }
        val listId = subscriptions.map { it.first }.toList().toMutableList()
        val listRemarks = subscriptions.map { it.second.remarks }.toList().toMutableList()
        listId.add(0,"")
        listRemarks.add(0,getString(R.string.new_item))
        val context=this
        var actv = Spinner(context)
        var ll=LinearLayout(context)
        ll.orientation=LinearLayout.VERTICAL
        val tv=TextView(context)
        tv.setText(R.string.no_sublink_found)
        ll.addView(tv)
        val params = tv.layoutParams as ViewGroup.MarginLayoutParams
        params.setMargins(24, 0, 24, 0)
        tv.layoutParams = params
        ll.addView(actv)
        val customName=TextInputEditText(this)
        ll.addView(customName)
        customName.hint=getString(R.string.msg_enter_group_name)
        customName.visibility=View.GONE
        actv.setAdapter(ArrayAdapter<String>( context, android.R.layout.simple_spinner_dropdown_item, listRemarks))
        var selectedSubid=""
        actv.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Handle the selection of an item
//                val selectedItem = parent?.getItemAtPosition(position).toString()

                customName.visibility=if (position==0)View.VISIBLE else View.GONE

                selectedSubid=listId[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Handle the case where nothing is selected
            }
        }
            val selectedIndex=listId.indexOf(HiddifyUtils.getSelectedSubId())
            if (selectedIndex>=0)
                actv.setSelection(selectedIndex)

            //        actv.threshold = 1

    //        actv.completionHint=getString(R.string.msg_enter_keywords)
    //        actv.hint=getString(R.string.msg_enter_group_name)


            val builder = AlertDialog.Builder(context).setView(ll)
            builder.setTitle(R.string.autoconfig_link_not_found)
            builder.setPositiveButton(R.string.tasker_setting_confirm) { dialogInterface: DialogInterface?, _: Int ->
                try {
                    var selected_sub= if (selectedSubid.isNullOrEmpty())
                        Pair(Utils.getUuid(),SubscriptionItem(remarks = customName.text.toString()))
                    else
                        subscriptions.find { it.first==selectedSubid }!!
                    if (selectedSubid.isNullOrEmpty()){
                        subStorage?.encode(selected_sub.first, Gson().toJson(selected_sub.second))
                        hiddifyMainViewModel. reloadServerList()
                    }
//                    onSelectSub(selected_sub.first)
                    importBatchConfig(content, selected_sub.first,append = true, selectSub = true)
                    dialogInterface?.dismiss()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            builder.show()


        }
}
