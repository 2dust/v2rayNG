package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogConfigFilterBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.ServersCache
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.OutSettingsBean.FragmentBean
import com.v2ray.ang.dto.FragmentsCache
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.MmkvManager.KEY_ANG_CONFIGS
import com.v2ray.ang.util.SpeedtestUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.V2rayConfigUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.util.Collections

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val serverRawStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SERVER_RAW,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
        )
    }

    var serverList = MmkvManager.decodeServerList()
    var subscriptionId: String = settingsStorage.decodeString(AppConfig.CACHE_SUBSCRIPTION_ID, "")!!
    var keywordFilter: String = settingsStorage.decodeString(AppConfig.CACHE_KEYWORD_FILTER, "")!!
        private set
    val serversCache = mutableListOf<ServersCache>()
    val fragmentsCache = mutableListOf<FragmentsCache>()
    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }

    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    fun startListenBroadcast() {
        isRunning.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<AngApplication>().registerReceiver(
                mMsgReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Context.RECEIVER_EXPORTED
            )
        } else {
            getApplication<AngApplication>().registerReceiver(
                mMsgReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
            )
        }
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        SpeedtestUtil.closeAllTcpSockets()
        Log.i(ANG_PACKAGE, "Main ViewModel is cleared")
        super.onCleared()
    }

    fun reloadServerList() {
        serverList = MmkvManager.decodeServerList()
        updateCache()
        updateListAction.value = -1
    }

    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        val index = getPosition(guid)
        if (index >= 0) {
            serversCache.removeAt(index)
        }
    }

    fun appendCustomConfigServer(server: String): Boolean {
        if (server.contains("inbounds")
            && server.contains("outbounds")
            && server.contains("routing")
        ) {
            try {
                val config = ServerConfig.create(EConfigType.CUSTOM)
                config.subscriptionId = subscriptionId
                config.fullConfig = Gson().fromJson(server, V2rayConfig::class.java)
                config.remarks = config.fullConfig?.remarks ?: System.currentTimeMillis().toString()
                val key = MmkvManager.encodeServerConfig("", config)
                serverRawStorage?.encode(key, server)
                serverList.add(0, key)
                serversCache.add(0, ServersCache(key, config))
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(serversCache, fromPosition, toPosition)
        mainStorage?.encode(KEY_ANG_CONFIGS, Gson().toJson(serverList))
    }

    @Synchronized
    fun updateCache() {
        serversCache.clear()
        for (guid in serverList) {
            val config = MmkvManager.decodeServerConfig(guid) ?: continue
            if (subscriptionId.isNotEmpty() && subscriptionId != config.subscriptionId) {
                continue
            }

            if (keywordFilter.isEmpty() || config.remarks.contains(keywordFilter)) {
                serversCache.add(ServersCache(guid, config))
            }
        }
    }

    fun testAllTcping() {
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        SpeedtestUtil.closeAllTcpSockets()
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        updateListAction.value = -1 // update all

        getApplication<AngApplication>().toast(R.string.connection_test_testing)
        for (item in serversCache) {
            item.config.getProxyOutbound()?.let { outbound ->
                val serverAddress = outbound.getServerAddress()
                val serverPort = outbound.getServerPort()
                if (serverAddress != null && serverPort != null) {
                    tcpingTestScope.launch {
                        val testResult = SpeedtestUtil.tcping(serverAddress, serverPort)
                        launch(Dispatchers.Main) {
                            MmkvManager.encodeServerTestDelayMillis(item.guid, testResult)
                            updateListAction.value = getPosition(item.guid)
                        }
                    }
                }
            }
        }
    }

    fun testAllRealPing() {
        MessageUtil.sendMsg2TestService(getApplication(), AppConfig.MSG_MEASURE_CONFIG_CANCEL, "")
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        updateListAction.value = -1 // update all

        val serversCopy = serversCache.toList() // Create a copy of the list

        getApplication<AngApplication>().toast(R.string.connection_test_testing)
        viewModelScope.launch(Dispatchers.Default) { // without Dispatchers.Default viewModelScope will launch in main thread
            for (item in serversCopy) {
                val config = V2rayConfigUtil.getV2rayConfig(getApplication(), item.guid)
                if (config.status) {
                    MessageUtil.sendMsg2TestService(
                        getApplication(),
                        AppConfig.MSG_MEASURE_CONFIG,
                        Pair(item.guid, config.content)
                    )
                }
            }
        }
    }

    fun findBestFragmentSettings() {
        MessageUtil.sendMsg2TestService(getApplication(), AppConfig.MSG_MEASURE_FRAGMENT_CANCEL, "")

        val guid = mainStorage.decodeString(MmkvManager.KEY_SELECTED_SERVER) ?: ""
        val config = MmkvManager.decodeServerConfig(guid)
        if (config?.configType == EConfigType.VLESS || config?.configType == EConfigType.VMESS) {
            if (config.outboundBean?.streamSettings?.security == "tls") {
                getApplication<AngApplication>().toast(R.string.fragment_test_started)
                val prefFragmentEnabled = settingsStorage.decodeBool(AppConfig.PREF_FRAGMENT_ENABLED, false)
                settingsStorage.putBoolean(AppConfig.PREF_FRAGMENT_ENABLED, false)
                val configJson = V2rayConfigUtil.getV2rayConfig(getApplication(), guid).content
                fragmentsCache.clear()
                fragmentsCache.add(FragmentsCache(FragmentBean("")))
                for (length in arrayOf(5, 7, 10, 12, 15, 20, 25, 30, 50)) {
                    for (interval in arrayOf(2, 3, 5, 7, 10, 12, 15, 20, 30)) {
                        fragmentsCache.add(FragmentsCache(FragmentBean(
                            "tlshello",
                            "${length}-${length * 2}",
                            "${interval}-${interval * 2}",
                        )))
                    }
                }
                viewModelScope.launch(Dispatchers.Default) { // without Dispatchers.Default viewModelScope will launch in main thread
                    for ((index, item) in fragmentsCache.withIndex()) {
                        val v2rayConfig = Gson().fromJson(configJson, V2rayConfig::class.java)
                        if (index > 0) {
                            V2rayConfigUtil.updateOutboundFragment(v2rayConfig, item.fragmentBean)
                        }
                        MessageUtil.sendMsg2TestService(
                            getApplication(),
                            AppConfig.MSG_MEASURE_FRAGMENT,
                            Pair(index, v2rayConfig.toPrettyPrinting())
                        )
                    }
                }
                settingsStorage.putBoolean(AppConfig.PREF_FRAGMENT_ENABLED, prefFragmentEnabled)
                return
            }
        }
        getApplication<AngApplication>().toast(R.string.error_select_a_tls_config)
    }

    fun checkAndSetBestFragmentSettings() {
        if (fragmentsCache.any { it.ping == -2L }) {
            return
        }

        var maxPing = 0L
        var bestFragmentBean: FragmentBean? = null

        for (item in fragmentsCache) {
            if (item.ping < 0) {
                continue
            }

            if (maxPing == 0L || item.ping < maxPing) {
                maxPing = item.ping
                bestFragmentBean = item.fragmentBean
            }
        }

        if (maxPing > 0L) {
            if (bestFragmentBean?.packets.isNullOrEmpty()) {
                settingsStorage.putBoolean(AppConfig.PREF_FRAGMENT_ENABLED, false)
            } else {
                settingsStorage.putString(AppConfig.PREF_FRAGMENT_PACKETS, bestFragmentBean?.packets)
                settingsStorage.putString(AppConfig.PREF_FRAGMENT_LENGTH, bestFragmentBean?.length)
                settingsStorage.putString(AppConfig.PREF_FRAGMENT_INTERVAL, bestFragmentBean?.interval)
                settingsStorage.putBoolean(AppConfig.PREF_FRAGMENT_ENABLED, true)
            }
            
            getApplication<AngApplication>().toast(R.string.fragment_test_done)
        } else {
            getApplication<AngApplication>().toast(R.string.fragment_test_failed)
        }
    }

    fun testCurrentServerRealPing() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
    }

    fun filterConfig(context: Context) {
        val subscriptions = MmkvManager.decodeSubscriptions()
        val listId = subscriptions.map { it.first }.toList().toMutableList()
        val listRemarks = subscriptions.map { it.second.remarks }.toList().toMutableList()
        listRemarks += context.getString(R.string.filter_config_all)
        val checkedItem = if (subscriptionId.isNotEmpty()) {
            listId.indexOf(subscriptionId)
        } else {
            listRemarks.count() - 1
        }

        val ivBinding = DialogConfigFilterBinding.inflate(LayoutInflater.from(context))
        ivBinding.spSubscriptionId.adapter = ArrayAdapter<String>(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            listRemarks
        )
        ivBinding.spSubscriptionId.setSelection(checkedItem)
        ivBinding.etKeyword.text = Utils.getEditable(keywordFilter)
        val builder = AlertDialog.Builder(context).setView(ivBinding.root)
        builder.setTitle(R.string.title_filter_config)
        builder.setPositiveButton(R.string.tasker_setting_confirm) { dialogInterface: DialogInterface?, _: Int ->
            try {
                val position = ivBinding.spSubscriptionId.selectedItemPosition
                subscriptionId = if (listRemarks.count() - 1 == position) {
                    ""
                } else {
                    subscriptions[position].first
                }
                keywordFilter = ivBinding.etKeyword.text.toString()
                settingsStorage?.encode(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
                settingsStorage?.encode(AppConfig.CACHE_KEYWORD_FILTER, keywordFilter)
                reloadServerList()

                dialogInterface?.dismiss()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        builder.show()
//        AlertDialog.Builder(context)
//            .setSingleChoiceItems(listRemarks.toTypedArray(), checkedItem) { dialog, i ->
//                try {
//                    subscriptionId = if (listRemarks.count() - 1 == i) {
//                        ""
//                    } else {
//                        subscriptions[i].first
//                    }
//                    reloadServerList()
//                    dialog.dismiss()
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }.show()
    }

    fun getPosition(guid: String): Int {
        serversCache.forEachIndexed { index, it ->
            if (it.guid == guid)
                return index
        }
        return -1
    }

    fun removeDuplicateServer() {
        val deleteServer = mutableListOf<String>()
        serversCache.forEachIndexed { index, it ->
            val outbound = it.config.getProxyOutbound()
            serversCache.forEachIndexed { index2, it2 ->
                if (index2 > index) {
                    val outbound2 = it2.config.getProxyOutbound()
                    if (outbound == outbound2 && !deleteServer.contains(it2.guid)) {
                        deleteServer.add(it2.guid)
                    }
                }
            }
        }
        for (it in deleteServer) {
            MmkvManager.removeServer(it)
        }
        reloadServerList()
        getApplication<AngApplication>().toast(
            getApplication<AngApplication>().getString(
                R.string.title_del_duplicate_config_count,
                deleteServer.count()
            )
        )
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    getApplication<AngApplication>().toast(R.string.toast_services_success)
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    getApplication<AngApplication>().toast(R.string.toast_services_failure)
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    isRunning.value = false
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    updateTestResultAction.value = intent.getStringExtra("content")
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val resultPair = intent.getSerializableExtra("content") as Pair<String, Long>
                    MmkvManager.encodeServerTestDelayMillis(resultPair.first, resultPair.second)
                    updateListAction.value = getPosition(resultPair.first)
                }

                AppConfig.MSG_MEASURE_FRAGMENT_SUCCESS -> {
                    val resultPair = intent.getSerializableExtra("content") as Pair<Int, Long>
                    fragmentsCache[resultPair.first].ping = resultPair.second
                    checkAndSetBestFragmentSettings()
                }
            }
        }
    }
}
