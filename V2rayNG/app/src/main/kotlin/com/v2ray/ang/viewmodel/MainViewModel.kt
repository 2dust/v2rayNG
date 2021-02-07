package com.v2ray.ang.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.*
import com.v2ray.ang.util.MmkvManager.KEY_ANG_CONFIGS
import kotlinx.coroutines.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }

    val serverList by lazy { Gson().fromJson(mainStorage?.decodeString(KEY_ANG_CONFIGS), Array<String>::class.java).toList() }
    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }

    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    fun startListenBroadcast() {
        isRunning.value = false
        getApplication<AngApplication>().registerReceiver(mMsgReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY))
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        Utils.closeAllTcpSockets()
        Log.i(AppConfig.ANG_PACKAGE, "Main ViewModel is cleared")
        super.onCleared()
    }

    fun getGuid(index: Int): String? {
        return serverList.getOrNull(index)
    }

    fun testAllTcping() {
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        Utils.closeAllTcpSockets()
        MmkvManager.clearAllTestDelayResults()
        updateListAction.value = -1 // update all

        getApplication<AngApplication>().toast(R.string.connection_test_testing)
        for (guid in serverList) {
            MmkvManager.decodeServerConfig(guid)?.getProxyOutbound()?.let { outbound ->
                val serverAddress = outbound.getServerAddress()
                val serverPort = outbound.getServerPort()
                if (serverAddress != null && serverPort != null) {
                    tcpingTestScope.launch {
                        val testResult = Utils.tcping(serverAddress, serverPort)
                        launch(Dispatchers.Main) {
                            MmkvManager.decodeServerAffiliationInfo(guid)?.let { aff ->
                                aff.testDelayMillis = testResult
                                serverAffStorage?.encode(guid, Gson().toJson(aff))
                                updateListAction.value = serverList.indexOf(guid)
                            }
                        }
                    }
                }
            }
        }
    }

    fun testCurrentServerRealPing() {
        val socksPort = 10808//Utils.parseInt(defaultDPreference.getPrefString(SettingsActivity.PREF_SOCKS_PORT, "10808"))
        GlobalScope.launch(Dispatchers.IO) {
            val result = Utils.testConnection(getApplication(), socksPort)
            launch(Dispatchers.Main) {
                updateTestResultAction.value = result
            }
        }
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
            }
        }
    }
}
